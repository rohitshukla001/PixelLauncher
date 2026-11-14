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
    /**
     * Stable id, stored in the home layout, usage counts and the icon cache. The personal profile
     * (serial 0) keeps the plain `package/class` form so existing saved layouts still match; other
     * profiles append their serial, so a work copy never collides with the personal one.
     */
    val key: String get() = if (profile == 0L) "$packageName/$className" else "$packageName/$className@$profile"

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
