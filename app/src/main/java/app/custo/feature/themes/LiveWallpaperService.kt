package app.custo.feature.themes

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import androidx.datastore.preferences.core.Preferences
import app.custo.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.min

/** Live wallpaper settings, read from [ThemeKeys]. */
data class LiveOptions(
    val animate: Boolean = true,
    val fps: Int = 15,
    val speed: Float = 1f,
    val timeOfDay: Boolean = false,
    val battery: Boolean = false,
    val parallax: Boolean = true,
    val unlockedOnly: Boolean = false,
) {
    companion object {
        fun from(p: Preferences) = LiveOptions(
            animate = p[ThemeKeys.liveAnimate] ?: true,
            fps = (p[ThemeKeys.liveFps] ?: 15).coerceIn(5, 20),
            speed = p[ThemeKeys.liveSpeed] ?: 1f,
            timeOfDay = p[ThemeKeys.liveTimeOfDay] ?: false,
            battery = p[ThemeKeys.liveBattery] ?: false,
            parallax = p[ThemeKeys.liveParallax] ?: true,
            unlockedOnly = p[ThemeKeys.liveUnlockedOnly] ?: false,
        )
    }
}

/**
 * Animates the saved procedural wallpaper slowly. Battery rules: draws at most [LiveOptions.fps]
 * (≤ 20) frames per second, only while visible, never in battery saver, and optionally only
 * while unlocked. Static styles and paused states draw a single frame.
 */
class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LiveEngine()

    private inner class LiveEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var spec = WallSpec()
        private var opts = LiveOptions()
        private var visible = false
        private var locked = false
        private var battery = 100
        private var xOffset = 0.5f
        private var animTime = 0f
        private var lastFrame = 0L
        private var paletteMinute = -1
        private var drawSpec = spec
        /** The scene recorded once per animation step; page swipes only re-position it. */
        private var node: RenderNode? = null
        private var sceneDirty = true
        private var sceneW = 0f
        private var sceneH = 0f
        private var sceneSpec: WallSpec? = null
        private val frame = Runnable { drawFrame() }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        if (lvl != battery) { battery = lvl; paletteMinute = -1; if (opts.battery) drawFrame() }
                    }
                    Intent.ACTION_SCREEN_OFF -> locked = true
                    Intent.ACTION_USER_PRESENT -> { locked = false; drawFrame() }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> drawFrame()
                }
            }
        }
        private var registered = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            scope.launch {
                applicationContext.container.prefs.data.collect { p ->
                    spec = WallSpec.decode(p[ThemeKeys.liveSpec]) ?: WallSpec(WallStyle.AURORA, BUILT_IN_LOOKS.first().wall.colors)
                    opts = LiveOptions.from(p)
                    paletteMinute = -1
                    sceneDirty = true
                    notifyColorsChanged()
                    drawFrame()
                }
            }
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            unregister()
            scope.cancel()
            node?.discardDisplayList()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                register()
                locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                lastFrame = 0L
                drawFrame()
            } else {
                handler.removeCallbacks(frame)
                unregister()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            sceneDirty = true
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            if (this.xOffset != xOffset) {
                this.xOffset = xOffset
                // Re-uses the recorded scene: just a GPU blit, not a full redraw.
                if (opts.parallax) present()
            }
        }

        override fun onComputeColors(): WallpaperColors {
            val c = spec.colors.ifEmpty { listOf(Color.BLACK) }
            return WallpaperColors(Color.valueOf(c.getOrElse(1) { c[0] }), Color.valueOf(c[0]), c.getOrNull(2)?.let { Color.valueOf(it) })
        }

        private fun register() {
            if (registered) return
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(this@LiveWallpaperService, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }

        private fun unregister() {
            if (!registered) return
            runCatching { unregisterReceiver(receiver) }
            registered = false
        }

        private fun animating(): Boolean =
            visible && opts.animate && spec.style.animated &&
                !(opts.unlockedOnly && locked) &&
                !getSystemService(PowerManager::class.java).isPowerSaveMode

        /** Applies time-of-day and battery reactions to the palette, at most once a minute. */
        private fun currentSpec(): WallSpec {
            val cal = Calendar.getInstance()
            val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (minute != paletteMinute || drawSpec.style != spec.style) {
                paletteMinute = minute
                drawSpec = spec.copy(colors = spec.colors.map { col ->
                    var v = col
                    if (opts.timeOfDay) v = WallLogic.timeOfDay(v, minute)
                    if (opts.battery) v = WallLogic.battery(v, battery)
                    v
                })
            }
            return drawSpec
        }

        private fun drawFrame() {
            handler.removeCallbacks(frame)
            val holder = surfaceHolder ?: return
            if (holder.surface?.isValid != true) return
            val anim = animating()
            val now = SystemClock.elapsedRealtime()
            if (anim) {
                val slow = if (opts.battery && battery <= 20) 0.5f else 1f
                if (lastFrame != 0L) animTime += min(now - lastFrame, 200L) / 1000f * opts.speed * slow
                lastFrame = now
                sceneDirty = true
            } else lastFrame = 0L
            present()
            if (anim) handler.postDelayed(frame, 1000L / opts.fps.coerceIn(5, 20))
        }

        /** Puts the (cached) scene on screen at the current parallax offset. */
        private fun present() {
            val holder = surfaceHolder ?: return
            if (holder.surface?.isValid != true) return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                render(canvas, holder.surfaceFrame.width().toFloat(), holder.surfaceFrame.height().toFloat())
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun render(canvas: Canvas, w: Float, h: Float) {
            val s = currentSpec()
            val extra = if (opts.parallax && !isPreview) w * 0.18f else 0f
            val sw = w + extra
            val n = node ?: RenderNode("custo-wall").also { node = it }
            if (sceneDirty || s !== sceneSpec || sw != sceneW || h != sceneH || !n.hasDisplayList()) {
                n.setPosition(0, 0, sw.toInt(), h.toInt())
                val r = s.blur * min(w, h) * 0.06f
                n.setRenderEffect(if (s.blur > 0.01f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null)
                val rc = n.beginRecording()
                WallpaperStudio.drawScene(rc, s, sw, h, animTime)
                n.endRecording()
                sceneDirty = false; sceneSpec = s; sceneW = sw; sceneH = h
            }
            canvas.withTranslation(-extra * xOffset, 0f) { drawRenderNode(n) }
            WallpaperStudio.drawGrain(canvas, s.grain, w, h)
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, LiveWallpaperService::class.java)

        fun isActive(context: Context): Boolean =
            WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)

        /** Opens the system preview to set Custo as the live wallpaper. */
        fun pickerIntent(context: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
