package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.TextPaint
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.scale
import androidx.core.graphics.withRotation
import androidx.datastore.preferences.core.Preferences
import app.custo.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Sticker images: bundled art rendered on demand, imported photos kept as small PNGs in app storage. */
object StickerStore {
    private const val MAX_IMPORT_PX = 640
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "stickers").apply { mkdirs() }

    /** Copies a picked image into app storage. Returns a new sticker, or null if it can't be read. */
    suspend fun import(ctx: Context, uri: Uri): Sticker? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val k = MAX_IMPORT_PX / max(s.width, s.height).toFloat()
                if (k < 1f) d.setTargetSize((s.width * k).roundToInt().coerceAtLeast(1), (s.height * k).roundToInt().coerceAtLeast(1))
            }
            val name = UUID.randomUUID().toString() + ".png"
            File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val aspect = bmp.height / bmp.width.toFloat()
            bmp.recycle()
            Sticker(UUID.randomUUID().toString(), "f:$name", aspect = aspect.coerceIn(0.2f, 5f))
        }.getOrNull()
    }

    fun newBundled(name: String) = Sticker(UUID.randomUUID().toString(), "b:$name")

    /** Deletes imported files no sticker uses any more. */
    fun prune(ctx: Context, keep: List<Sticker>) {
        val used = keep.mapNotNull { it.src.removePrefix("f:").takeIf { _ -> it.src.startsWith("f:") } }.toSet()
        dir(ctx).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    /** The sticker's image about [widthPx] wide. Cached. Null if an imported file is gone. */
    fun bitmap(ctx: Context, src: String, widthPx: Int): Bitmap? {
        val w = widthPx.coerceIn(16, 1024)
        // Round the size so small zoom changes reuse the cached bitmap.
        val bucket = ((w + 31) / 32) * 32
        val key = "$src@$bucket"
        cache.get(key)?.let { return it }
        val bmp = when {
            src.startsWith("b:") -> StickerArt.render(src.removePrefix("b:"), bucket)
            src.startsWith("f:") -> {
                val f = File(dir(ctx), src.removePrefix("f:").substringAfterLast('/'))
                if (!f.exists()) return null
                val raw = BitmapFactory.decodeFile(f.path) ?: return null
                if (raw.width > bucket) raw.scale(bucket, (raw.height * bucket / raw.width.toFloat()).roundToInt().coerceAtLeast(1)).also { if (it !== raw) raw.recycle() } else raw
            }
            else -> return null
        }
        cache.put(key, bmp)
        return bmp
    }

    fun homePackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val def = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        // "android" means no default is chosen yet (the resolver); then any launcher counts.
        return if (def != null && def != "android") setOf(def)
        else pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }
}

/**
 * Stickers floating over the home screen only. Each sticker is its own small window that ignores
 * touches, so taps reach the launcher. Windows exist only while the launcher is in front.
 * In arrange mode one full-screen window takes touches for dragging, pinching and twisting.
 */
class StickerOverlay(host: OverlayHost) : Overlay(host) {

    private var enabled = false
    private var arrange = false
    private var list: List<Sticker> = emptyList()
    private var onHome = false
    private var homePkgs: Set<String> = emptySet()
    private val windows = mutableListOf<View>()
    private var shownKey: Any? = null
    private var arrangeView: StickerArrangeView? = null

    override fun apply(p: Preferences) {
        enabled = p[OverlayKeys.stickers] == true
        if (!enabled) { remove(); return }
        arrange = p[OverlayKeys.stickerArrange] == true
        list = StickerMath.decode(p[OverlayKeys.stickerList])
        homePkgs = StickerStore.homePackages(ctx)
        sync()
    }

    override fun onForegroundApp(pkg: String) {
        if (!enabled) return
        val home = pkg in homePkgs || (homePkgs.isEmpty() && pkg in StickerStore.homePackages(ctx).also { homePkgs = it })
        if (home == onHome) return
        onHome = home
        sync()
    }

    private fun sync() {
        if (!enabled || !onHome) { clearWindows(); dropArrange(); return }
        if (arrange) {
            clearWindows()
            if (arrangeView == null) showArrange()
            return
        }
        dropArrange()
        val b = screen()
        val key = listOf(list, b.width(), b.height())
        if (key == shownKey && windows.isNotEmpty()) return
        clearWindows()
        shownKey = key
        val w = b.width().toFloat(); val h = b.height().toFloat()
        list.forEach { s ->
            val bmp = StickerStore.bitmap(ctx, s.src, (s.size * w).roundToInt()) ?: return@forEach
            val box = StickerMath.bounds(s, w, h)
            val v = StickerView(ctx, bmp, s, w)
            val lp = params(ceil(box.w).toInt().coerceAtLeast(1), ceil(box.h).toInt().coerceAtLeast(1)).apply {
                gravity = Gravity.TOP or Gravity.START
                x = box.l.roundToInt(); y = box.t.roundToInt()
            }
            if (add(v, lp)) windows += v
        }
    }

    private fun showArrange() {
        val b = screen()
        val v = StickerArrangeView(ctx, list, b.width().toFloat(), b.height().toFloat(), host.accent()) { done -> save(done) }
        if (add(v, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = true))) arrangeView = v
    }

    private fun save(stickers: List<Sticker>) {
        val c = ctx.container
        list = stickers
        arrange = false
        c.appScope.launch {
            c.prefs.edit {
                it[OverlayKeys.stickerList] = StickerMath.encode(stickers)
                it[OverlayKeys.stickerArrange] = false
            }
        }
        sync()
    }

    private fun clearWindows() {
        windows.forEach { drop(it) }
        windows.clear()
        shownKey = null
    }

    private fun dropArrange() { drop(arrangeView); arrangeView = null }

    override fun remove() {
        clearWindows()
        dropArrange()
    }
}

/** One sticker, drawn rotated in the middle of its own window. */
@SuppressLint("ViewConstructor")
class StickerView(context: Context, private val bmp: Bitmap, private val s: Sticker, private val screenW: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        val hw = StickerMath.halfW(s, screenW); val hh = StickerMath.halfH(s, screenW)
        val cx = width / 2f; val cy = height / 2f
        dst.set(cx - hw, cy - hh, cx + hw, cy + hh)
        canvas.withRotation(s.rot, cx, cy) { drawBitmap(bmp, null, dst, paint) }
    }
}

/** Full-screen editor on the real home screen: drag to move, pinch to resize, twist to rotate. */
@SuppressLint("ViewConstructor")
class StickerArrangeView(
    context: Context,
    initial: List<Sticker>,
    private val sw: Float,
    private val sh: Float,
    private val accent: Int,
    private val onDone: (List<Sticker>) -> Unit,
) : View(context) {
    private var list = initial
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = 0xCCFFFFFF.toInt() }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15 * d; textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = 0xFFFFFFFF.toInt() }
    private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * d; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt(); setShadowLayer(4 * d, 0f, 1 * d, 0xAA000000.toInt()) }
    private val dst = RectF()
    private val done = RectF()
    private var selected = -1
    private var lastAx = 0f; private var lastAy = 0f; private var lastBx = 0f; private var lastBy = 0f
    private var idA = -1; private var idB = -1
    private var downOnDone = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pw = 140 * d; val ph = 48 * d
        done.set(w / 2f - pw / 2, h - 120 * d, w / 2f + pw / 2, h - 120 * d + ph)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0x33000000)
        val w = width.toFloat(); val h = height.toFloat()
        list.forEachIndexed { i, s ->
            val bmp = StickerStore.bitmap(context, s.src, (s.size * w).roundToInt()) ?: return@forEachIndexed
            val hw = StickerMath.halfW(s, w); val hh = StickerMath.halfH(s, w)
            val cx = s.x * w; val cy = s.y * h
            dst.set(cx - hw, cy - hh, cx + hw, cy + hh)
            canvas.withRotation(s.rot, cx, cy) {
                drawBitmap(bmp, null, dst, paint)
                if (i == selected) drawRoundRect(dst, 8 * d, 8 * d, outline)
            }
        }
        canvas.drawText("Drag, pinch or twist a sticker", w / 2, 96 * d, hint)
        pill.color = accent
        canvas.drawRoundRect(done, done.height() / 2, done.height() / 2, pill)
        canvas.drawText("Done", done.centerX(), done.centerY() + text.textSize / 3, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat().takeIf { it > 0 } ?: sw
        val h = height.toFloat().takeIf { it > 0 } ?: sh
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downOnDone = done.contains(e.x, e.y)
                if (downOnDone) return true
                idA = e.getPointerId(0); idB = -1
                lastAx = e.x; lastAy = e.y
                val i = StickerMath.topmostAt(list, e.x, e.y, w, h)
                selected = if (i >= 0) { list = StickerMath.bringToFront(list, i); list.lastIndex } else -1
                if (selected >= 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (idB == -1 && !downOnDone) {
                val idx = e.actionIndex
                idB = e.getPointerId(idx); lastBx = e.getX(idx); lastBy = e.getY(idx)
                val ia = e.findPointerIndex(idA)
                if (ia >= 0) { lastAx = e.getX(ia); lastAy = e.getY(ia) }
            }
            MotionEvent.ACTION_MOVE -> if (selected >= 0 && !downOnDone) {
                val ia = e.findPointerIndex(idA)
                if (ia < 0) return true
                val ax = e.getX(ia); val ay = e.getY(ia)
                val ib = if (idB >= 0) e.findPointerIndex(idB) else -1
                val s = list[selected]
                val next = if (ib >= 0) {
                    val bx = e.getX(ib); val by = e.getY(ib)
                    val g = StickerMath.pinch(lastAx, lastAy, lastBx, lastBy, ax, ay, bx, by)
                    lastBx = bx; lastBy = by
                    StickerMath.transform(s, g.panX, g.panY, g.zoom, g.rotDeg, w, h)
                } else StickerMath.transform(s, ax - lastAx, ay - lastAy, 1f, 0f, w, h)
                lastAx = ax; lastAy = ay
                list = list.toMutableList().also { it[selected] = next }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == idA) {
                    // Keep moving with the finger that is still down.
                    idA = idB; lastAx = lastBx; lastAy = lastBy
                }
                idB = -1
            }
            MotionEvent.ACTION_UP -> {
                if (downOnDone && done.contains(e.x, e.y)) onDone(list)
                downOnDone = false; idA = -1; idB = -1
            }
            MotionEvent.ACTION_CANCEL -> { downOnDone = false; idA = -1; idB = -1 }
        }
        return true
    }
}
