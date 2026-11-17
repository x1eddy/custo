package app.custo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.data.Keys
import app.custo.feature.icons.ICON_THEMES
import app.custo.feature.icons.ThemeCard
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import app.custo.feature.themes.BUILT_IN_LOOKS
import app.custo.feature.themes.WallPreview
import app.custo.feature.wallhaven.BottomScrim
import app.custo.feature.wallhaven.Shimmer
import app.custo.feature.wallhaven.WallFormat
import app.custo.feature.wallhaven.WallPack
import app.custo.feature.wallhaven.WallPacks
import app.custo.feature.wallhaven.WallTile
import app.custo.feature.wallhaven.WhWallpaper
import app.custo.feature.widgetsets.SetPreviewImage
import app.custo.feature.widgetsets.WidgetThemes
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.enterAnimation
import app.custo.ui.components.rememberActionRunner
import kotlin.math.absoluteValue

/**
 * Home: a picture feed. Wallpapers up top (saved ones first, else wallhaven's favourites, else
 * Custo's own looks when offline), then rows of widget sets, wallpaper packs and icon themes, and a
 * few quick toggles. Every row is lazy and every picture comes from a cache or a background load.
 */
@Composable
fun HomeScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val state by c.shizuku.state.collectAsStateWithLifecycle()
    val prefs by c.prefs.data.collectAsStateWithLifecycle(emptyPreferences())
    val runner = rememberActionRunner()
    val ready = state == ShizukuState.READY
    // Quick toggles: remembered system values (instant), refreshed in the background; writes update them.
    val quick = app.custo.feature.systemui.rememberQuickSystem()

    ScreenScaffold(
        title = "Custo",
        leading = { app.custo.ui.components.CustoMark(Modifier.size(40.dp)) },
        actions = {
            IconButton(onClick = { nav.push(Route.AppSettings) }) { Icon(Icons.Rounded.Settings, "Settings") }
        },
    ) {
        item(key = "hero") { HeroCarousel(nav, Modifier.enterAnimation(0)) }

        item(key = "quick") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.enterAnimation(1)) {
                if (!ready) item {
                    UnlockPill {
                        if (state == ShizukuState.NO_PERMISSION) c.shizuku.requestPermission() else nav.push(Route.ShizukuSetup)
                    }
                }
                item { QuickToggle("Battery %", quick?.batteryPct, ready) { on -> runner.run { c.tweaks.setBatteryPercent(on) } } }
                item { QuickToggle("Clock seconds", quick?.clockSeconds, ready) { on -> runner.run { c.tweaks.setClockSeconds(on) } } }
                item {
                    QuickToggle("Snappy motion", quick?.snappy, ready) { on ->
                        val v = if (on) 0.5f else 1f
                        runner.run { c.tweaks.setAnimationScales(app.custo.feature.systemui.AnimationScales(v, v, v)) }
                    }
                }
                item { QuickToggle("Edge light", prefs[Keys.edgeEnabled] == true, true) { on -> runner.run { c.prefs.set(Keys.edgeEnabled, on) } } }
            }
        }

        item(key = "sets_h") { FeedHeader("Widget sets") { nav.push(Route.WidgetSets) } }
        item(key = "sets") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterAnimation(2)) {
                items(WidgetThemes.all, key = { it.id }) { t ->
                    Box(Modifier.width(112.dp).clip(MaterialTheme.shapes.large).clickable { nav.push(Route.WidgetSet(t.id)) }) {
                        SetPreviewImage(t, Modifier.fillMaxWidth(), corner = 16)
                    }
                }
            }
        }

        item(key = "packs_h") { FeedHeader("Wallpaper packs") { nav.push(Route.OfflinePacks) } }
        item(key = "packs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterAnimation(3)) {
                items(WallPacks.all, key = { it.id }) { p -> PackCover(p) { nav.push(Route.WallhavenBrowse(p.query)) } }
            }
        }

        item(key = "icons_h") { FeedHeader("Icon themes") { nav.push(Route.IconThemes) } }
        item(key = "icons") {
            val apps by produceState<List<LaunchableApp>>(emptyList()) { value = AppList.load(ctx) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterAnimation(4)) {
                items(ICON_THEMES, key = { it.id }) { t ->
                    ThemeCard(t.name, t.style, apps, Modifier.width(132.dp)) { nav.push(Route.IconThemes) }
                }
            }
        }
    }
}

// ---------- Hero ----------

/** One picture in the hero carousel. */
private sealed interface Hero {
    data class Saved(val id: String, val path: String) : Hero
    data class Online(val item: WhWallpaper) : Hero
    data class Look(val look: app.custo.feature.themes.Look) : Hero
}

/** Online hero pictures from the last visit, so coming back to Home doesn't flash placeholders. */
private var lastOnlineHero: List<WhWallpaper> = emptyList()

@Composable
private fun HeroCarousel(nav: Navigator, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.container.wallhaven
    val saved by repo.saved.collectAsStateWithLifecycle(null)
    val local = saved?.take(8)?.mapNotNull { s -> repo.savedFile(s.id)?.let { Hero.Saved(s.id, it.path) } }
    val needOnline = local != null && local.size < 4
    val online by produceState(lastOnlineHero, needOnline) {
        if (needOnline) value = runCatching { repo.topFor("", 8) }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) lastOnlineHero = it }
    }
    val heroes: List<Hero>? = when {
        local == null -> null
        local.size >= 4 -> local
        else -> (local + online.map { Hero.Online(it) }).ifEmpty { BUILT_IN_LOOKS.map { Hero.Look(it) } }
    }
    Box(modifier.fillMaxWidth().height(400.dp)) {
        if (heroes == null) {
            Shimmer(Modifier.fillMaxSize().padding(horizontal = 56.dp).clip(MaterialTheme.shapes.extraLarge))
            return@Box
        }
        val pager = rememberPagerState { heroes.size }
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = 56.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxSize(),
            key = { i -> heroes.getOrNull(i)?.let(::heroKey) ?: i },
        ) { page ->
            val h = heroes.getOrNull(page) ?: return@HorizontalPager
            val m = Modifier.fillMaxSize().graphicsLayer {
                // Read the scroll offset here (draw phase), not in composition, so swiping does not recompose.
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val s = 1f - 0.1f * off
                scaleX = s; scaleY = s
            }
            when (h) {
                is Hero.Saved -> WallTile(h.path, m, aspect = null, targetPx = 900, corner = 28, onClick = { nav.push(Route.WallpaperDetail(h.id)) })
                is Hero.Online -> WallTile(
                    h.item.gridThumb, m, aspect = null, targetPx = 900, corner = 28,
                    placeholder = h.item.colors.firstOrNull()?.let { WallFormat.color(it) }?.let { Color(it).copy(alpha = 0.55f) },
                    onClick = { nav.push(Route.WallpaperDetail(h.item.id)) },
                )
                is Hero.Look -> Box(m.clip(MaterialTheme.shapes.extraLarge).clickable { nav.push(Route.WallpaperMaker) }) {
                    WallPreview(h.look.wall, Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun heroKey(h: Hero): String = when (h) {
    is Hero.Saved -> "s" + h.id
    is Hero.Online -> "o" + h.item.id
    is Hero.Look -> "l" + h.look.name
}

// ---------- Rows ----------

/** Section name with an arrow; the whole line opens the full gallery. */
@Composable
private fun FeedHeader(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onClick).padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A pack's cover: its first saved picture, else wallhaven's top result for its search. */
@Composable
private fun PackCover(pack: WallPack, onClick: () -> Unit) {
    val repo = LocalContext.current.container.wallhaven
    val saved by repo.saved.collectAsStateWithLifecycle(emptyList())
    val local = saved.firstOrNull { it.packId == pack.id }?.let { repo.savedFile(it.id)?.path }
    val online by produceState<String?>(null, pack.id, local == null) {
        if (local == null) value = runCatching { repo.topFor(pack.query, 1).firstOrNull()?.gridThumb }.getOrNull()
    }
    WallTile(local ?: online, Modifier.width(124.dp), aspect = 3f / 4f, targetPx = 360, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(BottomScrim))
        Text(
            pack.title, Modifier.align(Alignment.BottomStart).padding(10.dp),
            style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- Small pieces ----------

@Composable
private fun UnlockPill(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(cs.errorContainer).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LockOpen, null, Modifier.size(16.dp), tint = cs.onErrorContainer)
        Spacer(Modifier.width(6.dp))
        Text("Unlock system UI", style = MaterialTheme.typography.labelLarge, color = cs.onErrorContainer)
    }
}

/** Pill toggle for Home; null value = still loading, disabled = needs Shizuku. */
@Composable
private fun QuickToggle(label: String, on: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val active = on == true
    val bg by androidx.compose.animation.animateColorAsState(if (active) cs.primary else cs.surfaceContainerHigh, label = "qt")
    val fg by androidx.compose.animation.animateColorAsState(if (active) cs.onPrimary else if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.4f), label = "qtf")
    Row(
        Modifier.clip(CircleShape).background(bg)
            .clickable(enabled = enabled && on != null) {
                haptics.performHapticFeedback(if (!active) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)
                onChange(!active)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) cs.onPrimary else fg.copy(alpha = 0.35f)))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
