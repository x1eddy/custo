package app.custo.feature.icons

/** A ready-made icon look for the themes gallery. All original art: shapes, colors and effects only. */
data class IconTheme(val id: String, val name: String, val style: IconStyle)

private fun c(v: Long) = v.toInt()

val ICON_THEMES: List<IconTheme> = listOf(
    IconTheme("pastel", "Pastel", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFFFFD6E8), c(0xFFD6E4FF)), 135f, fgColor = c(0xFF6B5B95), shadow = 0.12f,
    )),
    IconTheme("neon", "Neon", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFF0B0B1E), c(0xFF1A1A40)), fgColor = c(0xFF2DFFB3), glow = 0.7f, glowColor = c(0xFF2DFFB3), shadow = 0f,
        border = 0.12f, borderColor = c(0x992DFFB3),
    )),
    IconTheme("glass", "Glass", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0x77FFFFFF), c(0x33FFFFFF)), 120f, fgColor = c(0xFFFFFFFF), shadow = 0.25f,
        gloss = 0.6f, border = 0.1f, borderColor = c(0xAAFFFFFF),
    )),
    IconTheme("paper", "Paper", IconStyle(
        IconShape.ROUNDED, listOf(c(0xFFF4EEDF)), fgColor = c(0xFF3A3A3A), shadow = 0.25f, shadowDistance = 0.5f,
        texture = IconTexture.PAPER, textureOpacity = 0.6f, corner = 0.18f,
    )),
    IconTheme("carbon", "Carbon gold", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFF1A1A1A), c(0xFF2E2E2E)), 90f, fgColor = c(0xFFE9C46A), shadow = 0.35f,
        texture = IconTexture.CARBON, textureOpacity = 0.55f, border = 0.1f, borderColor = c(0xFFE9C46A), bevel = 0.35f,
    )),
    IconTheme("stickers", "Stickers", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFFFFFFFF)), fg = FgMode.APP_ORIGINAL, fgScale = 0.7f, shadow = 0.25f,
        sticker = Sticker.HEART, stickerColor = c(0xFFFF4D8D),
    )),
    IconTheme("mono", "Monochrome", IconStyle(IconShape.CIRCLE, listOf(c(0xFF111111)), fgColor = c(0xFFFFFFFF), shadow = 0f)),
    IconTheme("pixel", "Retro pixel", IconStyle(
        IconShape.ROUNDED, listOf(c(0xFF2B1055), c(0xFF7597DE)), 90f, pattern = IconPattern.GRID, patternColor = c(0xFFFFFFFF),
        patternSize = 0.15f, patternOpacity = 0.14f, fgColor = c(0xFFFFE66D), shadow = 0.9f, shadowDistance = 0.6f, shadowBlur = 0f,
        shadowAngle = 45f, corner = 0.06f,
    )),
    IconTheme("candy", "Candy", IconStyle(
        IconShape.BLOB, listOf(c(0xFFFF9A8B), c(0xFFFF6A88), c(0xFFFF99AC)), fgColor = c(0xFFFFFFFF), bgKind = BgKind.RADIAL,
        gloss = 0.35f, seed = 11,
    )),
    IconTheme("outline", "Outline", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFFFAFAFA)), fg = FgMode.OUTLINE, fgColor = c(0xFF222222), shadow = 0f,
        border = 0.06f, borderColor = c(0xFF222222),
    )),
    IconTheme("luxe", "Dark luxe", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFF0F0F12), c(0xFF26222F)), 160f, fgColor = c(0xFFD4AF37), shadow = 0.4f,
        bevel = 0.3f, border = 0.05f, borderColor = c(0x99D4AF37),
    )),
    IconTheme("hearts", "Sweetheart", IconStyle(
        IconShape.HEART, listOf(c(0xFFFF7EB3), c(0xFFFF758C)), 90f, fgColor = c(0xFFFFFFFF), fgScale = 0.46f, shadow = 0.2f, gloss = 0.3f,
    )),
    IconTheme("stars", "Starlight", IconStyle(
        IconShape.STAR, listOf(c(0xFF2E3192), c(0xFF1BFFFF)), 135f, fgColor = c(0xFFFFFFFF), fgScale = 0.4f, shadow = 0.2f,
    )),
    IconTheme("soft", "Soft UI", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFFE6E9EF)), fgColor = c(0xFF8A93A6), shadow = 0f, neumorph = 0.8f,
    )),
    IconTheme("duotone", "Duotone", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFF2D1B3D)), fg = FgMode.DUOTONE, fgColor = c(0xFF6A2C70), fgColor2 = c(0xFFFFC371),
        fgScale = 0.8f, shadow = 0.2f,
    )),
    IconTheme("ocean", "Ocean drop", IconStyle(
        IconShape.DROP, listOf(c(0xFF43C6AC), c(0xFF191654)), 90f, fgColor = c(0xFFFFFFFF), fgScale = 0.46f, fgY = 0.06f, gloss = 0.25f,
    )),
    IconTheme("leaf", "Leafy", IconStyle(
        IconShape.LEAF, listOf(c(0xFFA8E063), c(0xFF56AB2F)), 120f, fgColor = c(0xFFFFFFFF), texture = IconTexture.NOISE, textureOpacity = 0.18f,
    )),
    IconTheme("shield", "Guardian", IconStyle(
        IconShape.SHIELD, listOf(c(0xFF3A6073), c(0xFF16222A)), 90f, fgColor = c(0xFFE0E6EA), fgScale = 0.48f, bevel = 0.5f,
    )),
    IconTheme("rainbow", "Rainbow", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFFFF6B6B), c(0xFFFFA36B)), 135f, fgColor = c(0xFFFFFFFF), rainbow = true, seed = 3,
    )),
    IconTheme("glitter", "Glitter", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFF8E2DE2), c(0xFF4A00E0)), 135f, fgColor = c(0xFFFFFFFF),
        texture = IconTexture.GLITTER, textureOpacity = 0.85f,
    )),
    IconTheme("aurora", "Aurora", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFF00F5A0), c(0xFF00D9F5), c(0xFF7B61FF), c(0xFFFF61D8)), bgKind = BgKind.SWEEP,
        fgColor = c(0xFFFFFFFF), glow = 0.25f, glowColor = c(0xFFFFFFFF),
    )),
    IconTheme("peach", "Peach", IconStyle(
        IconShape.ROUNDED, listOf(c(0xFFFFF4E6)), fg = FgMode.SILHOUETTE, fgColor = c(0xFFF28482), shadow = 0.1f, corner = 0.3f,
    )),
    IconTheme("cafe", "Cafe", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFFC8A27A), c(0xFF8B5E3C)), 90f, fgColor = c(0xFFFFF4E6),
        texture = IconTexture.PAPER, textureOpacity = 0.45f, emboss = 0.4f,
    )),
    IconTheme("bubblegum", "Bubblegum", IconStyle(
        IconShape.BLOB, listOf(c(0xFFFFC8DD)), fg = FgMode.OUTLINE, fgColor = c(0xFFFF006E), shadow = 0.15f,
        border = 0.12f, borderColor = c(0xFFFFFFFF), borderDashed = true, seed = 5,
    )),
    IconTheme("arcade", "Arcade", IconStyle(
        IconShape.OCTAGON, listOf(c(0xFF120458), c(0xFF000000)), 90f, fgColor = c(0xFFFF00E4), glow = 0.6f, glowColor = c(0xFFFF00E4),
        shadow = 0f, border = 0.08f, borderColor = c(0xFF00F0FF),
    )),
    IconTheme("forest", "Forest", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFF2F4F3A), c(0xFF1E3326)), 90f, fgColor = c(0xFFD8E2DC), emboss = 0.6f, shadow = 0f,
    )),
    IconTheme("kawaii", "Kawaii", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFFFFE5EC), c(0xFFFFC2D1)), 90f, fgColor = c(0xFFFB6F92), shadow = 0.1f,
        sticker = Sticker.BOW, stickerColor = c(0xFFFB6F92), stickerCorner = StickerCorner.TOP_START,
    )),
    IconTheme("royal", "Royal", IconStyle(
        IconShape.ROUNDED, listOf(c(0xFFC94B4B), c(0xFF4B134F)), bgKind = BgKind.RADIAL, fgColor = c(0xFFFFD700),
        sticker = Sticker.CROWN, stickerColor = c(0xFFFFD700),
    )),
    IconTheme("cloud", "Cloud nine", IconStyle(
        IconShape.CLOUD, listOf(c(0xFFE0F7FF), c(0xFFB3E5FC)), 90f, fgColor = c(0xFF4A90C2), fgScale = 0.44f, fgY = 0.04f, shadow = 0.15f,
    )),
    IconTheme("polka", "Polka", IconStyle(
        IconShape.CIRCLE, listOf(c(0xFFFFD60A)), pattern = IconPattern.DOTS, patternColor = c(0xFFFFFFFF), patternOpacity = 0.45f,
        patternSize = 0.3f, fgColor = c(0xFF1D3557),
    )),
    IconTheme("blueprint", "Blueprint", IconStyle(
        IconShape.ROUNDED, listOf(c(0xFF1E4E8C)), pattern = IconPattern.GRID, patternColor = c(0xFFFFFFFF), patternOpacity = 0.15f,
        patternSize = 0.15f, fg = FgMode.OUTLINE, fgColor = c(0xFFFFFFFF), shadow = 0f, corner = 0.1f,
    )),
    IconTheme("sparkle", "Sparkle", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFF1B1B3A), c(0xFF693668)), 135f, fgColor = c(0xFFFFE8F4),
        sticker = Sticker.SPARKLE, stickerColor = c(0xFFFFD166), texture = IconTexture.GLITTER, textureOpacity = 0.4f,
    )),
    IconTheme("clean", "Clean", IconStyle(
        IconShape.SQUIRCLE, listOf(c(0xFFFFFFFF)), fg = FgMode.APP_ORIGINAL, fgScale = 0.78f, shadow = 0.25f,
    )),
)
