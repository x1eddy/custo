package app.custo.feature.overlays

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** A new notification worth lighting the edges for. */
data class EdgeEvent(val pkg: String?, val color: Int)

/** A notification preview for the dynamic island. */
data class IslandNotif(val pkg: String, val title: String, val text: String, val icon: Icon?, val intent: PendingIntent?)

/** Now playing info for the dynamic island. */
data class IslandMedia(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean)

/** A running chronometer from a notification (timer or stopwatch). */
data class IslandTimer(val pkg: String, val whenMs: Long, val countDown: Boolean)

enum class Preview { EDGE, NOTIF_LIGHT, ISLAND, CHARGING, VOLUME, SIDEBAR }

/** In-process signals between the notification listener, the app UI and the overlay service. */
object OverlayBus {
    /** A notification to flash around the screen edges. */
    val edge = MutableSharedFlow<EdgeEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Live preview requests from the settings screens. */
    val preview = MutableSharedFlow<Preview>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Unread (still active) notifications: package to light color. */
    val unread = MutableStateFlow<Map<String, Int>>(emptyMap())

    val islandNotif = MutableSharedFlow<IslandNotif>(extraBufferCapacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val media = MutableStateFlow<IslandMedia?>(null)
    val timer = MutableStateFlow<IslandTimer?>(null)

    /** True while the accessibility service is connected. */
    val serviceRunning = MutableStateFlow(false)

    /** True while the notification listener is connected. */
    val listenerRunning = MutableStateFlow(false)
}

enum class EdgeColorMode(val label: String) { APP("App color"), ACCENT("Accent"), RAINBOW("Rainbow"), CUSTOM("Custom") }
enum class EdgeStyle(val label: String) { SWEEP("Sweep"), PULSE("Pulse"), BREATHING("Breathing"), COMET("Comet") }
enum class BatteryBarStyle(val label: String) { TOP("Edge"), CUTOUT("Around camera") }
enum class BarPosition(val label: String) { TOP("Top"), BOTTOM("Bottom") }
enum class BarColorMode(val label: String) { LEVEL("By level"), GRADIENT("Gradient"), ACCENT("Accent"), CUSTOM("Custom") }
enum class PanelSide(val label: String) { RIGHT("Right"), LEFT("Left") }
enum class ChargeStyle(val label: String) { RING("Ring"), EDGES("Edges"), PULSE("Pulse") }
enum class NetPosition(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }
enum class VolumeSkin(val label: String) { PILL("Pill"), SLIM("Slim"), BIG("Big"), DOTS("Dots") }

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    if (name == null) default else runCatching { enumValueOf<E>(name) }.getOrDefault(default)
