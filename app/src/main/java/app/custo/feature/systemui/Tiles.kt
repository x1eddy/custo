package app.custo.feature.systemui

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.custo.AppContainer
import app.custo.R
import app.custo.container
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What a tile shows. */
data class TileInfo(val active: Boolean, val subtitle: String? = null)

/**
 * Base for Custo's Quick Settings tiles. All of them change settings through Shizuku, so they show
 * as unavailable while it isn't ready, and as "Not on this phone" when the probe fails.
 */
abstract class CustoTile : TileService() {
    protected val c: AppContainer get() = applicationContext.container

    /** Current state, or null if this phone doesn't support it. */
    abstract suspend fun read(): TileInfo?
    abstract suspend fun toggle()

    override fun onStartListening() {
        c.appScope.launch { render() }
    }

    override fun onClick() {
        c.appScope.launch {
            if (ready()) runCatching { toggle() }
            render()
        }
    }

    private suspend fun ready(): Boolean {
        c.shizuku.refresh()
        return withTimeoutOrNull(2_000) { c.shizuku.state.first { it == ShizukuState.READY } } != null
    }

    private suspend fun render() {
        val (state, subtitle) = if (!ready()) {
            Tile.STATE_UNAVAILABLE to "Needs Shizuku"
        } else {
            val info = runCatching { read() }.getOrNull()
            when {
                info == null -> Tile.STATE_UNAVAILABLE to "Not on this phone"
                else -> (if (info.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE) to info.subtitle
            }
        }
        withContext(Dispatchers.Main) {
            runCatching {
                val t = qsTile ?: return@runCatching
                t.state = state
                t.subtitle = subtitle
                t.updateTile()
            }
        }
    }
}

/** Keeps the screen on by raising the screen timeout while active; the old timeout is restored on tap. */
class CaffeineTile : CustoTile() {
    override suspend fun read(): TileInfo {
        val on = c.tweaks.read(Namespace.SYSTEM, TIMEOUT)?.toIntOrNull() == FOREVER
        return TileInfo(on, if (on) "Screen stays on" else null)
    }

    override suspend fun toggle() {
        val current = c.tweaks.read(Namespace.SYSTEM, TIMEOUT)?.toIntOrNull()
        if (current == FOREVER) {
            val prev = c.prefs.get(SysKeys.caffeinePrevTimeout, DEFAULT_TIMEOUT)
            c.tweaks.write(Namespace.SYSTEM, TIMEOUT, prev.toString())
            c.prefs.edit { it.remove(SysKeys.caffeinePrevTimeout) }
        } else {
            c.prefs.set(SysKeys.caffeinePrevTimeout, current ?: DEFAULT_TIMEOUT)
            c.tweaks.write(Namespace.SYSTEM, TIMEOUT, FOREVER.toString())
        }
    }

    companion object {
        const val TIMEOUT = "screen_off_timeout"
        const val FOREVER = Int.MAX_VALUE
        const val DEFAULT_TIMEOUT = 60_000
    }
}

class ExtraDimTile : CustoTile() {
    override suspend fun read(): TileInfo? {
        val v = c.tweaks.read(EXTRA_DIM.ns, EXTRA_DIM.key)
        if (!Probes.check(EXTRA_DIM.probe, this, c.tweaks, v)) return null
        return TileInfo(EXTRA_DIM.isOn(v))
    }

    override suspend fun toggle() {
        c.tweaks.setToggle(EXTRA_DIM, !EXTRA_DIM.isOn(c.tweaks.read(EXTRA_DIM.ns, EXTRA_DIM.key)))
    }
}

class HeadsUpTile : CustoTile() {
    override suspend fun read() = TileInfo(HEADS_UP.isOn(c.tweaks.read(HEADS_UP.ns, HEADS_UP.key)))
    override suspend fun toggle() {
        c.tweaks.setToggle(HEADS_UP, !HEADS_UP.isOn(c.tweaks.read(HEADS_UP.ns, HEADS_UP.key)))
    }
}

class ScreenshotModeTile : CustoTile() {
    override suspend fun read() = TileInfo(c.prefs.get(SysKeys.demoOn, false))
    override suspend fun toggle() {
        val on = !c.prefs.get(SysKeys.demoOn, false)
        c.tweaks.setDemoMode(on)
        c.prefs.set(SysKeys.demoOn, on)
    }
}

class PrivateDnsTile : CustoTile() {
    private suspend fun mode() = PrivateDnsMode.of(c.tweaks.read(Namespace.GLOBAL, MODE))
    private suspend fun host() = c.tweaks.read(Namespace.GLOBAL, HOST)

    override suspend fun read(): TileInfo {
        val m = mode()
        return TileInfo(m != PrivateDnsMode.OFF, if (m == PrivateDnsMode.HOSTNAME) host() ?: m.label else m.label)
    }

    override suspend fun toggle() {
        c.tweaks.write(Namespace.GLOBAL, MODE, PrivateDnsMode.next(mode(), !host().isNullOrBlank()).id)
    }

    companion object {
        const val MODE = "private_dns_mode"
        const val HOST = "private_dns_specifier"
    }
}

class WirelessDebuggingTile : CustoTile() {
    override suspend fun read() = TileInfo(c.tweaks.read(Namespace.GLOBAL, KEY) == "1")
    override suspend fun toggle() {
        c.tweaks.write(Namespace.GLOBAL, KEY, if (c.tweaks.read(Namespace.GLOBAL, KEY) == "1") "0" else "1")
    }

    companion object { const val KEY = "adb_wifi_enabled" }
}

/** The tile pack, for the "Add to Quick Settings" screen. */
enum class CustoTileSpec(val cls: Class<out TileService>, @StringRes val label: Int, val desc: String, @DrawableRes val icon: Int) {
    CAFFEINE(CaffeineTile::class.java, R.string.tile_caffeine, "Keep the screen on", R.drawable.ic_tile_coffee),
    EXTRA_DIM_TILE(ExtraDimTile::class.java, R.string.tile_extra_dim, "Dim below the lowest brightness", R.drawable.ic_tile_dim),
    HEADS_UP_TILE(HeadsUpTile::class.java, R.string.tile_heads_up, "Pop-up notifications on or off", R.drawable.ic_tile_popup),
    SCREENSHOT(ScreenshotModeTile::class.java, R.string.tile_screenshot, "Clean status bar for screenshots", R.drawable.ic_tile_screenshot),
    PRIVATE_DNS(PrivateDnsTile::class.java, R.string.tile_private_dns, "Off, automatic or your own", R.drawable.ic_tile_dns),
    WIRELESS_DEBUGGING(WirelessDebuggingTile::class.java, R.string.tile_wireless_debugging, "Turning it off may stop Shizuku", R.drawable.ic_tile_adb),
}
