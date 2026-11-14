package app.custo.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.custo.BuildConfig
import app.custo.IPrivilegedService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

/** Tracks Shizuku availability and owns the privileged user service connection. */
class ShizukuManager(private val context: Context) : Shell {

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile private var service: IPrivilegedService? = null
    @Volatile private var pending: CompletableDeferred<IPrivilegedService>? = null

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, PrivilegedService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("priv")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            val s = IPrivilegedService.Stub.asInterface(binder)
            service = s
            pending?.complete(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            pending = null
        }
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            pending = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _state.value = when {
            !isManagerInstalled() && !Shizuku.pingBinder() -> ShizukuState.NOT_INSTALLED
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun isManagerInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private suspend fun obtainService(): IPrivilegedService {
        service?.let { return it }
        if (_state.value != ShizukuState.READY) {
            refresh()
            if (_state.value != ShizukuState.READY) throw ShellUnavailableException()
        }
        val deferred = synchronized(this) {
            pending ?: CompletableDeferred<IPrivilegedService>().also {
                pending = it
                withMain { Shizuku.bindUserService(serviceArgs, connection) }
            }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    private inline fun withMain(crossinline block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { block() }
    }

    override suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        val s = obtainService()
        ShellResult.parse(s.exec(command))
    }

    override suspend fun fabricate(target: String, name: String, resource: String, type: Int, value: Int) = withContext(Dispatchers.IO) {
        obtainService().fabricate(target, name, resource, type, value)?.let { error(it) } ?: Unit
    }

    override suspend fun unfabricate(name: String) = withContext(Dispatchers.IO) {
        obtainService().unfabricate(name)?.let { error(it) } ?: Unit
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 4242
    }
}
