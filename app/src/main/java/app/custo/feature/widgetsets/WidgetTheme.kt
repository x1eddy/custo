package app.custo.feature.widgetsets

import android.graphics.Canvas
import app.custo.feature.icons.IconStyle
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.HAlign
import app.custo.feature.widgets.VAlign
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle
import java.time.LocalDate

enum class ThemeCategory(val label: String) {
    CUTE("Cute"), SWEET("Sweet"), CHRISTMAS("Christmas"), GAMES("Games"), HEROES("Anime & heroes"), MUSIC("Music"),
    MINIMAL("Minimal"), NATURE("Nature"), SEASONS("Seasons"), FUN("Fun"),
}

/**
 * The colors of one set. [background] fills the widget cards, [text] is drawn on them,
 * [accent] is the frame's star color (icing, tape, progress), [secondary] a second fill,
 * [line] the outline / ink color.
 */
data class ThemePalette(
    val background: Int,
    val text: Int,
    val accent: Int,
    val secondary: Int,
    val line: Int,
)

/** How every widget card in a set is framed. Insets are in dp. */
enum class FrameStyle(
    val label: String,
    /** Room around the card for stickers and the frame's own extras (snow cap, shadow). */
    val outerTop: Float, val outerSide: Float, val outerBottom: Float,
    /** Space between the card edge and the text inside. */
    val innerTop: Float, val innerSide: Float, val innerBottom: Float,
) {
    PLAIN("Plain rounded", 6f, 6f, 6f, 10f, 10f, 10f),
    DRIP("Icing drips", 6f, 6f, 6f, 20f, 10f, 10f),
    CARTOON("Cartoon outline", 6f, 6f, 10f, 10f, 10f, 10f),
    FROSTED("Frosted glass", 12f, 6f, 6f, 14f, 10f, 10f),
    STICKY("Sticky note", 8f, 8f, 8f, 16f, 10f, 10f),
}

/** Where a sticker sits, relative to the card. */
enum class Anchor(val x: Float, val y: Float) {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f),
}

enum class Sticker {
    BOW, CANDY, LOLLIPOP, GUMBALL, BALLOON, FLOWER, HEART, STAR, GIRL, CLOVER,
    SNOWMAN, TREE, SANTA, STOCKING, BELL, GIFT, ENVELOPE, PHONE, CAMERA, SPARKLE,
    SNOWFLAKE, CANDY_CANE, SHOE, BATTERY,
}

/**
 * One sticker on a widget. [scale] is relative to the default sticker size
 * (about a third of the card's short side); [rotate] in degrees.
 */
data class Decoration(
    val sticker: Sticker,
    val anchor: Anchor,
    val scale: Float = 1f,
    val rotate: Float = 0f,
    /** Theme-pack sticker drawn instead of [sticker] (see [custom]). */
    val art: StickerArt? = null,
)

/** A sticker drawn by a theme pack with the [Pen] helpers (unit coords: center 0,0, edges ±0.5). */
typealias StickerArt = Pen.() -> Unit

/** A decoration using a theme pack's own sticker art. */
fun custom(art: StickerArt, anchor: Anchor, scale: Float = 1f, rotate: Float = 0f) =
    Decoration(Sticker.STAR, anchor, scale, rotate, art)

/** Lets a theme pack draw its own card frame. [r] = corner radius px, [d] = density. */
fun interface FramePainter {
    fun paint(c: Canvas, theme: WidgetTheme, card: android.graphics.RectF, r: Float, d: Float, seed: Int)
}

/**
 * How a theme's HUD (see [ThemeHud]) takes part in one widget kind:
 * [BEHIND] draws a backdrop and the widget's own content goes on top (live clocks),
 * [REPLACE] draws the whole content from [HudData] (no Glance text at all),
 * [SLOTS] draws slots that the widget fills with app icons (see [ThemeHud.appSlots]).
 */
enum class HudMode { BEHIND, REPLACE, SLOTS }

/** Live values a HUD shows. Kept small and comparable so widget bitmaps can be cached on it. */
data class HudData(
    val today: LocalDate,
    val battery: Int,
    val charging: Boolean,
    val locale: java.util.Locale = java.util.Locale.getDefault(),
) {
    companion object {
        /** What gallery previews show. */
        fun sample(today: LocalDate = LocalDate.now()) = HudData(today, 76, false)
    }
}

/**
 * Lets a theme draw a widget's content itself, game-UI style (hearts for battery, a hotbar for apps...).
 * The same code paints home-screen widget bitmaps, in-app previews and the gallery cards, so all three match.
 */
interface ThemeHud {
    /** How [kind] is handled, or null to leave it to the normal widget content. */
    fun mode(kind: WidgetKind): HudMode?

    /**
     * Draws [kind]'s content. [content] is the card's content area (card minus the frame's inner insets),
     * [card] the whole card, [d] pixels per dp.
     */
    fun draw(c: Canvas, theme: WidgetTheme, kind: WidgetKind, style: WidgetStyle, data: HudData, card: android.graphics.RectF, content: android.graphics.RectF, d: Float)

    /** For [HudMode.SLOTS]: how many equal slots to draw across [w] x [h] (dp) for [apps] apps. */
    fun appSlots(apps: Int, w: Float, h: Float): Int = apps.coerceAtLeast(1)
}

/** How stickers are inked: full color with an outline, or line art in one color. */
enum class InkStyle { COLOR, LINE }

/** Paints a full-screen wallpaper of [w] × [h] pixels. Must be deterministic. */
fun interface WallPainter {
    fun paint(c: Canvas, w: Float, h: Float)
}

/**
 * One widget in a set: its kind, its size on a 4-column home grid, and any content
 * it starts with (banner text, countdown title...). [countdownTo] = month to day,
 * resolved to the next one when the set is applied.
 */
data class SetWidget(
    val kind: WidgetKind,
    val cols: Int,
    val rows: Int,
    val base: WidgetStyle = WidgetStyle(),
    val countdownTo: Pair<Int, Int>? = null,
)

/** A themed bundle: a wallpaper plus widgets drawn in one style. Everything is drawn in code. */
data class WidgetTheme(
    val id: String,
    val name: String,
    val category: ThemeCategory,
    val description: String,
    val palette: ThemePalette,
    val font: WidgetFont,
    val frame: FrameStyle,
    val ink: InkStyle,
    /** Stickers per widget kind; each inner list is one variant, picked by the widget's slot. */
    val decorations: Map<WidgetKind, List<List<Decoration>>>,
    /** Used for kinds with no entry in [decorations]. */
    val fallbackDecorations: List<List<Decoration>>,
    val banner: String? = null,
    val bannerSub: String = "",
    val wallpaper: WallPainter,
    val widgets: List<SetWidget>,
    val icons: IconStyle? = null,
    val clockLook: ClockLook = ClockLook.BIG,
    val textScale: Float = 0.9f,
    val textShadow: Boolean = false,
    val shadowColor: Int = 0x66000000,
    /** Card corner radius in dp. */
    val corner: Float = 22f,
    /** Overrides how [frame] is drawn (its insets still come from [frame]). */
    val framePainter: FramePainter? = null,
    /** Draws some widgets' content as game UI (see [ThemeHud]). */
    val hud: ThemeHud? = null,
) {
    /** The HUD mode for [kind], or null when this theme has no HUD for it. */
    fun hudMode(kind: WidgetKind?): HudMode? = kind?.let { hud?.mode(it) }

    fun decorationsFor(kind: WidgetKind?, slot: Int): List<Decoration> {
        val variants = kind?.let { decorations[it] } ?: fallbackDecorations
        if (variants.isEmpty()) return emptyList()
        return variants[Math.floorMod(slot, variants.size)]
    }

    /** Content padding (dp) used where a single number is needed (rings, icon rows). */
    val contentPadding: Int get() = (frame.outerSide + frame.innerSide).toInt()

    /**
     * [base] (keeping its content: notes, photos, apps...) with this theme's look.
     * Used both when a set pins a fresh widget and when restyling one already placed.
     */
    fun styleFor(kind: WidgetKind, slot: Int, base: WidgetStyle): WidgetStyle = base.copy(
        themeId = id,
        themeKind = kind.name,
        themeSlot = slot,
        themed = false,
        bg = palette.background,
        fg = palette.text,
        alpha = 1f,
        corner = corner.toInt(),
        font = font,
        gradient = false,
        borderWidth = 0,
        padding = contentPadding,
        hAlign = HAlign.CENTER,
        vAlign = VAlign.CENTER,
        textScale = textScale,
        textShadow = textShadow,
        shadowColor = shadowColor,
        clock = if (kind == WidgetKind.CLOCK) clockLook else base.clock,
    )

    /** The style a set widget gets when it's pinned. */
    fun styleForSetWidget(index: Int, today: LocalDate = LocalDate.now()): WidgetStyle {
        val sw = widgets[index]
        var base = sw.base
        if (sw.kind == WidgetKind.BANNER && banner != null && base.bannerText == WidgetStyle().bannerText) {
            base = base.copy(bannerText = banner, bannerSub = bannerSub)
        }
        sw.countdownTo?.let { (m, d) -> base = base.copy(countdownDay = SetLayout.nextOccurrence(m, d, today).toEpochDay()) }
        return styleFor(sw.kind, slotOf(index), base)
    }

    /** Slot = how many earlier widgets in the set share this kind, so twins get different stickers. */
    fun slotOf(index: Int): Int = widgets.take(index).count { it.kind == widgets[index].kind }
}
