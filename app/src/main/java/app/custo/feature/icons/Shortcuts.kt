package app.custo.feature.icons

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Home-screen shortcuts with custom icons: the no-root way to "change" an app icon. */
object Shortcuts {
    fun supported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun newId(pkg: String) = "custo_${pkg.hashCode().toUInt().toString(16)}_${System.currentTimeMillis().toString(36)}"

    private fun build(context: Context, id: String, pkg: String, label: String, icon: Bitmap, adaptive: Boolean): ShortcutInfoCompat {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: error("That app can't be opened from a shortcut")
        launch.action = Intent.ACTION_MAIN
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val ic = if (adaptive) IconCompat.createWithAdaptiveBitmap(icon) else IconCompat.createWithBitmap(icon)
        val name = label.ifBlank { pkg }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(name.take(24))
            .setLongLabel(name)
            .setIcon(ic)
            .setIntent(launch)
            .build()
    }

    /** Shows the launcher's "Add to home screen" prompt. Returns false if the launcher refused outright. */
    fun requestPin(context: Context, id: String, pkg: String, label: String, icon: Bitmap, adaptive: Boolean): Boolean {
        if (!supported(context)) error("Your launcher doesn't let apps add shortcuts")
        return ShortcutManagerCompat.requestPinShortcut(context, build(context, id, pkg, label, icon, adaptive), null)
    }

    /** Changes the icon/label of a shortcut already on the home screen, no prompt needed. */
    fun update(context: Context, id: String, pkg: String, label: String, icon: Bitmap, adaptive: Boolean): Boolean =
        ShortcutManagerCompat.updateShortcuts(context, listOf(build(context, id, pkg, label, icon, adaptive)))

    fun pinnedIds(context: Context): Set<String> = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).map { it.id }.toSet()
    }.getOrDefault(emptySet())

    /** Greys out a pinned shortcut. Only the user can remove it from the home screen. */
    fun disable(context: Context, id: String) = runCatching {
        ShortcutManagerCompat.disableShortcuts(context, listOf(id), "Removed in Custo")
    }
}
