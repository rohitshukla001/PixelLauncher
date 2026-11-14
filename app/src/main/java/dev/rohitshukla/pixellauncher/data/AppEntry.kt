package dev.rohitshukla.pixellauncher.data

import android.content.ComponentName

/** One launchable activity. Plain values so the pure logic can be unit tested without Android. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    /** Preinstalled; it cannot be uninstalled, so the menu hides Uninstall (as in Yomo). */
    val isSystem: Boolean = false,
) {
    /** Stable id, stored in the home layout. Same format as [ComponentName.flattenToString]. */
    val key: String get() = "$packageName/$className"

    val component: ComponentName get() = ComponentName(packageName, className)
}
