package app.custo.feature.widgetsets

import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconStyle
import app.custo.feature.themes.WallEdge
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgetsets.PackDArt as A

/** Theme pack D: dark luxe sets. Art lives in ThemePackDArt.kt; the wallpaper is the Carbon bands premium style. */
object ThemePackD {

    private fun v(vararg d: Decoration) = listOf(d.toList())

    /** Dark-glass pills with 1 px [metal] hairlines over carbon bands. */
    private fun carbon(id: String, name: String, description: String, metal: Int, edge: WallEdge, seed: Int): WidgetTheme {
        val glint = A.glint(metal)
        return WidgetTheme(
            id = id,
            name = name,
            category = ThemeCategory.LUXE,
            description = description,
            palette = ThemePalette(background = 0xFF141519.toInt(), text = A.TEXT, accent = metal, secondary = 0xFF2A2B30.toInt(), line = 0xFF000000.toInt()),
            font = WidgetFont.SANS,
            frame = FrameStyle.PLAIN,
            framePainter = A.glassFrame(metal),
            ink = InkStyle.COLOR,
            decorations = mapOf(
                WidgetKind.CLOCK to v(custom(glint, Anchor.TOP_RIGHT, 0.32f)),
                WidgetKind.PHOTO to v(custom(glint, Anchor.BOTTOM_RIGHT, 0.32f)),
                WidgetKind.DATE to v(custom(glint, Anchor.TOP_RIGHT, 0.26f)),
            ),
            fallbackDecorations = listOf(emptyList()),
            banner = "Stay golden",
            bannerSub = "",
            wallpaper = A.wallpaper(edge, seed),
            widgets = listOf(
                SetWidget(WidgetKind.BANNER, 4, 1),
                SetWidget(WidgetKind.CLOCK, 4, 1),
                SetWidget(WidgetKind.DATE, 2, 1),
                SetWidget(WidgetKind.BATTERY_RING, 2, 1),
                SetWidget(WidgetKind.CALENDAR, 2, 2),
                SetWidget(WidgetKind.PHOTO, 2, 2),
                SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
            ),
            icons = IconStyle(
                shape = IconShape.CIRCLE, bg = listOf(0xFF24252B.toInt(), 0xFF0B0B0E.toInt()), angle = 160f,
                fgColor = metal, shadow = 0.3f, glow = 0.12f, glowColor = metal,
            ),
            clockLook = ClockLook.BIG,
            textScale = 0.95f,
            corner = 26f,
            hud = A.Hud(metal),
        )
    }

    val CARBON_GOLD = carbon(
        "d_carbon", "Carbon",
        "Dark glass pills with gold hairlines on crossing carbon bands. Serif banner, gold battery ring and today marker.",
        A.GOLD, WallEdge.GOLD, 11,
    )

    val CARBON_SILVER = carbon(
        "d_carbon_silver", "Carbon Silver",
        "The Carbon set in cool silver: smoked glass, silver hairlines and rims on carbon bands.",
        A.SILVER, WallEdge.SILVER, 23,
    )

    val themes: List<WidgetTheme> = listOf(CARBON_GOLD, CARBON_SILVER)
}
