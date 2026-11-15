package app.custo.feature.widgetsets

import app.custo.feature.icons.IconPattern
import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconStyle
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle

/**
 * Every built-in widget set. To add one: write a [WidgetTheme] below (palette, frame, stickers,
 * widgets, wallpaper) and add it to [all]. New art goes in [ThemeArt] (stickers) or
 * [ThemeWallpapers] (wallpapers).
 */
object WidgetThemes {

    private fun v(vararg d: Decoration) = listOf(d.toList())

    // ---------- Good luck (cute, yellow) ----------

    val GOOD_LUCK = WidgetTheme(
        id = "good_luck",
        name = "Good luck",
        category = ThemeCategory.CUTE,
        description = "Sunny yellow, icing drips, candy and bows.",
        palette = ThemePalette(
            background = 0xFFFFFBEA.toInt(), text = 0xFF6B4226.toInt(), accent = 0xFFFFB3CB.toInt(),
            secondary = 0xFF8FD3FF.toInt(), line = 0xFF6B4226.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.DRIP,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(Decoration(Sticker.CLOVER, Anchor.LEFT, 0.95f, -15f), Decoration(Sticker.BOW, Anchor.RIGHT, 0.95f, 12f)),
            WidgetKind.CLOCK to v(Decoration(Sticker.BOW, Anchor.TOP_RIGHT, 0.85f, 15f), Decoration(Sticker.STAR, Anchor.BOTTOM_LEFT, 0.65f, -10f)),
            WidgetKind.PHOTO to v(Decoration(Sticker.GIRL, Anchor.CENTER, 1.7f), Decoration(Sticker.CANDY, Anchor.BOTTOM_RIGHT, 0.7f, -20f)),
            WidgetKind.CALENDAR to v(Decoration(Sticker.GUMBALL, Anchor.BOTTOM_RIGHT, 0.85f)),
            WidgetKind.STEPS to v(Decoration(Sticker.SHOE, Anchor.BOTTOM_RIGHT, 0.65f, -10f)),
            WidgetKind.BATTERY to v(Decoration(Sticker.LOLLIPOP, Anchor.BOTTOM_RIGHT, 0.75f, 20f)),
            WidgetKind.BATTERY_RING to v(Decoration(Sticker.LOLLIPOP, Anchor.BOTTOM_RIGHT, 0.7f, 20f)),
            WidgetKind.DATE to v(Decoration(Sticker.CLOVER, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            WidgetKind.APPS to v(Decoration(Sticker.SPARKLE, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.COUNTDOWN to v(Decoration(Sticker.BALLOON, Anchor.BOTTOM_RIGHT, 0.75f, 10f)),
            WidgetKind.NOTE to v(Decoration(Sticker.ENVELOPE, Anchor.BOTTOM_RIGHT, 0.7f, -8f)),
            WidgetKind.QUOTE to v(Decoration(Sticker.FLOWER, Anchor.BOTTOM_RIGHT, 0.7f)),
        ),
        fallbackDecorations = listOf(
            listOf(Decoration(Sticker.STAR, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            listOf(Decoration(Sticker.HEART, Anchor.BOTTOM_LEFT, 0.65f, -10f)),
        ),
        banner = "Good luck",
        bannerSub = "Today is your lucky day",
        wallpaper = ThemeWallpapers.goodLuck,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFFFFFBEA.toInt()), fgColor = 0xFF6B4226.toInt(),
            pattern = IconPattern.DOTS, patternColor = 0xFFFFB3CB.toInt(), patternOpacity = 0.55f, shadow = 0.15f,
        ),
        clockLook = ClockLook.BIG,
        corner = 22f,
    )

    // ---------- Frosted Christmas ----------

    val FROSTED_CHRISTMAS = WidgetTheme(
        id = "frosted_christmas",
        name = "Frosted Christmas",
        category = ThemeCategory.CHRISTMAS,
        description = "Glass frames with snow on top, a snowman and a quiet snowy night.",
        palette = ThemePalette(
            background = 0xFF7FAEBE.toInt(), text = 0xFFFFFFFF.toInt(), accent = 0xFFE53945.toInt(),
            secondary = 0xFF2E5E6E.toInt(), line = 0xFF1D3B4A.toInt(),
        ),
        font = WidgetFont.SERIF,
        frame = FrameStyle.FROSTED,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(Decoration(Sticker.TREE, Anchor.LEFT, 1f), Decoration(Sticker.GIFT, Anchor.RIGHT, 0.85f, 8f)),
            WidgetKind.CLOCK to v(Decoration(Sticker.BELL, Anchor.BOTTOM_LEFT, 0.7f, -15f), Decoration(Sticker.SNOWFLAKE, Anchor.BOTTOM_RIGHT, 0.5f)),
            WidgetKind.PHOTO to v(Decoration(Sticker.SNOWMAN, Anchor.CENTER, 1.6f), Decoration(Sticker.TREE, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.COUNTDOWN to v(Decoration(Sticker.GIFT, Anchor.BOTTOM_RIGHT, 0.7f, 8f)),
            WidgetKind.BATTERY to v(Decoration(Sticker.CANDY_CANE, Anchor.BOTTOM_RIGHT, 0.7f, 15f)),
            WidgetKind.BATTERY_RING to v(Decoration(Sticker.CANDY_CANE, Anchor.BOTTOM_RIGHT, 0.7f, 15f)),
            WidgetKind.CALENDAR to v(Decoration(Sticker.STOCKING, Anchor.BOTTOM_RIGHT, 0.8f, 10f)),
            WidgetKind.STEPS to v(Decoration(Sticker.SNOWFLAKE, Anchor.BOTTOM_RIGHT, 0.55f)),
            WidgetKind.DATE to v(Decoration(Sticker.BELL, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            WidgetKind.APPS to v(Decoration(Sticker.SNOWFLAKE, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(Decoration(Sticker.SNOWFLAKE, Anchor.BOTTOM_RIGHT, 0.55f)),
            listOf(Decoration(Sticker.BELL, Anchor.BOTTOM_LEFT, 0.6f, -10f)),
        ),
        banner = "Merry Christmas",
        bannerSub = "",
        wallpaper = ThemeWallpapers.frostedNight,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Christmas"), countdownTo = 12 to 25),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 4, 1),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFF7FAEBE.toInt(), 0xFF2E5E6E.toInt()), angle = 160f,
            fgColor = 0xFFFFFFFF.toInt(), glow = 0.3f, glowColor = 0xFFFFFFFF.toInt(), shadow = 0.2f,
        ),
        clockLook = ClockLook.STACKED,
        textShadow = true,
        shadowColor = 0x88102A36.toInt(),
        corner = 24f,
    )

    // ---------- Christmas doodle sticky notes (red and green) ----------

    private fun doodle(id: String, name: String, ink: Int, tape: Int, wallBg: Int, wallGrid: Int, seed: Int) = WidgetTheme(
        id = id,
        name = name,
        category = ThemeCategory.CHRISTMAS,
        description = "Sticky notes with washi tape and hand-drawn Christmas doodles.",
        palette = ThemePalette(background = 0xFFFFF8EC.toInt(), text = ink, accent = tape, secondary = 0xFFF0DCC0.toInt(), line = ink),
        font = WidgetFont.CURSIVE,
        frame = FrameStyle.STICKY,
        ink = InkStyle.LINE,
        decorations = mapOf(
            WidgetKind.BANNER to v(Decoration(Sticker.STOCKING, Anchor.LEFT, 0.95f, -10f), Decoration(Sticker.GIFT, Anchor.RIGHT, 0.9f, 8f)),
            WidgetKind.CLOCK to v(Decoration(Sticker.BELL, Anchor.BOTTOM_LEFT, 0.7f, -12f)),
            WidgetKind.PHOTO to v(Decoration(Sticker.SANTA, Anchor.CENTER, 1.7f), Decoration(Sticker.STAR, Anchor.BOTTOM_LEFT, 0.55f, -10f)),
            WidgetKind.CALENDAR to v(Decoration(Sticker.TREE, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.COUNTDOWN to v(Decoration(Sticker.GIFT, Anchor.BOTTOM_RIGHT, 0.7f, 8f)),
            WidgetKind.BATTERY to v(Decoration(Sticker.CANDY_CANE, Anchor.BOTTOM_RIGHT, 0.7f, 15f)),
            WidgetKind.BATTERY_RING to v(Decoration(Sticker.CANDY_CANE, Anchor.BOTTOM_RIGHT, 0.7f, 15f)),
            WidgetKind.DATE to v(Decoration(Sticker.STAR, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            WidgetKind.STEPS to v(Decoration(Sticker.SHOE, Anchor.BOTTOM_RIGHT, 0.6f, -8f)),
            WidgetKind.APPS to v(Decoration(Sticker.SPARKLE, Anchor.BOTTOM_LEFT, 0.5f)),
            WidgetKind.NOTE to v(Decoration(Sticker.ENVELOPE, Anchor.BOTTOM_RIGHT, 0.6f, -8f)),
        ),
        fallbackDecorations = listOf(
            listOf(Decoration(Sticker.STAR, Anchor.BOTTOM_RIGHT, 0.6f)),
            listOf(Decoration(Sticker.SNOWFLAKE, Anchor.BOTTOM_LEFT, 0.6f)),
        ),
        banner = "Merry Christmas",
        bannerSub = "Ho ho ho!",
        wallpaper = ThemeWallpapers.doodle(wallBg, wallGrid, 0xFFFFFFFF.toInt(), seed),
        widgets = listOf(
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Christmas"), countdownTo = 12 to 25),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(0xFFFFF8EC.toInt()), fgColor = ink,
            pattern = IconPattern.GRID, patternColor = ink, patternOpacity = 0.12f, shadow = 0.12f,
        ),
        clockLook = ClockLook.BIG,
        corner = 4f,
    )

    val DOODLE_RED = doodle("doodle_red", "Doodle notes", 0xFFC62828.toInt(), 0xFFEF8A8A.toInt(), 0xFFC62828.toInt(), 0xFFB71C1C.toInt(), 31)
    val DOODLE_GREEN = doodle("doodle_green", "Doodle notes, green", 0xFF2E7D32.toInt(), 0xFFEF8A8A.toInt(), 0xFF2E6B3F.toInt(), 0xFF285E37.toInt(), 47)

    val all: List<WidgetTheme> = listOf(GOOD_LUCK, FROSTED_CHRISTMAS, DOODLE_RED, DOODLE_GREEN) +
        ThemePackA.themes + ThemePackB.themes + ThemePackC.themes + ThemePackD.themes

    fun byId(id: String?): WidgetTheme? = id?.let { i -> all.firstOrNull { it.id == i } }

    /** Categories that have at least one set, in enum order. */
    val categories: List<ThemeCategory> get() = ThemeCategory.entries.filter { c -> all.any { it.category == c } }
}
