package dev.rohitshukla.pixellauncher.data

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore

/** The dock: the phone's default dialer, messages, browser and camera, in that order. */
object DockApps {

    private fun intents() = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    )

    /** Package of each default handler; null when there is none or the user has not picked one yet. */
    fun defaultPackages(pm: PackageManager): List<String?> =
        intents().map { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }

    /**
     * Maps each package to its launchable app. A package with no launchable app (for example the
     * system chooser, when no default is set) has no slot, and each app shows once.
     */
    fun pick(packages: List<String?>, apps: List<AppEntry>): List<AppEntry> =
        packages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }.distinct()
}
