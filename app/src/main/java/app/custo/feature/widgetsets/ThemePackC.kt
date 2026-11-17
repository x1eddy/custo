package app.custo.feature.widgetsets

import app.custo.feature.icons.IconPattern
import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconStyle
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgetsets.PackCArt as A

/** Theme pack C. Its art lives in ThemePackCArt.kt (stickers, frames) and ThemePackCWalls.kt (wallpapers). */
object ThemePackC {

    private fun v(vararg d: Decoration) = listOf(d.toList())
    private fun s(art: StickerArt, anchor: Anchor, scale: Float = 1f, rotate: Float = 0f) = custom(art, anchor, scale, rotate)
    private fun col(c: Long) = c.toInt()

    // ---------- 1. Cozy Christmas ----------

    val CHRISTMAS = WidgetTheme(
        id = "c_cozy_christmas",
        name = "Cozy Christmas",
        category = ThemeCategory.CHRISTMAS,
        description = "Knitted sweater frames, cocoa, gingerbread and twinkling string lights on tartan.",
        palette = ThemePalette(
            background = col(0xFFB83A3F), text = col(0xFFFFF6E8), accent = col(0xFFF2C14E),
            secondary = col(0xFF2F6B45), line = col(0xFF3E1416),
        ),
        font = WidgetFont.SERIF,
        frame = FrameStyle.DRIP,
        framePainter = A.knitFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.WREATH, Anchor.LEFT, 1f), s(A.GINGERBREAD, Anchor.RIGHT, 0.95f, 10f)),
            WidgetKind.CLOCK to v(s(A.COCOA, Anchor.BOTTOM_LEFT, 0.8f, -8f), s(A.ORNAMENT, Anchor.TOP_RIGHT, 0.7f, 12f)),
            WidgetKind.PHOTO to v(s(A.FIREPLACE, Anchor.CENTER, 1.7f), s(A.GINGERBREAD, Anchor.BOTTOM_RIGHT, 0.7f, 12f)),
            WidgetKind.CALENDAR to v(s(A.WREATH, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.COUNTDOWN to v(s(A.ORNAMENT, Anchor.BOTTOM_RIGHT, 0.75f, 10f)),
            WidgetKind.BATTERY to v(s(A.LIGHTS, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.BATTERY_RING to v(s(A.LIGHTS, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.DATE to v(s(A.GINGERBREAD, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            WidgetKind.APPS to v(s(A.ORNAMENT, Anchor.BOTTOM_RIGHT, 0.55f, -10f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.ORNAMENT, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            listOf(s(A.COCOA, Anchor.BOTTOM_LEFT, 0.65f, -8f)),
        ),
        banner = "Cozy Christmas",
        bannerSub = "Cocoa by the fire",
        wallpaper = PackCWalls.christmas,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Christmas"), countdownTo = 12 to 25),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(col(0xFFB83A3F), col(0xFF8E262C)), angle = 160f,
            fgColor = col(0xFFFFF6E8), pattern = IconPattern.GRID, patternColor = col(0xFF3E1416), patternOpacity = 0.15f, shadow = 0.2f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 16f,
    )

    // ---------- 2. Teddy bears ----------

    val TEDDY = WidgetTheme(
        id = "c_teddy_bears",
        name = "Teddy Bears",
        category = ThemeCategory.CUTE,
        description = "Soft beige patchwork with stitched edges, teddy bears, honey and hearts.",
        palette = ThemePalette(
            background = col(0xFFFFF4E6), text = col(0xFF6B4A3A), accent = col(0xFFFFB7C5),
            secondary = col(0xFFE8C9A6), line = col(0xFF7A5238),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.PLAIN,
        framePainter = A.patchFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.TEDDY_WAVE, Anchor.LEFT, 1.05f), s(A.HONEY_POT, Anchor.RIGHT, 0.9f, 8f)),
            WidgetKind.CLOCK to v(s(A.STITCH_HEART, Anchor.TOP_RIGHT, 0.65f, 14f), s(A.TEDDY_FACE, Anchor.BOTTOM_LEFT, 0.8f, -8f)),
            WidgetKind.PHOTO to v(s(A.TEDDY_HEART, Anchor.CENTER, 1.7f), s(A.STITCH_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 12f)),
            WidgetKind.CALENDAR to v(s(A.TEDDY, Anchor.BOTTOM_RIGHT, 0.95f)),
            WidgetKind.STEPS to v(s(A.TEDDY_WAVE, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.BATTERY to v(s(A.HONEY_POT, Anchor.BOTTOM_RIGHT, 0.75f, 8f)),
            WidgetKind.BATTERY_RING to v(s(A.HONEY_POT, Anchor.BOTTOM_RIGHT, 0.7f, 8f)),
            WidgetKind.DATE to v(s(A.STITCH_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            WidgetKind.NOTE to v(s(A.TEDDY_FACE, Anchor.BOTTOM_RIGHT, 0.7f, 8f)),
            WidgetKind.APPS to v(s(A.STITCH_HEART, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.STITCH_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            listOf(s(A.TEDDY_FACE, Anchor.BOTTOM_LEFT, 0.7f, -8f)),
        ),
        banner = "Bear hugs",
        bannerSub = "You are loved",
        wallpaper = PackCWalls.teddy,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.NOTE, 2, 1, WidgetStyle(note = "Hug someone today")),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(col(0xFFFFF4E6)), fgColor = col(0xFF7A5238),
            pattern = IconPattern.DOTS, patternColor = col(0xFFFFB7C5), patternOpacity = 0.5f, shadow = 0.15f,
        ),
        clockLook = ClockLook.BIG,
        corner = 20f,
    )

    // ---------- 3. Cake ----------

    val CAKE = WidgetTheme(
        id = "c_cake",
        name = "Cake",
        category = ThemeCategory.SWEET,
        description = "Piped frosting frames, layer cakes, cupcakes and strawberries in a pastel bakery.",
        palette = ThemePalette(
            background = col(0xFFFFF8F0), text = col(0xFF8A4B5C), accent = col(0xFFFFB6CF),
            secondary = col(0xFFA6E3D0), line = col(0xFF8A4B5C),
        ),
        font = WidgetFont.CURSIVE,
        frame = FrameStyle.DRIP,
        framePainter = A.frostingFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.CUPCAKE, Anchor.LEFT, 1f, -8f), s(A.STRAWBERRY, Anchor.RIGHT, 0.85f, 12f)),
            WidgetKind.CLOCK to v(s(A.CANDLE, Anchor.TOP_RIGHT, 0.75f, 12f), s(A.STRAWBERRY, Anchor.BOTTOM_LEFT, 0.65f, -12f)),
            WidgetKind.PHOTO to v(s(A.LAYER_CAKE, Anchor.CENTER, 1.7f), s(A.CANDLE, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            WidgetKind.CALENDAR to v(s(A.CAKE_SLICE, Anchor.BOTTOM_RIGHT, 0.85f)),
            WidgetKind.COUNTDOWN to v(s(A.LAYER_CAKE, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.BATTERY to v(s(A.CUPCAKE, Anchor.BOTTOM_RIGHT, 0.75f, 8f)),
            WidgetKind.BATTERY_RING to v(s(A.CUPCAKE, Anchor.BOTTOM_RIGHT, 0.7f, 8f)),
            WidgetKind.DATE to v(s(A.STRAWBERRY, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            WidgetKind.APPS to v(s(A.STRAWBERRY, Anchor.BOTTOM_RIGHT, 0.5f, 10f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.STRAWBERRY, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            listOf(s(A.CUPCAKE, Anchor.BOTTOM_LEFT, 0.65f, -8f)),
        ),
        banner = "Life is sweet",
        bannerSub = "Have a slice of cake",
        wallpaper = PackCWalls.cake,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "My birthday")),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.FLOWER, bg = listOf(col(0xFFFFD6E2), col(0xFFFFF0F4)), angle = 90f,
            fgColor = col(0xFF8A4B5C), pattern = IconPattern.CONFETTI, patternColor = col(0xFFA6E3D0), patternOpacity = 0.5f, shadow = 0.15f,
        ),
        clockLook = ClockLook.BIG,
        corner = 20f,
    )

    // ---------- 4. Pink ----------

    val PINK = WidgetTheme(
        id = "c_pink",
        name = "Pink",
        category = ThemeCategory.CUTE,
        description = "All pink: glossy frames with pearls, ribbons, hearts and sparkles.",
        palette = ThemePalette(
            background = col(0xFFFFE6F0), text = col(0xFFD6427F), accent = col(0xFFFF7FB0),
            secondary = col(0xFFFFC1D8), line = col(0xFFC23B74),
        ),
        font = WidgetFont.SERIF,
        frame = FrameStyle.PLAIN,
        framePainter = A.glossyFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.RIBBON, Anchor.LEFT, 1f, -10f), s(A.GLOSSY_HEART, Anchor.RIGHT, 0.85f, 12f)),
            WidgetKind.CLOCK to v(s(A.RIBBON, Anchor.TOP_RIGHT, 0.75f, 14f), s(A.SPARKLES, Anchor.BOTTOM_LEFT, 0.6f)),
            WidgetKind.PHOTO to v(s(A.GLOSSY_HEART, Anchor.CENTER, 1.6f), s(A.TIARA, Anchor.TOP_LEFT, 0.7f, -14f)),
            WidgetKind.CALENDAR to v(s(A.PERFUME, Anchor.BOTTOM_RIGHT, 0.8f, 8f)),
            WidgetKind.STEPS to v(s(A.SPARKLES, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY to v(s(A.PEARLS, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.BATTERY_RING to v(s(A.PEARLS, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.DATE to v(s(A.GLOSSY_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 12f)),
            WidgetKind.QUOTE to v(s(A.TIARA, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            WidgetKind.APPS to v(s(A.SPARKLES, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.GLOSSY_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 12f)),
            listOf(s(A.SPARKLES, Anchor.BOTTOM_LEFT, 0.55f)),
        ),
        banner = "Pretty in pink",
        bannerSub = "",
        wallpaper = PackCWalls.pink,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.QUOTE, 4, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(col(0xFFFFE6F0), col(0xFFFFB9D4)), angle = 90f,
            fgColor = col(0xFFD6427F), glow = 0.3f, glowColor = col(0xFFFFFFFF), shadow = 0.15f,
        ),
        clockLook = ClockLook.MINIMAL,
        corner = 24f,
    )

    // ---------- 5. Squishy ----------

    val SQUISHY = WidgetTheme(
        id = "c_squishy",
        name = "Squishy",
        category = ThemeCategory.CUTE,
        description = "Puffy pillow frames and round little blob friends floating on pastel clouds.",
        palette = ThemePalette(
            background = col(0xFFFFFBF5), text = col(0xFF7C6A8E), accent = col(0xFFFFC4DD),
            secondary = col(0xFFE6DDF7), line = col(0xFF6E5D80),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.PLAIN,
        framePainter = A.pillowFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.BLOB_CAT, Anchor.LEFT, 1f), s(A.BLOB_FROG, Anchor.RIGHT, 0.95f)),
            WidgetKind.CLOCK to v(s(A.BLOB_BUNNY, Anchor.BOTTOM_LEFT, 0.85f, -6f), s(A.PUFF_STAR, Anchor.TOP_RIGHT, 0.55f, 12f)),
            WidgetKind.PHOTO to v(s(A.BLOB_AXOLOTL, Anchor.CENTER, 1.7f), s(A.BLOB_COW, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.CALENDAR to v(s(A.BLOB_COW, Anchor.BOTTOM_RIGHT, 0.85f)),
            WidgetKind.MOOD to v(s(A.BLOB_FROG, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.BATTERY to v(s(A.BLOB_BUNNY, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.BATTERY_RING to v(s(A.BLOB_BUNNY, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.WATER to v(s(A.BLOB_AXOLOTL, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.DATE to v(s(A.BLOB_CAT, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.APPS to v(s(A.PUFF_STAR, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.BLOB_CAT, Anchor.BOTTOM_RIGHT, 0.7f)),
            listOf(s(A.BLOB_FROG, Anchor.BOTTOM_LEFT, 0.7f)),
        ),
        banner = "Stay squishy",
        bannerSub = "Soft day ahead",
        wallpaper = PackCWalls.squishy,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.WATER, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(col(0xFFFFFBF5), col(0xFFE6DDF7)), angle = 120f,
            fgColor = col(0xFF7C6A8E), shadow = 0.2f,
        ),
        clockLook = ClockLook.BIG,
        corner = 26f,
    )

    // ---------- 6. Café ----------

    val CAFE = WidgetTheme(
        id = "c_cafe",
        name = "Café",
        category = ThemeCategory.SWEET,
        description = "Chalkboard menu frames, lattes, croissants and coffee beans in warm browns.",
        palette = ThemePalette(
            background = col(0xFF33403A), text = col(0xFFF6EFE3), accent = col(0xFFE8B98A),
            secondary = col(0xFFA06A3F), line = col(0xFF3E2618),
        ),
        font = WidgetFont.CURSIVE,
        frame = FrameStyle.CARTOON,
        framePainter = A.boardFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.COFFEE_CUP, Anchor.LEFT, 1f), s(A.CROISSANT, Anchor.RIGHT, 0.95f, 10f)),
            WidgetKind.CLOCK to v(s(A.LATTE_ART, Anchor.TOP_RIGHT, 0.75f, 10f), s(A.BEANS, Anchor.BOTTOM_LEFT, 0.6f)),
            WidgetKind.PHOTO to v(s(A.COFFEE_CUP, Anchor.CENTER, 1.6f), s(A.CROISSANT, Anchor.BOTTOM_RIGHT, 0.7f, -10f)),
            WidgetKind.CALENDAR to v(s(A.TAKEAWAY, Anchor.BOTTOM_RIGHT, 0.85f, 8f)),
            WidgetKind.WORK to v(s(A.TAKEAWAY, Anchor.BOTTOM_RIGHT, 0.75f, 8f)),
            WidgetKind.BATTERY to v(s(A.BEANS, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.BATTERY_RING to v(s(A.BEANS, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.DATE to v(s(A.CROISSANT, Anchor.BOTTOM_RIGHT, 0.65f, -8f)),
            WidgetKind.APPS to v(s(A.BEANS, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.BEANS, Anchor.BOTTOM_RIGHT, 0.6f)),
            listOf(s(A.LATTE_ART, Anchor.BOTTOM_LEFT, 0.65f)),
        ),
        banner = "But first, coffee",
        bannerSub = "Today's special: you",
        wallpaper = PackCWalls.cafe,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.WORK, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(col(0xFF33403A)), fgColor = col(0xFFF6EFE3),
            pattern = IconPattern.DOTS, patternColor = col(0xFFFFFFFF), patternOpacity = 0.06f, shadow = 0.25f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 10f,
    )

    // ---------- 7. Sakura ----------

    val SAKURA = WidgetTheme(
        id = "c_sakura",
        wallhavenQuery = "cherry blossom",
        name = "Sakura",
        category = ThemeCategory.NATURE,
        description = "Washi paper frames with ink brush strokes, cherry blossoms, lanterns and falling petals.",
        palette = ThemePalette(
            background = col(0xFFFFF8F6), text = col(0xFF8E3B55), accent = col(0xFFFF9DB8),
            secondary = col(0xFFFAD6E0), line = col(0xFF5B3A3F),
        ),
        font = WidgetFont.SERIF,
        frame = FrameStyle.PLAIN,
        framePainter = A.washiFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.BRANCH, Anchor.LEFT, 1.1f), s(A.PAPER_LANTERN, Anchor.RIGHT, 0.9f, 6f)),
            WidgetKind.CLOCK to v(s(A.BLOSSOM, Anchor.TOP_LEFT, 0.7f, 10f), s(A.PETALS, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.PHOTO to v(s(A.FAN, Anchor.CENTER, 1.6f), s(A.BLOSSOM, Anchor.BOTTOM_LEFT, 0.6f, 20f)),
            WidgetKind.CALENDAR to v(s(A.BRANCH, Anchor.BOTTOM_RIGHT, 0.9f)),
            WidgetKind.COUNTDOWN to v(s(A.PAPER_LANTERN, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.MOON to v(s(A.PAPER_LANTERN, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.BATTERY to v(s(A.BLOSSOM, Anchor.BOTTOM_RIGHT, 0.6f, 15f)),
            WidgetKind.DATE to v(s(A.PETALS, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.QUOTE to v(s(A.BRANCH, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.APPS to v(s(A.BLOSSOM, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.BLOSSOM, Anchor.BOTTOM_RIGHT, 0.6f, 15f)),
            listOf(s(A.PETALS, Anchor.BOTTOM_LEFT, 0.6f)),
        ),
        banner = "Sakura season",
        bannerSub = "Bloom where you are",
        wallpaper = PackCWalls.sakura,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Spring"), countdownTo = 3 to 20),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.MOON, 2, 2),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(col(0xFFFFF8F6)), fgColor = col(0xFF8E3B55),
            pattern = IconPattern.WAVES, patternColor = col(0xFFFF9DB8), patternOpacity = 0.3f, shadow = 0.12f,
        ),
        clockLook = ClockLook.MINIMAL,
        corner = 14f,
    )

    // ---------- 8. Kitty ----------

    val KITTY = WidgetTheme(
        id = "c_kitty",
        name = "Kitty",
        category = ThemeCategory.CUTE,
        description = "Frames with cat ears and whiskers, kittens, yarn, fish and paw prints.",
        palette = ThemePalette(
            background = col(0xFFFFF7EF), text = col(0xFF7A5A6B), accent = col(0xFFFFB0C8),
            secondary = col(0xFFFFD79A), line = col(0xFF5E4452),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.FROSTED,
        framePainter = A.catEarFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.CAT_SIT, Anchor.LEFT, 1.05f), s(A.YARN, Anchor.RIGHT, 0.85f, 10f)),
            WidgetKind.CLOCK to v(s(A.PAW, Anchor.BOTTOM_LEFT, 0.6f, -15f), s(A.FISH, Anchor.BOTTOM_RIGHT, 0.65f, -10f)),
            WidgetKind.PHOTO to v(s(A.CAT_FACE, Anchor.CENTER, 1.6f), s(A.YARN, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.CALENDAR to v(s(A.CAT_SIT, Anchor.BOTTOM_RIGHT, 0.9f)),
            WidgetKind.STEPS to v(s(A.PAW, Anchor.BOTTOM_RIGHT, 0.6f, 15f)),
            WidgetKind.BATTERY to v(s(A.FISH, Anchor.BOTTOM_RIGHT, 0.7f, -8f)),
            WidgetKind.BATTERY_RING to v(s(A.FISH, Anchor.BOTTOM_RIGHT, 0.65f, -8f)),
            WidgetKind.DATE to v(s(A.YARN, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.APPS to v(s(A.PAW, Anchor.BOTTOM_RIGHT, 0.5f, 15f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.PAW, Anchor.BOTTOM_RIGHT, 0.6f, 15f)),
            listOf(s(A.CAT_FACE, Anchor.BOTTOM_LEFT, 0.65f, -8f)),
        ),
        banner = "Meow",
        bannerSub = "Purr-fect day",
        wallpaper = PackCWalls.kitty,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(col(0xFFFFF7EF)), fgColor = col(0xFF7A5A6B),
            pattern = IconPattern.DOTS, patternColor = col(0xFFFFB0C8), patternOpacity = 0.4f, shadow = 0.15f,
        ),
        clockLook = ClockLook.BIG,
        corner = 22f,
    )

    // ---------- 9. Autumn ----------

    val AUTUMN = WidgetTheme(
        id = "c_autumn",
        wallhavenQuery = "autumn",
        name = "Autumn",
        category = ThemeCategory.SEASONS,
        description = "Flannel plaid bands, maple leaves, acorns, mushrooms and pumpkins in warm oranges.",
        palette = ThemePalette(
            background = col(0xFFFFF5E6), text = col(0xFF7A3E1D), accent = col(0xFFE07A2F),
            secondary = col(0xFF9C4A1F), line = col(0xFF5A3018),
        ),
        font = WidgetFont.SERIF,
        frame = FrameStyle.DRIP,
        framePainter = A.plaidBandFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.MAPLE, Anchor.LEFT, 1f, -15f), s(A.MUSHROOM, Anchor.RIGHT, 0.9f, 6f)),
            WidgetKind.CLOCK to v(s(A.ACORN, Anchor.BOTTOM_LEFT, 0.7f, -12f), s(A.MAPLE_RED, Anchor.BOTTOM_RIGHT, 0.6f, 20f)),
            WidgetKind.PHOTO to v(s(A.PUMPKIN, Anchor.CENTER, 1.6f), s(A.LEAF, Anchor.TOP_LEFT, 0.6f, -20f)),
            WidgetKind.CALENDAR to v(s(A.MUSHROOM, Anchor.BOTTOM_RIGHT, 0.85f)),
            WidgetKind.COUNTDOWN to v(s(A.PUMPKIN, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.BATTERY to v(s(A.ACORN, Anchor.BOTTOM_RIGHT, 0.65f, 12f)),
            WidgetKind.BATTERY_RING to v(s(A.ACORN, Anchor.BOTTOM_RIGHT, 0.6f, 12f)),
            WidgetKind.DATE to v(s(A.LEAF, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.APPS to v(s(A.MAPLE, Anchor.BOTTOM_RIGHT, 0.5f, 20f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.MAPLE, Anchor.BOTTOM_RIGHT, 0.6f, 20f)),
            listOf(s(A.ACORN, Anchor.BOTTOM_LEFT, 0.6f, -12f)),
        ),
        banner = "Hello autumn",
        bannerSub = "Sweater weather",
        wallpaper = PackCWalls.autumn,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Halloween"), countdownTo = 10 to 31),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(col(0xFFFFF5E6), col(0xFFF7DDBA)), angle = 90f,
            fgColor = col(0xFF7A3E1D), pattern = IconPattern.GRID, patternColor = col(0xFFE07A2F), patternOpacity = 0.2f, shadow = 0.15f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 18f,
    )

    // ---------- 10. Camping ----------

    val CAMPING = WidgetTheme(
        id = "c_camping",
        wallhavenQuery = "forest",
        name = "Camping",
        category = ThemeCategory.NATURE,
        description = "Wood-plank frames, a tent, a campfire and marshmallows under a starry mountain sky.",
        palette = ThemePalette(
            background = col(0xFFE3B57D), text = col(0xFF3B2414), accent = col(0xFFF2994A),
            secondary = col(0xFFC98E52), line = col(0xFF3B2414),
        ),
        font = WidgetFont.MONO,
        frame = FrameStyle.CARTOON,
        framePainter = A.woodFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.TENT_ART, Anchor.LEFT, 1.05f), s(A.CAMPFIRE, Anchor.RIGHT, 0.95f)),
            WidgetKind.CLOCK to v(s(A.PINE_TREE, Anchor.BOTTOM_LEFT, 0.8f), s(A.MARSHMALLOW, Anchor.TOP_RIGHT, 0.7f, 10f)),
            WidgetKind.PHOTO to v(s(A.MOUNTAINS, Anchor.CENTER, 1.7f), s(A.PINE_TREE, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.CALENDAR to v(s(A.TENT_ART, Anchor.BOTTOM_RIGHT, 0.85f)),
            WidgetKind.COUNTDOWN to v(s(A.CAMPFIRE, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.STEPS to v(s(A.MOUNTAINS, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.BATTERY to v(s(A.MARSHMALLOW, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.BATTERY_RING to v(s(A.MARSHMALLOW, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.MOON to v(s(A.PINE_TREE, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.APPS to v(s(A.PINE_TREE, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.PINE_TREE, Anchor.BOTTOM_RIGHT, 0.65f)),
            listOf(s(A.CAMPFIRE, Anchor.BOTTOM_LEFT, 0.6f)),
        ),
        banner = "Happy camper",
        bannerSub = "Into the woods",
        wallpaper = PackCWalls.camping,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Summer"), countdownTo = 6 to 21),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.MOON, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(col(0xFF26386A), col(0xFF0E1A3A)), angle = 160f,
            fgColor = col(0xFFFFE3B0), glow = 0.25f, glowColor = col(0xFFFFB74D), shadow = 0.25f,
        ),
        clockLook = ClockLook.BIG,
        corner = 12f,
    )

    // ---------- 11. Rainbow ----------

    val RAINBOW = WidgetTheme(
        id = "c_rainbow",
        name = "Rainbow",
        category = ThemeCategory.FUN,
        description = "Bubbly frames with rainbow piping, happy clouds, sunshine and candy colors.",
        palette = ThemePalette(
            background = col(0xFFFFFFFF), text = col(0xFF5B4BB7), accent = col(0xFFFF6B8B),
            secondary = col(0xFF7FD3FF), line = col(0xFF3F3A66),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.CARTOON,
        framePainter = A.pipingFrame,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.RAINBOW_ART, Anchor.LEFT, 1.05f), s(A.SUNSHINE, Anchor.RIGHT, 0.95f, 10f)),
            WidgetKind.CLOCK to v(s(A.CLOUD, Anchor.TOP_RIGHT, 0.75f), s(A.RAINBOW_HEART, Anchor.BOTTOM_LEFT, 0.6f, -12f)),
            WidgetKind.PHOTO to v(s(A.RAINBOW_ART, Anchor.CENTER, 1.7f), s(A.SUNSHINE, Anchor.TOP_LEFT, 0.6f)),
            WidgetKind.CALENDAR to v(s(A.SUNSHINE, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.MOOD to v(s(A.CLOUD, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.BATTERY to v(s(A.RAINBOW_HEART, Anchor.BOTTOM_RIGHT, 0.65f, 10f)),
            WidgetKind.BATTERY_RING to v(s(A.RAINBOW_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            WidgetKind.WEEK to v(s(A.CLOUD, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.DATE to v(s(A.RAINBOW_HEART, Anchor.BOTTOM_RIGHT, 0.6f, 10f)),
            WidgetKind.APPS to v(s(A.SUNSHINE, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.CLOUD, Anchor.BOTTOM_RIGHT, 0.65f)),
            listOf(s(A.RAINBOW_HEART, Anchor.BOTTOM_LEFT, 0.6f, -10f)),
        ),
        banner = "Good vibes",
        bannerSub = "Find your rainbow",
        wallpaper = PackCWalls.rainbow,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.WEEK, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.MOOD, 2, 2),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE,
            bg = listOf(col(0xFFFF8FA3), col(0xFFFFD166), col(0xFF7FD3FF)), angle = 135f,
            fgColor = col(0xFFFFFFFF), pattern = IconPattern.CONFETTI, patternColor = col(0xFFFFFFFF), patternOpacity = 0.35f, shadow = 0.2f,
        ),
        clockLook = ClockLook.BIG,
        corner = 26f,
    )

    val themes: List<WidgetTheme> = listOf(CHRISTMAS, TEDDY, CAKE, PINK, SQUISHY, CAFE, SAKURA, KITTY, AUTUMN, CAMPING, RAINBOW)
}
