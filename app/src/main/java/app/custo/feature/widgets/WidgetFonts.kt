package app.custo.feature.widgets

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.custo.R

/**
 * The bundled widget faces (all SIL OFL, licenses in assets/licenses): [WidgetFont.PIXEL] (Pixelify Sans),
 * [WidgetFont.HEAVY] (Anton), [WidgetFont.ROUNDED] (Fredoka), [WidgetFont.TECH] (Orbitron),
 * [WidgetFont.HAND] (Caveat) and [WidgetFont.ELEGANT] (DM Serif Display).
 * Canvas drawing has no Context at hand, so the faces are loaded once at startup ([init]).
 */
object WidgetFonts {
    private val cache = HashMap<WidgetFont, Typeface>()

    /** Loads the bundled faces (a few small files) so Canvas code can use them anywhere. */
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
        WidgetFont.ROUNDED -> R.font.custo_round
        WidgetFont.TECH -> R.font.custo_tech
        WidgetFont.HAND -> R.font.custo_hand
        WidgetFont.ELEGANT -> R.font.custo_elegant
        else -> null
    }

    /** Typeface for a bundled face; a close system family when it isn't loaded (tests, early startup). */
    fun typeface(font: WidgetFont): Typeface = synchronized(cache) { cache[font] } ?: when (font) {
        WidgetFont.PIXEL -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        WidgetFont.HEAVY -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        WidgetFont.ROUNDED -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        WidgetFont.TECH -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        WidgetFont.HAND -> Typeface.create("cursive", Typeface.NORMAL)
        WidgetFont.ELEGANT -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }
}
