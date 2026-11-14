package app.custo.feature.overlays

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-process signals between the notification listener, the app UI and the overlay service. */
object OverlayBus {
    /** A color to flash around the screen edges (null = use the configured/accent color). */
    val edge = MutableSharedFlow<Int?>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** True while the accessibility service is connected. */
    val serviceRunning = MutableStateFlow(false)
}

enum class EdgeColorMode(val label: String) { APP("App color"), ACCENT("Accent"), RAINBOW("Rainbow") }
enum class BatteryBarStyle(val label: String) { TOP("Top edge"), CUTOUT("Around camera") }
enum class PanelSide(val label: String) { RIGHT("Right"), LEFT("Left") }
