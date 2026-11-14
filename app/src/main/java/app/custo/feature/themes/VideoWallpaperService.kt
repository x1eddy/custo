package app.custo.feature.themes

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.custo.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Video wallpaper settings. */
object VideoKeys {
    val uri = stringPreferencesKey("video_wall_uri")
    val sound = booleanPreferencesKey("video_wall_sound")
    val crop = booleanPreferencesKey("video_wall_crop")
}

data class VideoOptions(val uri: String?, val sound: Boolean, val crop: Boolean)

/**
 * Plays a video the user picked as the wallpaper: looped, muted unless asked otherwise, and only
 * while the wallpaper is visible and battery saver is off. MediaPlayer decodes into a texture that
 * is drawn with GL, so the video can be cropped to fill or fitted with bars.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine(), SurfaceTexture.OnFrameAvailableListener {
        private val thread = HandlerThread("custo-video").also { it.start() }
        private val gl = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val power by lazy { getSystemService(PowerManager::class.java) }

        // Everything below is touched on the GL thread only.
        private var renderer: VideoGl? = null
        private var texture: SurfaceTexture? = null
        private var videoSurface: Surface? = null
        private var player: MediaPlayer? = null
        private var opts: VideoOptions? = null
        private var loadedUri: String? = null
        private var prepared = false
        private var visible = false
        private var videoW = 0
        private var videoH = 0
        private var surfW = 0
        private var surfH = 0
        private val st = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { gl.post { updatePlayback() } }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            ContextCompat.registerReceiver(this@VideoWallpaperService, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            scope.launch {
                applicationContext.container.prefs.data
                    .map { VideoOptions(it[VideoKeys.uri], it[VideoKeys.sound] == true, it[VideoKeys.crop] != false) }
                    .distinctUntilChanged()
                    .collect { o -> gl.post { onOptions(o) } }
            }
        }

        override fun onDestroy() {
            scope.cancel()
            runCatching { unregisterReceiver(receiver) }
            blockOnGl { releasePlayer(); releaseGl() }
            thread.quitSafely()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            gl.post {
                releaseGl()
                val r = VideoGl()
                if (!r.init(holder.surface)) return@post
                renderer = r
                texture = SurfaceTexture(r.texture).also { it.setOnFrameAvailableListener(this, gl) }
                videoSurface = Surface(texture)
                loadedUri = null
                load()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            gl.post { surfW = width; surfH = height; draw() }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            blockOnGl { draw() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            // The surface goes away when this returns, so tear down GL first.
            blockOnGl { releasePlayer(); releaseGl() }
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            gl.post { this.visible = visible; updatePlayback() }
        }

        override fun onFrameAvailable(st: SurfaceTexture) {
            val t = texture ?: return
            if (t !== st) return
            runCatching { t.updateTexImage(); t.getTransformMatrix(this.st) }
            draw()
        }

        private fun blockOnGl(block: () -> Unit) {
            val latch = CountDownLatch(1)
            if (!gl.post { try { block() } finally { latch.countDown() } }) return
            latch.await(2, TimeUnit.SECONDS)
        }

        private fun onOptions(o: VideoOptions) {
            val old = opts
            opts = o
            if (old?.uri != o.uri) load()
            else {
                player?.let { val v = if (o.sound) 1f else 0f; runCatching { it.setVolume(v, v) } }
                draw()
            }
        }

        private fun load() {
            val o = opts ?: return
            val surface = videoSurface ?: return
            if (o.uri == loadedUri && player != null) return
            releasePlayer()
            loadedUri = o.uri
            videoW = 0; videoH = 0
            val uri = o.uri?.let(Uri::parse) ?: run { draw(); return }
            readSize(uri)
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                mp.setDataSource(applicationContext, uri)
                mp.setSurface(surface)
                mp.isLooping = true
                val v = if (o.sound) 1f else 0f
                mp.setVolume(v, v)
                // Seeking shows the first frame even while paused (hidden or battery saver).
                mp.setOnPreparedListener { prepared = true; runCatching { it.seekTo(0) }; updatePlayback() }
                mp.setOnErrorListener { _, _, _ -> prepared = false; true }
                mp.prepareAsync()
                player = mp
            } catch (_: Exception) {
                // Permission lost or the file is gone: show black rather than crash the wallpaper.
                mp.release()
                draw()
            }
        }

        /** Display size of the video, with rotation applied. */
        private fun readSize(uri: Uri) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(applicationContext, uri)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { videoW = h; videoH = w } else { videoW = w; videoH = h }
            } catch (_: Exception) {
            } finally {
                runCatching { r.release() }
            }
        }

        private fun updatePlayback() {
            val mp = player ?: return
            if (!prepared) return
            val play = visible && !power.isPowerSaveMode
            runCatching {
                if (play && !mp.isPlaying) mp.start()
                else if (!play && mp.isPlaying) mp.pause()
            }
        }

        private fun draw() {
            val r = renderer ?: return
            if (surfW == 0 || surfH == 0) return
            val dest = if (videoW > 0) CollageLogic.videoRect(videoW, videoH, surfW, surfH, opts?.crop != false) else null
            r.draw(st, dest, surfW, surfH)
        }

        private fun releasePlayer() {
            player?.let { runCatching { it.reset() }; it.release() }
            player = null
            prepared = false
        }

        private fun releaseGl() {
            videoSurface?.release(); videoSurface = null
            texture?.release(); texture = null
            renderer?.release(); renderer = null
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, VideoWallpaperService::class.java)

        fun isActive(context: Context): Boolean =
            WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)

        fun pickerIntent(context: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Minimal GLES2 renderer: one external (video) texture on a quad. */
private class VideoGl {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    var texture = 0
        private set

    private val quad: FloatBuffer = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val uv: FloatBuffer = floats(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

    fun init(target: Surface): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return false
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, n, 0) || n[0] == 0) return false
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false
        surface = runCatching { EGL14.eglCreateWindowSurface(display, configs[0], target, intArrayOf(EGL14.EGL_NONE), 0) }.getOrNull() ?: EGL14.EGL_NO_SURFACE
        if (surface == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false

        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        texture = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        program = link(VS, FS)
        return program != 0
    }

    /** Clears to black, then draws the latest video frame into [dest] (x, y, w, h), if any. */
    fun draw(st: FloatArray, dest: IntArray?, w: Int, h: Int) {
        if (surface == EGL14.EGL_NO_SURFACE) return
        EGL14.eglMakeCurrent(display, surface, surface, context)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (dest != null) {
            // glViewport bottom-left origin; the rect is centered so y flips cleanly.
            GLES20.glViewport(dest[0], h - dest[1] - dest[3], dest[2], dest[3])
            GLES20.glUseProgram(program)
            val aPos = GLES20.glGetAttribLocation(program, "aPos")
            val aTex = GLES20.glGetAttribLocation(program, "aTex")
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, uv)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uSt"), 1, false, st, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        EGL14.eglSwapBuffers(display, surface)
    }

    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
        display = EGL14.EGL_NO_DISPLAY; context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { GLES20.glDeleteShader(s); return 0 }
        return s
    }

    private fun link(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs); val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        if (v == 0 || f == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        return if (ok[0] == 0) { GLES20.glDeleteProgram(p); 0 } else p
    }

    companion object {
        private const val VS = "attribute vec4 aPos; attribute vec4 aTex; uniform mat4 uSt; varying vec2 vTex;\n" +
            "void main() { gl_Position = aPos; vTex = (uSt * aTex).xy; }"
        private const val FS = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\n" +
            "varying vec2 vTex; uniform samplerExternalOES sTex;\nvoid main() { gl_FragColor = texture2D(sTex, vTex); }"

        private fun floats(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }
    }
}
