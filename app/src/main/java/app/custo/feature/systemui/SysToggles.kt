package app.custo.feature.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.IconSwitchRow
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SupportBadge
import androidx.compose.ui.graphics.vector.ImageVector

/** How to tell whether a tweak does anything on this phone. */
sealed interface Probe {
    data object Always : Probe
    data class MinSdk(val sdk: Int) : Probe
    /** The setting already exists (has a value). */
    data object Present : Probe
    /** A framework `bool` config is true. */
    data class ResBool(val name: String) : Probe
    /** A framework `string` config is non-empty. */
    data class ResString(val name: String) : Probe
    /** A system property equals "true". */
    data class Prop(val name: String) : Probe
    data class AnyOf(val probes: List<Probe>) : Probe {
        constructor(vararg p: Probe) : this(p.toList())
    }
}

/** A boolean system setting shown as a switch. */
data class Toggle(
    val ns: Namespace,
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val on: String = "1",
    val off: String = "0",
    val default: Boolean = false,
    val probe: Probe = Probe.Always,
    val icon: ImageVector? = null,
) {
    val id: String get() = "${ns.id}:$key"
    fun isOn(value: String?): Boolean = SettingValues.matches(value, on, default)
}

data class ToggleState(val supported: Boolean, val on: Boolean)

object SettingValues {
    /** Compares a stored value with the "on" value; numbers compare numerically ("1" == "1.0"). */
    fun matches(value: String?, on: String, default: Boolean): Boolean {
        val v = value?.trim() ?: return default
        val a = v.toDoubleOrNull()
        val b = on.toDoubleOrNull()
        return if (a != null && b != null) a == b else v == on
    }
}

object Probes {
    @SuppressLint("DiscouragedApi")
    fun resBool(ctx: Context, name: String): Boolean {
        val id = ctx.resources.getIdentifier(name, "bool", "android")
        return id != 0 && runCatching { ctx.resources.getBoolean(id) }.getOrDefault(false)
    }

    @SuppressLint("DiscouragedApi")
    fun resString(ctx: Context, name: String): String? {
        val id = ctx.resources.getIdentifier(name, "string", "android")
        return if (id == 0) null else runCatching { ctx.resources.getString(id) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @SuppressLint("DiscouragedApi")
    fun resInt(ctx: Context, name: String): Int? {
        val id = ctx.resources.getIdentifier(name, "integer", "android")
        return if (id == 0) null else runCatching { ctx.resources.getInteger(id) }.getOrNull()
    }

    suspend fun check(probe: Probe, ctx: Context, tweaks: SystemTweaks, value: String?): Boolean = when (probe) {
        Probe.Always -> true
        is Probe.MinSdk -> Build.VERSION.SDK_INT >= probe.sdk
        Probe.Present -> value != null
        is Probe.ResBool -> resBool(ctx, probe.name)
        is Probe.ResString -> resString(ctx, probe.name) != null
        is Probe.Prop -> tweaks.probe(SysCmd.getprop(probe.name)).trim() == "true"
        is Probe.AnyOf -> probe.probes.any { check(it, ctx, tweaks, value) }
    }

    /** Cache keys of the leaf probes [probe] needs (resource and property lookups). */
    fun leaves(probe: Probe): Set<String> = when (probe) {
        Probe.Always, Probe.Present, is Probe.MinSdk -> emptySet()
        is Probe.ResBool -> setOf("res.bool:${probe.name}")
        is Probe.ResString -> setOf("res.string:${probe.name}")
        is Probe.Prop -> setOf("prop:${probe.name}")
        is Probe.AnyOf -> probe.probes.flatMapTo(LinkedHashSet()) { leaves(it) }
    }

    /** Like [check], from cached leaf results ([cached], keyed as in [leaves]) and the current [value]. */
    fun eval(probe: Probe, value: String?, cached: Map<String, String>, sdk: Int = Build.VERSION.SDK_INT): Boolean = when (probe) {
        Probe.Always -> true
        is Probe.MinSdk -> sdk >= probe.sdk
        Probe.Present -> value != null
        is Probe.AnyOf -> probe.probes.any { eval(it, value, cached, sdk) }
        else -> leaves(probe).all { cached[it] == "1" }
    }
}

@Composable
fun rememberShizukuReady(): Boolean {
    val state by LocalContext.current.container.shizuku.state.collectAsStateWithLifecycle()
    return state == ShizukuState.READY
}

fun LazyListScope.lockedItem(nav: Navigator) = item {
    SectionCard("Set up Shizuku", Icons.Rounded.Lock, onClick = { nav.push(Route.ShizukuSetup) })
}

@Composable
fun NotOnPhoneRow(title: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        SupportBadge("Not on this phone", false)
    }
}

/**
 * Switch rows for [toggles]. Options this phone doesn't have are left out and named once,
 * quietly, at the bottom, so cards aren't cluttered with dead rows.
 */
@Composable
fun ToggleRows(toggles: List<Toggle>, states: Map<String, ToggleState>, onChange: (Toggle, Boolean) -> Unit) {
    val known = toggles.mapNotNull { t -> states[t.id]?.let { t to it } }
    val (ok, missing) = known.partition { it.second.supported }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ok.forEach { (t, st) -> IconSwitchRow(t.icon, t.title, st.on) { onChange(t, it) } }
        if (missing.isNotEmpty()) {
            Text(
                "Not here: " + missing.joinToString(", ") { it.first.title.replaceFirstChar { c -> c.lowercase() } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

suspend fun SystemTweaks.setToggle(t: Toggle, on: Boolean) = write(t.ns, t.key, if (on) t.on else t.off)
