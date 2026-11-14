package dev.rohitshukla.pixellauncher.data

import android.content.ComponentName

/** One launchable activity. Plain values so the pure logic can be unit tested without Android. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    /** Preinstalled; it cannot be uninstalled, so the menu hides Uninstall (as in Yomo). */
    val isSystem: Boolean = false,
    /** Pixel Launcher itself: opens Settings and cannot be uninstalled from its own menu (as in Yomo). */
    val isSelf: Boolean = false,
    /** Which profile (personal or work) the app is in: Android's serial number for that user. */
    val profile: Long = 0,
) {
    /** Stable id, stored in the home layout. Same format as [ComponentName.flattenToString]. */
    val key: String get() = "$packageName/$className"

    val component: ComponentName get() = ComponentName(packageName, className)
}

/**
 * One drawer entry per package, as in Yomo, with the personal profile first. Within a package it
 * keeps the main icon ([isMain]), not just the first one listed, so a package with two icons
 * (Google and Voice Search) shows its main one; with no main icon, the first one.
 */
fun onePerPackage(entries: List<AppEntry>, isMain: (AppEntry) -> Boolean): List<AppEntry> =
    entries.groupBy { it.packageName }.values.map { sameApp ->
        val profile = sameApp.first().profile
        val candidates = sameApp.filter { it.profile == profile }
        candidates.firstOrNull(isMain) ?: candidates.first()
    }
