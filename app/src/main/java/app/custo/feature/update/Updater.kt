package app.custo.feature.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.custo.BuildConfig
import app.custo.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Keeps Custo up to date from its GitHub releases: checks the latest release (on start at most every
 * few hours, and daily in the background), downloads the APK into the cache, makes sure it really is
 * a newer Custo, and hands it to Android's package installer. Android itself refuses an APK that isn't
 * signed with Custo's key, so a tampered file can't be installed.
 */
class Updater(private val app: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private var job: Job? = null

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val autoCheck = prefs.flow(AUTO, true)
    /** Version the user said "Later" to on Home (Settings still shows it). */
    val skipped = prefs.flow(SKIPPED, "")

    /** Called on app start: checks quietly if automatic checks are on and the last one is old. */
    fun checkIfDue() {
        scope.launch {
            if (!prefs.get(AUTO, true)) return@launch
            val last = prefs.get(LAST_CHECK, 0L)
            if (System.currentTimeMillis() - last < CHECK_EVERY_MS) {
                // Show a release found earlier without asking GitHub again.
                fetchCached()?.let { if (_state.value is UpdateState.Idle) _state.value = UpdateState.Available(it) }
                return@launch
            }
            check(manual = false)
        }
    }

    /** Asks GitHub for the latest release. [manual] shows errors; automatic checks stay silent. */
    fun check(manual: Boolean = true) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            val result = runCatching { fetchLatest() }
            result.onSuccess { info ->
                _state.value = if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
            }.onFailure {
                _state.value = if (manual) UpdateState.Failed(if (isOnline()) "Couldn't reach GitHub" else "You're offline") else UpdateState.Idle
            }
        }
    }

    /** The newer release, or null when Custo is up to date. Throws on network errors. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(UpdateParsing.LATEST_URL).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            val code = conn.responseCode
            if (code == 404) return@withContext null // no releases yet
            if (code != 200) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            prefs.edit {
                it[LAST_CHECK] = System.currentTimeMillis()
                it[CACHED] = body
            }
            UpdateParsing.newerRelease(body, currentVersion)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun fetchCached(): UpdateInfo? =
        prefs.get(CACHED, "").takeIf { it.isNotEmpty() }?.let { UpdateParsing.newerRelease(it, currentVersion) }

    fun skip(info: UpdateInfo) {
        scope.launch { prefs.set(SKIPPED, info.version) }
    }

    fun setAutoCheck(on: Boolean) {
        scope.launch { prefs.set(AUTO, on) }
        schedule(on)
    }

    /** Daily background check that posts a notification when a new version is out. */
    fun schedule(on: Boolean) {
        val wm = WorkManager.getInstance(app)
        if (!on) { wm.cancelUniqueWork(UpdateWorker.NAME); return }
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(UpdateWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Whether Android lets Custo install apps; if not, [openInstallPermission] first. */
    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(i) }
    }

    /** Downloads [info]'s APK with progress and starts the install. */
    fun downloadAndInstall(info: UpdateInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            try {
                val apk = download(info)
                verify(apk, info)
                _state.value = UpdateState.Installing(info)
                install(apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = UpdateState.Available(info)
                throw e
            } catch (e: Throwable) {
                _state.value = UpdateState.Failed(
                    when {
                        e is UpdateException -> e.message ?: "Update failed"
                        !isOnline() -> "You're offline"
                        else -> "Download failed. Try again"
                    },
                    info,
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever keep the one being installed
        val dest = File(dir, "custo-${info.version.filter { it.isLetterOrDigit() || it == '.' }}.apk")
        val conn = open(info.apkUrl) // github.com redirects to its download CDN over https
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val host = conn.url.host
            if (host != "github.com" && !host.endsWith(".githubusercontent.com")) throw UpdateException("Unexpected download location")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkBytes
            if (total > MAX_APK_BYTES) throw UpdateException("The update is too large")
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done > MAX_APK_BYTES) throw UpdateException("The update is too large")
                        val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (p - lastShown >= 0.01f) { lastShown = p; _state.value = UpdateState.Downloading(info, p) }
                    }
                }
            }
            dest
        } catch (e: Throwable) {
            dest.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Checks the file is a Custo APK newer than this one (Android checks the signature itself). */
    @Suppress("DEPRECATION")
    private fun verify(apk: File, info: UpdateInfo) {
        val pi = app.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: run { apk.delete(); throw UpdateException("The download is damaged. Try again") }
        if (pi.packageName != app.packageName) { apk.delete(); throw UpdateException("That file isn't Custo") }
        if (pi.longVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
            apk.delete(); throw UpdateException("Custo ${info.version} is not newer than this one")
        }
    }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { s ->
                apk.inputStream().use { input ->
                    s.openWrite("custo.apk", 0, apk.length()).use { out ->
                        input.copyTo(out, 64 * 1024)
                        s.fsync(out)
                    }
                }
                val intent = Intent(app, InstallResultReceiver::class.java).setPackage(app.packageName)
                val pending = PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                s.commit(pending.intentSender)
            }
        } catch (e: Throwable) {
            runCatching { installer.abandonSession(id) }
            throw UpdateException("Android couldn't start the install")
        }
    }

    /** Result from Android's installer (see [InstallResultReceiver]). */
    internal fun onInstallResult(status: Int, message: String?) {
        val info = (state.value as? UpdateState.Installing)?.info
        if (status == PackageInstaller.STATUS_SUCCESS) return // Custo is being replaced
        _state.value = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("This Custo was signed with a different key. Install the update from GitHub", info)
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Not enough storage for the update", info)
            else -> UpdateState.Failed(message?.take(120) ?: "The install didn't finish", info)
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Custo-Updater/${BuildConfig.VERSION_NAME}")
    }

    private fun isOnline(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    class UpdateException(message: String) : Exception(message)

    companion object {
        val AUTO = booleanPreferencesKey("update_auto")
        val LAST_CHECK = longPreferencesKey("update_last_check")
        val CACHED = stringPreferencesKey("update_cached_release")
        val SKIPPED = stringPreferencesKey("update_skipped")
        val NOTIFIED = stringPreferencesKey("update_notified")
        private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
        private const val MAX_APK_BYTES = 100L * 1024 * 1024
    }
}
