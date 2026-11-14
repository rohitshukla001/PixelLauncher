package dev.rohitshukla.pixellauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App icons as bitmaps, loaded off the main thread and kept in memory. */
class IconCache(context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val density = context.resources.displayMetrics.densityDpi
    private val sizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val cache = LruCache<String, ImageBitmap>(MAX_ICONS)

    suspend fun icon(app: AppEntry): ImageBitmap? = cache[app.key] ?: withContext(Dispatchers.IO) {
        val user = userManager.getUserForSerialNumber(app.profile) ?: Process.myUserHandle()
        val info = launcherApps.resolveActivity(Intent().setComponent(app.component), user)
        // Badged: work apps get Android's work badge; personal apps look unchanged.
        info?.getBadgedIcon(density)?.toBitmap(sizePx, sizePx)?.asImageBitmap()?.also { cache.put(app.key, it) }
    }

    /** Drops the icons of a package that was updated or removed. */
    fun invalidate(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(cache::remove)
    }

    companion object {
        const val ICON_SIZE_DP = 56
        private const val MAX_ICONS = 300
    }
}
