package app.custo.feature.widgetsets

import app.custo.feature.icons.IconPattern
import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconStyle
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle

/** Theme pack A: games and neon. Its art lives in ThemePackAArt.kt, wallpapers in ThemePackAWalls.kt. */
object ThemePackA {
    private fun v(vararg d: Decoration) = listOf(d.toList())
    private fun s(art: StickerArt, anchor: Anchor, scale: Float = 1f, rotate: Float = 0f) = custom(art, anchor, scale, rotate)
    private val A = PackAArt

    // ---------- Blocky ----------

    val BLOCKY = WidgetTheme(
        id = "a_blocky",
        name = "Blocky",
        category = ThemeCategory.GAMES,
        description = "Grass-topped dirt blocks, gem tools and a pixel landscape.",
        palette = ThemePalette(
            background = 0xFF8A603F.toInt(), text = 0xFFFFFFFF.toInt(), accent = 0xFF62B33F.toInt(),
            secondary = 0xFF3CCFC6.toInt(), line = 0xFF1C1A1E.toInt(),
        ),
        font = WidgetFont.MONO,
        frame = FrameStyle.DRIP,
        framePainter = PackAFrames.pixelBlock,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.pickaxe, Anchor.LEFT, 1f, -8f), s(A.sword, Anchor.RIGHT, 1f, 8f)),
            WidgetKind.CLOCK to v(s(A.pixelSun, Anchor.TOP_RIGHT, 0.75f), s(A.torch, Anchor.BOTTOM_LEFT, 0.75f)),
            WidgetKind.PHOTO to v(s(A.slime, Anchor.BOTTOM_RIGHT, 0.8f), s(A.grassBlock, Anchor.BOTTOM_LEFT, 0.6f)),
            WidgetKind.CALENDAR to v(s(A.pixelTree, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.BATTERY to v(s(A.gem, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.BATTERY_RING to v(s(A.gem, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.STEPS to v(s(A.pickaxe, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.DATE to v(s(A.torch, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.APPS to v(s(A.grassBlock, Anchor.BOTTOM_RIGHT, 0.55f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.gem, Anchor.BOTTOM_RIGHT, 0.65f)),
            listOf(s(A.slime, Anchor.BOTTOM_LEFT, 0.65f)),
        ),
        banner = "Mine & build",
        bannerSub = "One block at a time",
        wallpaper = PackAWalls.blocky,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(0xFF62B33F.toInt(), 0xFF8A603F.toInt()), angle = 90f,
            fgColor = 0xFFFFFFFF.toInt(), pattern = IconPattern.GRID, patternColor = 0xFF1C1A1E.toInt(), patternOpacity = 0.18f, shadow = 0.3f,
        ),
        clockLook = ClockLook.BIG,
        textShadow = true,
        shadowColor = 0xAA000000.toInt(),
        corner = 4f,
    )

    // ---------- Matrix code ----------

    val MATRIX = WidgetTheme(
        id = "a_matrix_code",
        name = "Matrix Code",
        category = ThemeCategory.GAMES,
        description = "Black terminals, neon green glyph rain and a blinking cursor.",
        palette = ThemePalette(
            background = 0xF2020904.toInt(), text = 0xFF39FF6A.toInt(), accent = 0xFF39FF6A.toInt(),
            secondary = 0xFF0B3D1A.toInt(), line = 0xFF39FF6A.toInt(),
        ),
        font = WidgetFont.MONO,
        frame = FrameStyle.STICKY,
        framePainter = PackAFrames.terminal,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.terminal, Anchor.LEFT, 0.95f, -6f), s(A.lock, Anchor.RIGHT, 0.85f, 6f)),
            WidgetKind.CLOCK to v(s(A.binary, Anchor.BOTTOM_RIGHT, 0.7f, 6f)),
            WidgetKind.PHOTO to v(s(A.glitch, Anchor.TOP_LEFT, 0.75f), s(A.codeTag, Anchor.BOTTOM_RIGHT, 0.7f, -6f)),
            WidgetKind.CALENDAR to v(s(A.lock, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.BATTERY to v(s(A.glitch, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY_RING to v(s(A.glitch, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.SCREEN_TIME to v(s(A.terminal, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.DATE to v(s(A.binary, Anchor.BOTTOM_RIGHT, 0.55f, -6f)),
            WidgetKind.APPS to v(s(A.codeTag, Anchor.BOTTOM_RIGHT, 0.55f)),
            WidgetKind.NOTE to v(s(A.terminal, Anchor.BOTTOM_RIGHT, 0.6f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.codeTag, Anchor.BOTTOM_RIGHT, 0.6f)),
            listOf(s(A.binary, Anchor.BOTTOM_LEFT, 0.6f)),
        ),
        banner = "SYSTEM ONLINE",
        bannerSub = "> access granted_",
        wallpaper = PackAWalls.codeRain,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.SCREEN_TIME, 2, 2),
            SetWidget(WidgetKind.NOTE, 2, 2, WidgetStyle(note = "$ todo --today")),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(0xFF020904.toInt()), fgColor = 0xFF39FF6A.toInt(),
            pattern = IconPattern.STRIPES, patternColor = 0xFF39FF6A.toInt(), patternOpacity = 0.08f,
            glow = 0.45f, glowColor = 0xFF39FF6A.toInt(), shadow = 0f,
        ),
        clockLook = ClockLook.MINIMAL,
        textShadow = true,
        shadowColor = 0xAA39FF6A.toInt(),
        corner = 8f,
    )

    // ---------- Blocky summer ----------

    val BLOCKY_SUMMER = WidgetTheme(
        id = "a_blocky_summer",
        name = "Blocky Summer",
        category = ThemeCategory.GAMES,
        description = "Cube-head friends at the beach: sun, palms, surf and ice cream.",
        palette = ThemePalette(
            background = 0xFFFFF6E0.toInt(), text = 0xFF0B4F8A.toInt(), accent = 0xFFFF6B4A.toInt(),
            secondary = 0xFF29C5F6.toInt(), line = 0xFF0B3556.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.CARTOON,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.sunnySun, Anchor.LEFT, 1f), s(A.surfboard, Anchor.RIGHT, 1f)),
            WidgetKind.CLOCK to v(s(A.palm, Anchor.BOTTOM_LEFT, 0.85f), s(A.beachBall, Anchor.TOP_RIGHT, 0.6f, 15f)),
            WidgetKind.PHOTO to v(s(A.summerKid, Anchor.BOTTOM_LEFT, 0.9f), s(A.summerKid2, Anchor.BOTTOM_RIGHT, 0.9f)),
            WidgetKind.CALENDAR to v(s(A.iceCream, Anchor.BOTTOM_RIGHT, 0.75f, 10f)),
            WidgetKind.COUNTDOWN to v(s(A.sunnySun, Anchor.BOTTOM_RIGHT, 0.75f)),
            WidgetKind.BATTERY to v(s(A.iceCream, Anchor.BOTTOM_RIGHT, 0.65f, 12f)),
            WidgetKind.BATTERY_RING to v(s(A.iceCream, Anchor.BOTTOM_RIGHT, 0.65f, 12f)),
            WidgetKind.WATER to v(s(A.beachBall, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.STEPS to v(s(A.summerKid, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.APPS to v(s(A.beachBall, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.beachBall, Anchor.BOTTOM_RIGHT, 0.6f)),
            listOf(s(A.palm, Anchor.BOTTOM_LEFT, 0.65f)),
        ),
        banner = "Summer vibes",
        bannerSub = "Sun, sand and blocks",
        wallpaper = PackAWalls.beach,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Summer"), countdownTo = 6 to 21),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.WATER, 2, 2),
            SetWidget(WidgetKind.APPS, 4, 1),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFF29C5F6.toInt(), 0xFF7FE3E8.toInt()), angle = 90f,
            fgColor = 0xFFFFFFFF.toInt(), pattern = IconPattern.WAVES, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.25f, shadow = 0.25f,
        ),
        clockLook = ClockLook.BIG,
        corner = 20f,
    )

    // ---------- Brick party ----------

    val BRICK_PARTY = WidgetTheme(
        id = "a_brick_party",
        name = "Brick Party",
        category = ThemeCategory.GAMES,
        description = "Studded toy bricks in primary colors, coins, trophies and checkpoints.",
        palette = ThemePalette(
            background = 0xFF2563EB.toInt(), text = 0xFFFFFFFF.toInt(), accent = 0xFFFACC15.toInt(),
            secondary = 0xFFE3342F.toInt(), line = 0xFF1B2433.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.CARTOON,
        framePainter = PackAFrames.brick,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.builder, Anchor.LEFT, 1f, -6f), s(A.goldTrophy, Anchor.RIGHT, 0.95f, 8f)),
            WidgetKind.CLOCK to v(s(A.coin, Anchor.TOP_RIGHT, 0.65f, 10f), s(A.brickYellow, Anchor.BOTTOM_LEFT, 0.7f, -8f)),
            WidgetKind.PHOTO to v(s(A.builder2, Anchor.BOTTOM_RIGHT, 0.95f), s(A.checkpoint, Anchor.TOP_LEFT, 0.7f, -6f)),
            WidgetKind.CALENDAR to v(s(A.obstacle, Anchor.BOTTOM_RIGHT, 0.8f)),
            WidgetKind.STOPWATCH to v(s(A.checkpoint, Anchor.BOTTOM_RIGHT, 0.65f, 6f)),
            WidgetKind.BATTERY to v(s(A.coin, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY_RING to v(s(A.coin, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.STEPS to v(s(A.brickRed, Anchor.BOTTOM_RIGHT, 0.6f, 8f)),
            WidgetKind.DATE to v(s(A.brickBlue, Anchor.BOTTOM_RIGHT, 0.6f, -8f)),
            WidgetKind.APPS to v(s(A.coin, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.coin, Anchor.BOTTOM_RIGHT, 0.6f)),
            listOf(s(A.brickRed, Anchor.BOTTOM_LEFT, 0.6f, -8f)),
        ),
        banner = "Let's build!",
        bannerSub = "Collect every coin",
        wallpaper = PackAWalls.brickCourse,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STOPWATCH, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(0xFFE3342F.toInt()), fgColor = 0xFFFFFFFF.toInt(),
            pattern = IconPattern.DOTS, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.2f, shadow = 0.35f,
        ),
        clockLook = ClockLook.BIG,
        textShadow = true,
        shadowColor = 0x661B2433,
        corner = 8f,
    )

    // ---------- Tactical red ----------

    val TACTICAL = WidgetTheme(
        id = "a_tactical_red",
        name = "Tactical Red",
        category = ThemeCategory.GAMES,
        description = "Cut-corner plates in black and red, hazard stripes and sharp emblems.",
        palette = ThemePalette(
            background = 0xFF16161B.toInt(), text = 0xFFF2EDE4.toInt(), accent = 0xFFE8312F.toInt(),
            secondary = 0xFF3A3A42.toInt(), line = 0xFF0B0B0D.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.PLAIN,
        framePainter = PackAFrames.tactical,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.chevrons, Anchor.LEFT, 0.85f), s(A.crosshair, Anchor.RIGHT, 0.95f)),
            WidgetKind.CLOCK to v(s(A.crosshair, Anchor.TOP_RIGHT, 0.6f)),
            WidgetKind.PHOTO to v(s(A.diamond, Anchor.BOTTOM_RIGHT, 0.75f), s(A.hazard, Anchor.TOP_LEFT, 0.7f, -20f)),
            WidgetKind.CALENDAR to v(s(A.chevrons, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY to v(s(A.warning, Anchor.BOTTOM_RIGHT, 0.55f)),
            WidgetKind.BATTERY_RING to v(s(A.warning, Anchor.BOTTOM_RIGHT, 0.55f)),
            WidgetKind.WEEK to v(s(A.diamond, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.DATE to v(s(A.hazard, Anchor.BOTTOM_RIGHT, 0.55f, -15f)),
            WidgetKind.APPS to v(s(A.diamond, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.diamond, Anchor.BOTTOM_RIGHT, 0.55f)),
            listOf(s(A.chevrons, Anchor.BOTTOM_LEFT, 0.55f)),
        ),
        banner = "LOCK IN",
        bannerSub = "Plant. Defend. Win.",
        wallpaper = PackAWalls.shards,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY_RING, 2, 2),
            SetWidget(WidgetKind.WEEK, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.HEXAGON, bg = listOf(0xFF16161B.toInt()), fgColor = 0xFFE8312F.toInt(),
            pattern = IconPattern.STRIPES, patternColor = 0xFFE8312F.toInt(), patternOpacity = 0.12f, shadow = 0.3f,
        ),
        clockLook = ClockLook.STACKED,
        textScale = 0.92f,
        corner = 2f,
    )

    // ---------- Battle royale ----------

    val BATTLE_ROYALE = WidgetTheme(
        id = "a_battle_royale",
        name = "Battle Royale",
        category = ThemeCategory.GAMES,
        description = "Bright cartoon panels, a closing storm, floating islands and loot.",
        palette = ThemePalette(
            background = 0xFF4A2AB8.toInt(), text = 0xFFFFFFFF.toInt(), accent = 0xFFFFD23F.toInt(),
            secondary = 0xFF2D7FF9.toInt(), line = 0xFF15093D.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.CARTOON,
        framePainter = PackAFrames.battlePanel,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.crown, Anchor.LEFT, 0.95f, -10f), s(A.lootChest, Anchor.RIGHT, 0.95f)),
            WidgetKind.CLOCK to v(s(A.storm, Anchor.TOP_RIGHT, 0.65f), s(A.glider, Anchor.BOTTOM_LEFT, 0.75f)),
            WidgetKind.PHOTO to v(s(A.supplyDrop, Anchor.TOP_RIGHT, 0.8f, 8f), s(A.island, Anchor.BOTTOM_LEFT, 0.75f)),
            WidgetKind.CALENDAR to v(s(A.lootChest, Anchor.BOTTOM_RIGHT, 0.7f)),
            WidgetKind.COUNTDOWN to v(s(A.storm, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.BATTERY to v(s(A.supplyDrop, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.BATTERY_RING to v(s(A.supplyDrop, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.STEPS to v(s(A.glider, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.APPS to v(s(A.crown, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.lootChest, Anchor.BOTTOM_RIGHT, 0.6f)),
            listOf(s(A.storm, Anchor.BOTTOM_LEFT, 0.6f)),
        ),
        banner = "Victory!",
        bannerSub = "Last one standing",
        wallpaper = PackAWalls.stormSky,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "New season"), countdownTo = 3 to 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFF6A3BE0.toInt(), 0xFF2D7FF9.toInt()), angle = 135f,
            fgColor = 0xFFFFFFFF.toInt(), pattern = IconPattern.CONFETTI, patternColor = 0xFFFFD23F.toInt(), patternOpacity = 0.35f, shadow = 0.35f,
        ),
        clockLook = ClockLook.BIG,
        textShadow = true,
        shadowColor = 0xAA15093D.toInt(),
        corner = 18f,
    )

    // ---------- Gamer RGB ----------

    val GAMER_RGB = WidgetTheme(
        id = "a_gamer_rgb",
        name = "Gamer RGB",
        category = ThemeCategory.GAMES,
        description = "Dark cards with rainbow glow edges, controllers, headsets and keycaps.",
        palette = ThemePalette(
            background = 0xFF0E0E16.toInt(), text = 0xFFEDEBFF.toInt(), accent = 0xFF00E5FF.toInt(),
            secondary = 0xFFFF2BD6.toInt(), line = 0xFF050508.toInt(),
        ),
        font = WidgetFont.MONO,
        frame = FrameStyle.PLAIN,
        framePainter = PackAFrames.rgbGlow,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.controller, Anchor.LEFT, 1f, -10f), s(A.headset, Anchor.RIGHT, 0.95f, 8f)),
            WidgetKind.CLOCK to v(s(A.gg, Anchor.TOP_RIGHT, 0.65f, 8f)),
            WidgetKind.PHOTO to v(s(A.keys, Anchor.BOTTOM_LEFT, 0.8f, -6f), s(A.mouse, Anchor.TOP_RIGHT, 0.6f, 15f)),
            WidgetKind.CALENDAR to v(s(A.controller, Anchor.BOTTOM_RIGHT, 0.7f, -8f)),
            WidgetKind.SCREEN_TIME to v(s(A.headset, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY to v(s(A.mouse, Anchor.BOTTOM_RIGHT, 0.55f, 15f)),
            WidgetKind.BATTERY_RING to v(s(A.mouse, Anchor.BOTTOM_RIGHT, 0.55f, 15f)),
            WidgetKind.STOPWATCH to v(s(A.keys, Anchor.BOTTOM_RIGHT, 0.55f)),
            WidgetKind.APPS to v(s(A.gg, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.gg, Anchor.BOTTOM_RIGHT, 0.55f)),
            listOf(s(A.keys, Anchor.BOTTOM_LEFT, 0.55f)),
        ),
        banner = "GG",
        bannerSub = "Good game, well played",
        wallpaper = PackAWalls.rgbDesk,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.SCREEN_TIME, 2, 2),
            SetWidget(WidgetKind.BATTERY_RING, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STOPWATCH, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFF14141E.toInt()), fgColor = 0xFF00E5FF.toInt(),
            glow = 0.5f, glowColor = 0xFFFF2BD6.toInt(), shadow = 0.2f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 16f,
    )

    // ---------- Synthwave ----------

    val SYNTHWAVE = WidgetTheme(
        id = "a_synthwave",
        name = "Synthwave",
        category = ThemeCategory.MUSIC,
        description = "80s sunset, neon grid, chrome stars and palm silhouettes.",
        palette = ThemePalette(
            background = 0xFF1B0B3A.toInt(), text = 0xFFFFE9FF.toInt(), accent = 0xFFFF3EA5.toInt(),
            secondary = 0xFF2DE2E6.toInt(), line = 0xFF0D0420.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.PLAIN,
        framePainter = PackAFrames.synth,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.retroSun, Anchor.LEFT, 0.95f), s(A.neonPalm, Anchor.RIGHT, 1f)),
            WidgetKind.CLOCK to v(s(A.chromeStar, Anchor.TOP_RIGHT, 0.6f), s(A.shades, Anchor.BOTTOM_LEFT, 0.7f, -10f)),
            WidgetKind.PHOTO to v(s(A.neonPalm, Anchor.BOTTOM_LEFT, 0.9f), s(A.chromeStar, Anchor.TOP_RIGHT, 0.55f)),
            WidgetKind.MEDIA to v(s(A.cassette, Anchor.BOTTOM_RIGHT, 0.7f, 8f)),
            WidgetKind.CALENDAR to v(s(A.retroSun, Anchor.BOTTOM_RIGHT, 0.65f)),
            WidgetKind.BATTERY to v(s(A.cassette, Anchor.BOTTOM_RIGHT, 0.6f, -8f)),
            WidgetKind.BATTERY_RING to v(s(A.cassette, Anchor.BOTTOM_RIGHT, 0.6f, -8f)),
            WidgetKind.DATE to v(s(A.chromeStar, Anchor.BOTTOM_RIGHT, 0.5f)),
            WidgetKind.APPS to v(s(A.shades, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.chromeStar, Anchor.BOTTOM_RIGHT, 0.5f)),
            listOf(s(A.cassette, Anchor.BOTTOM_LEFT, 0.55f)),
        ),
        banner = "Night drive",
        bannerSub = "Neon dreams since '84",
        wallpaper = PackAWalls.synthSunset,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.MEDIA, 4, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFFFF3EA5.toInt(), 0xFF6A1B9A.toInt()), angle = 90f,
            fgColor = 0xFFFFFFFF.toInt(), pattern = IconPattern.STRIPES, patternColor = 0xFF2DE2E6.toInt(), patternOpacity = 0.2f,
            glow = 0.3f, glowColor = 0xFFFF3EA5.toInt(), shadow = 0.2f,
        ),
        clockLook = ClockLook.BIG,
        textShadow = true,
        shadowColor = 0xCCFF3EA5.toInt(),
        corner = 14f,
    )

    // ---------- Neon cyberpunk ----------

    val CYBERPUNK = WidgetTheme(
        id = "a_neon_cyberpunk",
        name = "Neon Cyberpunk",
        category = ThemeCategory.GAMES,
        description = "Rainy neon city, HUD panels in magenta and yellow, circuits and chips.",
        palette = ThemePalette(
            background = 0xEB0B0A18.toInt(), text = 0xFFFCE93A.toInt(), accent = 0xFFFF2E97.toInt(),
            secondary = 0xFFFCE93A.toInt(), line = 0xFF07060F.toInt(),
        ),
        font = WidgetFont.MONO,
        frame = FrameStyle.STICKY,
        framePainter = PackAFrames.hud,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.chip, Anchor.LEFT, 0.9f, -8f), s(A.neonSign, Anchor.RIGHT, 0.95f, 6f)),
            WidgetKind.CLOCK to v(s(A.hudRing, Anchor.TOP_RIGHT, 0.65f), s(A.circuit, Anchor.BOTTOM_LEFT, 0.7f)),
            WidgetKind.PHOTO to v(s(A.umbrella, Anchor.BOTTOM_RIGHT, 0.85f, 8f), s(A.circuit, Anchor.TOP_LEFT, 0.6f)),
            WidgetKind.CALENDAR to v(s(A.chip, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY to v(s(A.circuit, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY_RING to v(s(A.circuit, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.ALARM to v(s(A.hudRing, Anchor.BOTTOM_RIGHT, 0.55f)),
            WidgetKind.DATE to v(s(A.neonSign, Anchor.BOTTOM_RIGHT, 0.55f, -6f)),
            WidgetKind.APPS to v(s(A.hudRing, Anchor.BOTTOM_RIGHT, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.chip, Anchor.BOTTOM_RIGHT, 0.55f)),
            listOf(s(A.circuit, Anchor.BOTTOM_LEFT, 0.55f)),
        ),
        banner = "Neon district",
        bannerSub = "Stay wired",
        wallpaper = PackAWalls.neonCity,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.ALARM, 2, 1),
            SetWidget(WidgetKind.DATE, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.HEXAGON, bg = listOf(0xFF0B0A18.toInt(), 0xFF2A0E3E.toInt()), angle = 160f,
            fgColor = 0xFFFCE93A.toInt(), glow = 0.4f, glowColor = 0xFFFF2E97.toInt(), shadow = 0.2f,
        ),
        clockLook = ClockLook.STACKED,
        textShadow = true,
        shadowColor = 0x99FF2E97.toInt(),
        corner = 4f,
    )

    // ---------- Football ----------

    val FOOTBALL = WidgetTheme(
        id = "a_football",
        name = "Football",
        category = ThemeCategory.FUN,
        description = "Scoreboard panels over a striped pitch, with ball, goal, whistle and cup.",
        palette = ThemePalette(
            background = 0xFF101820.toInt(), text = 0xFFFFFFFF.toInt(), accent = 0xFF2FA84F.toInt(),
            secondary = 0xFFFFD23F.toInt(), line = 0xFF05090C.toInt(),
        ),
        font = WidgetFont.SANS,
        frame = FrameStyle.STICKY,
        framePainter = PackAFrames.scoreboard,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            WidgetKind.BANNER to v(s(A.ball, Anchor.LEFT, 0.9f, 15f), s(A.cupTrophy, Anchor.RIGHT, 0.95f)),
            WidgetKind.CLOCK to v(s(A.whistle, Anchor.TOP_RIGHT, 0.65f, -10f), s(A.ball, Anchor.BOTTOM_LEFT, 0.6f)),
            WidgetKind.PHOTO to v(s(A.goal, Anchor.BOTTOM_RIGHT, 0.85f), s(A.cornerFlag, Anchor.TOP_LEFT, 0.6f)),
            WidgetKind.CALENDAR to v(s(A.scoreboard, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.COUNTDOWN to v(s(A.whistle, Anchor.BOTTOM_RIGHT, 0.6f, -10f)),
            WidgetKind.WEEK to v(s(A.whistle, Anchor.BOTTOM_RIGHT, 0.6f, -10f)),
            WidgetKind.BATTERY to v(s(A.cards, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.BATTERY_RING to v(s(A.cards, Anchor.BOTTOM_RIGHT, 0.6f)),
            WidgetKind.STEPS to v(s(A.ball, Anchor.BOTTOM_RIGHT, 0.55f, 20f)),
            WidgetKind.APPS to v(s(A.ball, Anchor.BOTTOM_RIGHT, 0.45f)),
        ),
        fallbackDecorations = listOf(
            listOf(s(A.ball, Anchor.BOTTOM_RIGHT, 0.55f)),
            listOf(s(A.cards, Anchor.BOTTOM_LEFT, 0.55f)),
        ),
        banner = "Matchday",
        bannerSub = "Every match matters",
        wallpaper = PackAWalls.pitch,
        widgets = listOf(
            SetWidget(WidgetKind.BANNER, 4, 1),
            SetWidget(WidgetKind.CLOCK, 2, 2),
            SetWidget(WidgetKind.PHOTO, 2, 2),
            SetWidget(WidgetKind.WEEK, 2, 1),
            SetWidget(WidgetKind.BATTERY, 2, 1),
            SetWidget(WidgetKind.CALENDAR, 2, 2),
            SetWidget(WidgetKind.STEPS, 2, 1),
            SetWidget(WidgetKind.APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFF2E8B3E.toInt(), 0xFF379C48.toInt()), angle = 90f,
            fgColor = 0xFFFFFFFF.toInt(), pattern = IconPattern.STRIPES, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.12f, shadow = 0.3f,
        ),
        clockLook = ClockLook.BIG,
        corner = 10f,
    )

    val themes: List<WidgetTheme> = listOf(
        BLOCKY, MATRIX, BLOCKY_SUMMER, BRICK_PARTY, TACTICAL, BATTLE_ROYALE, GAMER_RGB, SYNTHWAVE, CYBERPUNK, FOOTBALL,
    )
}
