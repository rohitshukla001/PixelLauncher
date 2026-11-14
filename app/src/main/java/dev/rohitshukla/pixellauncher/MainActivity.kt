package dev.rohitshukla.pixellauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.rohitshukla.pixellauncher.data.AppData
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.Cell
import dev.rohitshukla.pixellauncher.data.ClockAlignment
import dev.rohitshukla.pixellauncher.data.GridSize
import dev.rohitshukla.pixellauncher.data.HomeLayout
import dev.rohitshukla.pixellauncher.data.LauncherSettings
import dev.rohitshukla.pixellauncher.data.SearchEngine
import dev.rohitshukla.pixellauncher.search.SearchActivity
import dev.rohitshukla.pixellauncher.settings.AppLanguage
import dev.rohitshukla.pixellauncher.settings.SettingsActivity
import dev.rohitshukla.pixellauncher.wallpaper.WallpaperActivity
import dev.rohitshukla.pixellauncher.ui.LauncherController
import dev.rohitshukla.pixellauncher.ui.LauncherScreen
import dev.rohitshukla.pixellauncher.ui.theme.PixelLauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), LauncherController {

    private val appRepo by lazy { AppData.apps(this) }
    private val homeRepo by lazy { AppData.home(this) }
    private val icons by lazy { AppData.icons(this) }
    private val usageRepo by lazy { AppData.usage(this) }
    private val hiddenRepo by lazy { AppData.hidden(this) }
    private val settingsRepo by lazy { AppData.settings(this) }

    /** Counts Home presses that arrive while the launcher is already showing. */
    private var homePresses by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onRestart() {
        super.onRestart()
        if (!isDefaultHome()) askToBeDefaultHome()
    }

    override fun onResume() {
        super.onResume()
        // Before Android 13, a language picked in Settings reaches home when it next comes to the front.
        if (AppLanguage.isOutdated(this)) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockRotationOnPhones()
        // Light status and navigation bar icons over the wallpaper.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // First run: Yomo's "Choose wallpaper" screen comes before anything else.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                if (!settingsRepo.settings.first().setupComplete) {
                    startActivity(WallpaperActivity.intent(this@MainActivity, firstRun = true))
                } else if (!isDefaultHome()) {
                    askToBeDefaultHome()
                }
            }
        }
        setContent {
            val apps by appRepo.state.collectAsState()
            // Once the app list loads, drop launch counts for apps that are no longer installed.
            LaunchedEffect(apps.loaded, apps.apps) {
                if (apps.loaded) usageRepo.prune(apps.apps.mapTo(HashSet()) { it.key })
            }
            // Null until loaded: the grid depends on the search bar setting, so home waits for it.
            val settings by settingsRepo.settings.collectAsState<LauncherSettings, LauncherSettings?>(initial = null)
            val layout by homeRepo.layout.collectAsState(initial = HomeLayout())
            val launchCounts by usageRepo.launchCounts.collectAsState(initial = emptyMap())
            val hiddenPackages by hiddenRepo.hiddenPackages.collectAsState(initial = emptySet())
            PixelLauncherTheme {
                settings?.let {
                    LauncherScreen(
                        apps = apps,
                        layout = layout,
                        launchCounts = launchCounts,
                        hiddenPackages = hiddenPackages,
                        settings = it,
                        icons = icons,
                        homePresses = homePresses,
                        controller = this,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun launch(app: AppEntry, sourceBounds: Rect) {
        val bounds = sourceBounds.takeUnless { it.isEmpty }
        // Pixel Launcher's own icon opens its Settings, as Yomo's does.
        val opened = if (app.isSelf) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else {
            appRepo.launch(app, bounds)
        }
        if (opened) {
            lifecycleScope.launch { usageRepo.recordLaunch(app.key) }
        } else {
            toast(getString(R.string.cannot_open_app, app.label))
        }
    }

    override fun openAppInfo(app: AppEntry, sourceBounds: Rect) {
        if (!appRepo.openAppInfo(app, sourceBounds.takeUnless { it.isEmpty })) {
            toast(getString(R.string.cannot_open_app, app.label))
        }
    }

    override fun uninstall(app: AppEntry) {
        try {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                    .putExtra(Intent.EXTRA_USER, appRepo.userFor(app)),
            )
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.cannot_open_app, app.label))
        }
    }

    override fun placeShortcut(app: AppEntry, cell: Cell) {
        lifecycleScope.launch {
            if (!homeRepo.place(app.key, cell, installedKeys())) toast(getString(R.string.no_room_for_shortcut))
        }
    }

    override fun moveShortcut(app: AppEntry, from: Cell, to: Cell) {
        lifecycleScope.launch {
            if (!homeRepo.move(from, to, installedKeys())) toast(getString(R.string.no_room_for_shortcut))
        }
    }

    override fun removeShortcut(app: AppEntry, cell: Cell) {
        lifecycleScope.launch {
            homeRepo.remove(cell)
            toast(getString(R.string.removed_shortcut, app.label))
        }
    }

    override suspend fun addPage(): Boolean = homeRepo.addPage()

    override fun fitHome(grid: GridSize) {
        lifecycleScope.launch {
            homeRepo.fitTo(grid, installedKeys())
            // The Pixel Launcher icon, which opens Settings.
            homeRepo.seedSettingsShortcut("$packageName/${MainActivity::class.java.name}", grid)
        }
    }

    override fun openSearch(query: String?) {
        startActivity(SearchActivity.intent(this, query))
    }

    override fun setClockAlignment(alignment: ClockAlignment) {
        lifecycleScope.launch { settingsRepo.setClockAlignment(alignment) }
    }

    override fun setSearchEngine(engine: SearchEngine) {
        lifecycleScope.launch { settingsRepo.setSearchEngine(engine) }
    }

    /** Null until the app list has loaded, so no home shortcut is dropped as "uninstalled" by mistake. */
    private fun installedKeys(): Set<String>? =
        appRepo.state.value.takeIf { it.loaded }?.apps?.mapTo(HashSet()) { it.key }

    override fun openClock() {
        try {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.clock_app_not_found))
        }
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    /** As in Yomo: say so, open Android's Home app setting, and close. */
    private fun askToBeDefaultHome() {
        toast(getString(R.string.set_as_default_home))
        try {
            startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
        }
        finish()
    }

    /** As in Yomo: phones stay in their natural orientation; tablets (520 dp and wider) rotate. */
    private fun lockRotationOnPhones() {
        val tablet = resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
        requestedOrientation =
            if (tablet) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TABLET_MIN_WIDTH_DP = 520
    }
}
