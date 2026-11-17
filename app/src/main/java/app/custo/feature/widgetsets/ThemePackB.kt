package app.custo.feature.widgetsets

import app.custo.feature.icons.IconPattern
import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconStyle
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetKind.APPS
import app.custo.feature.widgets.WidgetKind.BANNER
import app.custo.feature.widgets.WidgetKind.BATTERY
import app.custo.feature.widgets.WidgetKind.BATTERY_RING
import app.custo.feature.widgets.WidgetKind.CALENDAR
import app.custo.feature.widgets.WidgetKind.CLOCK
import app.custo.feature.widgets.WidgetKind.COUNTDOWN
import app.custo.feature.widgets.WidgetKind.DATE
import app.custo.feature.widgets.WidgetKind.NOTE
import app.custo.feature.widgets.WidgetKind.PHOTO
import app.custo.feature.widgets.WidgetKind.QUOTE
import app.custo.feature.widgets.WidgetKind.STEPS
import app.custo.feature.widgets.WidgetStyle

/** Theme pack B. Its art lives in ThemePackBArt.kt and ThemePackBWalls.kt. */
object ThemePackB {
    private val A = PackBArt
    private val F = PackBFrames
    private val W = PackBWalls

    private fun v(vararg d: Decoration) = listOf(d.toList())
    private fun pal(bg: Long, text: Long, accent: Long, secondary: Long, line: Long) =
        ThemePalette(bg.toInt(), text.toInt(), accent.toInt(), secondary.toInt(), line.toInt())

    private val TL = Anchor.TOP_LEFT; private val TC = Anchor.TOP_CENTER; private val TR = Anchor.TOP_RIGHT
    private val L = Anchor.LEFT; private val C = Anchor.CENTER; private val R = Anchor.RIGHT
    private val BL = Anchor.BOTTOM_LEFT; private val BR = Anchor.BOTTOM_RIGHT

    /** The usual eight-piece layout: banner, clock + photo, calendar, three small info cards, apps. */
    private fun standard(countdown: SetWidget? = null) = listOfNotNull(
        SetWidget(BANNER, 4, 1),
        SetWidget(CLOCK, 2, 2),
        SetWidget(PHOTO, 2, 2),
        SetWidget(CALENDAR, 2, 2),
        countdown ?: SetWidget(STEPS, 2, 1),
        SetWidget(BATTERY, 2, 1),
        SetWidget(DATE, 2, 1),
        SetWidget(APPS, 2, 1, WidgetStyle(showLabels = false)),
    )

    // ---------- 1. Comic hero ----------

    val COMIC_HERO = WidgetTheme(
        id = "b_comic_hero",
        wallhavenQuery = "Invincible",
        name = "Comic Hero",
        category = ThemeCategory.HEROES,
        description = "Comic panels with thick ink, halftone dots, POW and BAM bursts.",
        palette = pal(0xFFFFF6CC, 0xFF15131A, 0xFFE8282B, 0xFF1E6FD9, 0xFF15131A),
        font = WidgetFont.SANS,
        frame = FrameStyle.CARTOON,
        framePainter = F.comic,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.pow, L, 1.05f, -12f), custom(A.comicBolt, R, 0.9f, 14f)),
            CLOCK to v(custom(A.bam, TR, 0.95f, 10f), custom(A.comicBolt, BL, 0.6f, -18f)),
            PHOTO to v(custom(A.mask, TL, 0.8f, -10f), custom(A.pow, BR, 0.85f, 8f)),
            CALENDAR to v(custom(A.zap, BR, 0.8f, 10f)),
            STEPS to v(custom(A.comicBolt, BR, 0.6f, 14f)),
            BATTERY to v(custom(A.comicBolt, BR, 0.62f, 16f)),
            BATTERY_RING to v(custom(A.comicBolt, BR, 0.62f, 16f)),
            DATE to v(custom(A.mask, BR, 0.6f, 8f)),
            APPS to v(custom(A.zap, BR, 0.6f, -8f)),
            COUNTDOWN to v(custom(A.bam, BR, 0.7f, 6f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.pow, BR, 0.65f, 10f)),
            listOf(custom(A.comicBolt, BL, 0.6f, -10f)),
        ),
        banner = "Hero mode",
        bannerSub = "Save the day!",
        wallpaper = W.comic,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFFFFD43B.toInt()), fgColor = 0xFF15131A.toInt(),
            pattern = IconPattern.DOTS, patternColor = 0xFFE8282B.toInt(), patternOpacity = 0.35f, shadow = 0.35f,
        ),
        clockLook = ClockLook.BIG,
        corner = 8f,
    )

    // ---------- 2. Pirate crew ----------

    val PIRATE_CREW = WidgetTheme(
        id = "b_pirate_crew",
        wallhavenQuery = "One Piece",
        name = "Pirate Crew",
        category = ThemeCategory.HEROES,
        description = "Wanted posters, treasure maps and a ship on a sunny sea.",
        palette = pal(0xFFF3E2B8, 0xFF4A2C17, 0xFFD7322E, 0xFFE7B847, 0xFF3B2414),
        font = WidgetFont.SERIF,
        frame = FrameStyle.STICKY,
        framePainter = F.poster,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.jolly, L, 1f, -8f), custom(A.strawHat, R, 1f, 10f)),
            CLOCK to v(custom(A.compass, BR, 0.75f, 12f)),
            PHOTO to v(custom(A.strawHat, TL, 0.85f, -14f), custom(A.ship, BR, 0.8f, 0f)),
            CALENDAR to v(custom(A.map, BR, 0.8f, -8f)),
            STEPS to v(custom(A.wave, BR, 0.65f)),
            BATTERY to v(custom(A.compass, BR, 0.6f, 15f)),
            BATTERY_RING to v(custom(A.compass, BR, 0.6f, 15f)),
            DATE to v(custom(A.jolly, BR, 0.6f, 8f)),
            APPS to v(custom(A.wave, BL, 0.6f)),
            NOTE to v(custom(A.map, BR, 0.7f, 10f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.compass, BR, 0.6f, 10f)),
            listOf(custom(A.wave, BL, 0.6f)),
        ),
        banner = "Set sail",
        bannerSub = "Adventure awaits",
        wallpaper = W.pirate,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFFF3E2B8.toInt(), 0xFFE2C68C.toInt()), angle = 160f, fgColor = 0xFF3B2414.toInt(),
            pattern = IconPattern.WAVES, patternColor = 0xFF3A8DDE.toInt(), patternOpacity = 0.25f, shadow = 0.25f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 3f,
    )

    // ---------- 3. Cursed energy ----------

    val CURSED_ENERGY = WidgetTheme(
        id = "b_cursed_energy",
        wallhavenQuery = "Jujutsu Kaisen",
        name = "Cursed Energy",
        category = ThemeCategory.HEROES,
        description = "Dark sorcery: violet energy swirls, paper talismans and brushstroke frames.",
        palette = pal(0xFF14122B, 0xFFEDE8FF, 0xFF7C5CFF, 0xFF3FB6FF, 0xFF0A0918),
        font = WidgetFont.SERIF,
        frame = FrameStyle.PLAIN,
        framePainter = F.brush,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.ofuda, L, 1.05f, -14f), custom(A.flame, R, 0.9f, 8f)),
            CLOCK to v(custom(A.eye, TR, 0.8f, 8f), custom(A.swirl, BL, 0.6f)),
            PHOTO to v(custom(A.ofuda, TR, 0.85f, 14f), custom(A.handSign, BL, 0.85f, -8f)),
            CALENDAR to v(custom(A.splash, BR, 0.75f)),
            STEPS to v(custom(A.flame, BR, 0.6f, 6f)),
            BATTERY to v(custom(A.swirl, BR, 0.6f)),
            BATTERY_RING to v(custom(A.swirl, BR, 0.6f)),
            DATE to v(custom(A.ofuda, BR, 0.65f, 12f)),
            APPS to v(custom(A.flame, BL, 0.55f, -6f)),
            QUOTE to v(custom(A.eye, BR, 0.6f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.flame, BR, 0.6f, 8f)),
            listOf(custom(A.ofuda, BL, 0.6f, -10f)),
        ),
        banner = "Domain open",
        bannerSub = "Focus your energy",
        wallpaper = W.cursed,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFF14122B.toInt(), 0xFF2E2266.toInt()), angle = 135f, fgColor = 0xFFEDE8FF.toInt(),
            glow = 0.45f, glowColor = 0xFF7C5CFF.toInt(), shadow = 0.3f,
        ),
        clockLook = ClockLook.BIG,
        textShadow = true,
        shadowColor = 0xAA7C5CFF.toInt(),
        corner = 14f,
    )

    // ---------- 4. Rock ----------

    val ROCK = WidgetTheme(
        id = "b_rock",
        name = "Rock",
        category = ThemeCategory.MUSIC,
        description = "Black leather with silver studs, red lightning and a flower skull.",
        palette = pal(0xFF141414, 0xFFF2F2F2, 0xFFD7192A, 0xFFC9CED6, 0xFF0A0A0A),
        font = WidgetFont.MONO,
        frame = FrameStyle.PLAIN,
        framePainter = F.leather,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.guitar, L, 1.1f), custom(A.rockBolt, R, 0.85f, 14f)),
            CLOCK to v(custom(A.skull, TR, 0.85f, 10f), custom(A.rockBolt, BL, 0.55f, -16f)),
            PHOTO to v(custom(A.guitar, BR, 1f), custom(A.studHeart, TL, 0.6f, -12f)),
            CALENDAR to v(custom(A.amp, BR, 0.75f, 6f)),
            STEPS to v(custom(A.rockBolt, BR, 0.6f, 14f)),
            BATTERY to v(custom(A.pick, BR, 0.6f, 18f)),
            BATTERY_RING to v(custom(A.pick, BR, 0.6f, 18f)),
            DATE to v(custom(A.skull, BR, 0.6f, -8f)),
            APPS to v(custom(A.studHeart, BL, 0.5f, -10f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.rockBolt, BR, 0.6f, 12f)),
            listOf(custom(A.pick, BL, 0.55f, -14f)),
        ),
        banner = "Turn it up",
        bannerSub = "Loud and proud",
        wallpaper = W.rock,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.ROUNDED, bg = listOf(0xFF1E1E20.toInt(), 0xFF0A0A0A.toInt()), angle = 90f, fgColor = 0xFFE0242F.toInt(),
            pattern = IconPattern.GRID, patternColor = 0xFFC9CED6.toInt(), patternOpacity = 0.1f, shadow = 0.4f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 12f,
    )

    // ---------- 5. Music ----------

    val MUSIC = WidgetTheme(
        id = "b_music",
        name = "Music",
        category = ThemeCategory.MUSIC,
        description = "Vinyl, headphones and cassettes on soft pastel record sleeves.",
        palette = pal(0xFFFFF8F3, 0xFF3D2C5E, 0xFFFF8FB1, 0xFF9FD8F5, 0xFF3D2C5E),
        font = WidgetFont.SANS,
        frame = FrameStyle.PLAIN,
        framePainter = F.sleeve,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.headphones, L, 1f, -8f), custom(A.notes, R, 0.85f, 10f)),
            CLOCK to v(custom(A.vinyl, TR, 0.85f), custom(A.notes, BL, 0.55f, -10f)),
            PHOTO to v(custom(A.headphones, TL, 0.85f, -12f), custom(A.cassette, BR, 0.8f, 8f)),
            CALENDAR to v(custom(A.eq, BR, 0.7f)),
            STEPS to v(custom(A.notes, BR, 0.6f, 8f)),
            BATTERY to v(custom(A.eq, BR, 0.55f)),
            BATTERY_RING to v(custom(A.eq, BR, 0.55f)),
            DATE to v(custom(A.cassette, BR, 0.62f, -8f)),
            APPS to v(Decoration(Sticker.SPARKLE, BL, 0.5f)),
            WidgetKind.MEDIA to v(custom(A.vinyl, BR, 0.7f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.notes, BR, 0.6f, 8f)),
            listOf(custom(A.vinyl, BL, 0.55f)),
        ),
        banner = "Now playing",
        bannerSub = "Your favourite song",
        wallpaper = W.music,
        widgets = listOf(
            SetWidget(BANNER, 4, 1),
            SetWidget(CLOCK, 2, 2),
            SetWidget(PHOTO, 2, 2),
            SetWidget(WidgetKind.MEDIA, 4, 1),
            SetWidget(CALENDAR, 2, 2),
            SetWidget(BATTERY_RING, 2, 2),
            SetWidget(DATE, 2, 1),
            SetWidget(APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFFFFD6E2.toInt(), 0xFFCBE9FF.toInt()), angle = 135f, fgColor = 0xFF3D2C5E.toInt(),
            pattern = IconPattern.WAVES, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.35f, shadow = 0.15f,
        ),
        clockLook = ClockLook.BIG,
        corner = 20f,
    )

    // ---------- 6. Galaxy ----------

    val GALAXY = WidgetTheme(
        id = "b_galaxy",
        wallhavenQuery = "space",
        name = "Galaxy",
        category = ThemeCategory.FUN,
        description = "Deep space: planets, rockets, a little astronaut and a glowing nebula.",
        palette = pal(0xFF1B1840, 0xFFFFFFFF, 0xFFFFC94D, 0xFF7FE0FF, 0xFF0E0C24),
        font = WidgetFont.MONO,
        frame = FrameStyle.PLAIN,
        framePainter = F.starry,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.planet, L, 1.1f), custom(A.rocket, R, 0.95f, 30f)),
            CLOCK to v(custom(A.moon, TR, 0.7f, -15f), custom(A.twinkle, BL, 0.45f)),
            PHOTO to v(custom(A.astronaut, BR, 1f, 12f), custom(A.twinkle, TL, 0.45f)),
            CALENDAR to v(custom(A.ufo, BR, 0.7f, -8f)),
            STEPS to v(custom(A.rocket, BR, 0.62f, 35f)),
            BATTERY to v(custom(A.rocket, BR, 0.62f, 35f)),
            BATTERY_RING to v(custom(A.rocket, BR, 0.62f, 35f)),
            DATE to v(custom(A.planet, BR, 0.65f)),
            APPS to v(custom(A.twinkle, BL, 0.45f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.twinkle, BR, 0.5f)),
            listOf(custom(A.moon, BL, 0.55f, -10f)),
        ),
        banner = "To the stars",
        bannerSub = "Keep exploring",
        wallpaper = W.galaxy,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFF2A1F5C.toInt(), 0xFF0E0C24.toInt()), angle = 135f, fgColor = 0xFFFFFFFF.toInt(),
            glow = 0.35f, glowColor = 0xFF7FE0FF.toInt(), shadow = 0.3f,
        ),
        clockLook = ClockLook.MINIMAL,
        textShadow = true,
        shadowColor = 0x887FE0FF.toInt(),
        corner = 20f,
    )

    // ---------- 7. Halloween ----------

    val HALLOWEEN = WidgetTheme(
        id = "b_halloween",
        wallhavenQuery = "halloween",
        name = "Halloween",
        category = ThemeCategory.SEASONS,
        description = "Spooky-cute: pumpkins, ghosts, bats and candy under a big moon.",
        palette = pal(0xFF2B1B3D, 0xFFFFE9D1, 0xFFFF8A1F, 0xFF8E5BD8, 0xFF1A0F26),
        font = WidgetFont.CURSIVE,
        frame = FrameStyle.DRIP,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.pumpkin, L, 1.05f, -8f), custom(A.bat, R, 0.95f, 10f)),
            CLOCK to v(custom(A.ghost, BR, 0.8f, 8f)),
            PHOTO to v(custom(A.ghost, C, 1.5f), custom(A.candyCorn, BR, 0.6f, -14f)),
            COUNTDOWN to v(custom(A.pumpkin, BR, 0.7f, 6f)),
            CALENDAR to v(custom(A.witchHat, BR, 0.75f, 12f)),
            STEPS to v(custom(A.bat, BR, 0.6f, -8f)),
            BATTERY to v(Decoration(Sticker.LOLLIPOP, BR, 0.7f, 18f)),
            BATTERY_RING to v(Decoration(Sticker.LOLLIPOP, BR, 0.7f, 18f)),
            DATE to v(custom(A.spookyMoon, BR, 0.6f, -12f)),
            APPS to v(custom(A.candyCorn, BL, 0.5f, -10f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.bat, BR, 0.6f, 8f)),
            listOf(custom(A.pumpkin, BL, 0.6f, -6f)),
        ),
        banner = "Happy Halloween",
        bannerSub = "Trick or treat!",
        wallpaper = W.halloween,
        widgets = listOf(
            SetWidget(BANNER, 4, 1),
            SetWidget(CLOCK, 2, 2),
            SetWidget(PHOTO, 2, 2),
            SetWidget(CALENDAR, 2, 2),
            SetWidget(COUNTDOWN, 2, 1, WidgetStyle(countdownTitle = "Halloween"), countdownTo = 10 to 31),
            SetWidget(BATTERY, 2, 1),
            SetWidget(DATE, 2, 1),
            SetWidget(APPS, 2, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.SQUIRCLE, bg = listOf(0xFF4B2A6E.toInt(), 0xFF2B1B3D.toInt()), angle = 160f, fgColor = 0xFFFF8A1F.toInt(),
            pattern = IconPattern.CONFETTI, patternColor = 0xFFFF8A1F.toInt(), patternOpacity = 0.2f, shadow = 0.3f,
        ),
        clockLook = ClockLook.BIG,
        corner = 22f,
    )

    // ---------- 8. Dinosaurs ----------

    val DINOSAURS = WidgetTheme(
        id = "b_dinosaurs",
        name = "Dinosaurs",
        category = ThemeCategory.CUTE,
        description = "Cute dinos, speckled eggs, big leaves and a friendly volcano.",
        palette = pal(0xFFF4FCEA, 0xFF2F5130, 0xFFFF9F43, 0xFF8FD26A, 0xFF2F3B2A),
        font = WidgetFont.SANS,
        frame = FrameStyle.CARTOON,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.longNeck, L, 1.1f), custom(A.egg, R, 0.85f, 10f)),
            CLOCK to v(custom(A.leaf, TR, 0.8f, 30f), custom(A.footprint, BL, 0.5f, -20f)),
            PHOTO to v(custom(A.tinyRex, BR, 1.2f), custom(A.leaf, TL, 0.7f, -40f)),
            CALENDAR to v(custom(A.volcano, BR, 0.8f)),
            STEPS to v(custom(A.footprint, BR, 0.6f, 20f)),
            BATTERY to v(custom(A.egg, BR, 0.6f, 12f)),
            BATTERY_RING to v(custom(A.egg, BR, 0.6f, 12f)),
            DATE to v(custom(A.leaf, BR, 0.62f, 20f)),
            APPS to v(custom(A.footprint, BL, 0.5f, -15f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.egg, BR, 0.6f, 10f)),
            listOf(custom(A.leaf, BL, 0.6f, -20f)),
        ),
        banner = "Roar!",
        bannerSub = "Have a dino-mite day",
        wallpaper = W.dinos,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.FLOWER, bg = listOf(0xFF8FD26A.toInt()), fgColor = 0xFF2F3B2A.toInt(),
            pattern = IconPattern.DOTS, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.3f, shadow = 0.2f,
        ),
        clockLook = ClockLook.BIG,
        corner = 26f,
    )

    // ---------- 9. Ocean ----------

    val OCEAN = WidgetTheme(
        id = "b_ocean",
        wallhavenQuery = "ocean",
        name = "Ocean",
        category = ThemeCategory.NATURE,
        description = "Under the sea: a happy whale, jellyfish, coral and rising bubbles.",
        palette = pal(0xFFEAFAF8, 0xFF0D4A5A, 0xFFFF8C7A, 0xFF4FC3D0, 0xFF0D3B4A),
        font = WidgetFont.SANS,
        frame = FrameStyle.PLAIN,
        framePainter = F.tide,
        ink = InkStyle.COLOR,
        decorations = mapOf(
            BANNER to v(custom(A.whale, L, 1.15f), custom(A.bubbles, R, 0.8f)),
            CLOCK to v(custom(A.jelly, TR, 0.8f, 8f)),
            PHOTO to v(custom(A.fish, TL, 0.75f, -8f), custom(A.starfish, BR, 0.75f, 14f)),
            CALENDAR to v(custom(A.coral, BR, 0.75f)),
            STEPS to v(custom(A.fish, BR, 0.6f, 6f)),
            BATTERY to v(custom(A.shell, BR, 0.6f, -10f)),
            BATTERY_RING to v(custom(A.shell, BR, 0.6f, -10f)),
            DATE to v(custom(A.starfish, BR, 0.6f, 12f)),
            APPS to v(custom(A.bubbles, BL, 0.5f)),
        ),
        fallbackDecorations = listOf(
            listOf(custom(A.bubbles, BR, 0.55f)),
            listOf(custom(A.shell, BL, 0.55f, -10f)),
        ),
        banner = "Deep blue",
        bannerSub = "Go with the flow",
        wallpaper = W.ocean,
        widgets = standard(),
        icons = IconStyle(
            shape = IconShape.TEARDROP, bg = listOf(0xFF8FE3DE.toInt(), 0xFF2E9CC0.toInt()), angle = 180f, fgColor = 0xFFFFFFFF.toInt(),
            pattern = IconPattern.WAVES, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.2f, shadow = 0.2f,
        ),
        clockLook = ClockLook.STACKED,
        corner = 22f,
    )

    // ---------- 10. Mono ----------

    val MONO = WidgetTheme(
        id = "b_mono",
        name = "Mono",
        category = ThemeCategory.MINIMAL,
        description = "Black and white, hairlines and whitespace, one tiny red dot.",
        palette = pal(0xFFFFFFFF, 0xFF111111, 0xFFE5484D, 0xFFF2F2F2, 0xFF111111),
        font = WidgetFont.SERIF,
        frame = FrameStyle.PLAIN,
        framePainter = F.hairline,
        ink = InkStyle.LINE,
        decorations = mapOf(
            BANNER to v(custom(A.monoArch, R, 0.55f)),
            CLOCK to v(custom(A.monoCircle, BR, 0.45f)),
            PHOTO to v(custom(A.monoSprig, BR, 0.55f, 8f)),
            CALENDAR to v(custom(A.monoStar, BR, 0.4f)),
            QUOTE to v(custom(A.monoWave, BR, 0.45f)),
        ),
        fallbackDecorations = listOf(emptyList(), listOf(custom(A.monoDot, BR, 0.18f))),
        banner = "Less, but better",
        bannerSub = "",
        wallpaper = W.mono,
        widgets = listOf(
            SetWidget(CLOCK, 4, 2),
            SetWidget(BANNER, 4, 1),
            SetWidget(CALENDAR, 2, 2),
            SetWidget(PHOTO, 2, 2),
            SetWidget(DATE, 2, 1),
            SetWidget(BATTERY, 2, 1),
            SetWidget(QUOTE, 4, 1),
            SetWidget(APPS, 4, 1, WidgetStyle(showLabels = false)),
        ),
        icons = IconStyle(
            shape = IconShape.CIRCLE, bg = listOf(0xFFFFFFFF.toInt()), fgColor = 0xFF111111.toInt(), shadow = 0.05f,
        ),
        clockLook = ClockLook.MINIMAL,
        textScale = 0.85f,
        corner = 2f,
    )

    val themes: List<WidgetTheme> = listOf(
        COMIC_HERO, PIRATE_CREW, CURSED_ENERGY, ROCK, MUSIC, GALAXY, HALLOWEEN, DINOSAURS, OCEAN, MONO,
    )
}
