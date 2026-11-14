package app.custo.feature.widgets

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.custo.R

/**
 * The bundled widget faces: [WidgetFont.PIXEL] (Pixelify Sans, OFL) and [WidgetFont.HEAVY] (Anton, OFL).
 * Canvas drawing has no Context at hand, so the faces are loaded once at startup ([init]).
 */
object WidgetFonts {
    private val cache = HashMap<WidgetFont, Typeface>()

    /** Loads the bundled faces (two small files) so Canvas code can use them anywhere. */
    fun init(ctx: Context) {
        WidgetFont.entries.forEach { f ->
            val res = resource(f) ?: return@forEach
            runCatching { ResourcesCompat.getFont(ctx, res) }.getOrNull()?.let { tf -> synchronized(cache) { cache[f] = tf } }
        }
    }

    /** The font resource behind a bundled face, or null for system families. */
    fun resource(font: WidgetFont): Int? = when (font) {
        WidgetFont.PIXEL -> R.font.custo_pixel
        WidgetFont.HEAVY -> R.font.custo_heavy
        else -> null
    }

    /** Typeface for a bundled face; a close system family when it isn't loaded (tests, early startup). */
    fun typeface(font: WidgetFont): Typeface = synchronized(cache) { cache[font] } ?: when (font) {
        WidgetFont.PIXEL -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        WidgetFont.HEAVY -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        else -> Typeface.DEFAULT
    }
}
