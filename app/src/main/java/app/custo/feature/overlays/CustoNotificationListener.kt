package app.custo.feature.overlays

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.custo.container
import app.custo.data.Keys
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Triggers edge lighting for new notifications and keeps the Now playing widget in sync.
 * Notification content is never stored or sent anywhere.
 */
class CustoNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var msm: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()
    private var pendingRefresh: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> track(list ?: emptyList()) }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshMedia()
    }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        val me = ComponentName(this, CustoNotificationListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, me)
            track(m.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { it.unregisterCallback(mediaCallback) }
        controllers.clear()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun track(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(mediaCallback) }
        controllers.clear()
        list.forEach { it.registerCallback(mediaCallback); controllers += it }
        refreshMedia()
    }

    /** Debounced so a burst of metadata updates causes one widget redraw. */
    private fun refreshMedia() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(300)
            Widgets.refresh(this@CustoNotificationListener, WidgetKind.MEDIA)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS) return
        scope.launch {
            if (container.prefs.get(Keys.edgeEnabled, false)) OverlayBus.edge.tryEmit(n.color)
        }
    }
}
