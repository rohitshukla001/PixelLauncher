package dev.rohitshukla.pixellauncher.settings

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppData
import dev.rohitshukla.pixellauncher.data.LauncherSettings
import dev.rohitshukla.pixellauncher.wallpaper.WallpaperActivity
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.Pattern
import kotlinx.coroutines.launch

/** Pixel Launcher Settings, listed in the app drawer (Yomo has its own settings icon too). */
class SettingsActivity : ComponentActivity(), SettingsController {

    private val appRepo by lazy { AppData.apps(this) }
    private val hiddenRepo by lazy { AppData.hidden(this) }
    private val homeRepo by lazy { AppData.home(this) }
    private val icons by lazy { AppData.icons(this) }
    private val settingsRepo by lazy { AppData.settings(this) }

    /**
     * Held here, not saved: leaving Settings (or Android recreating it) always comes back to the
     * main screen, so the hidden-apps list needs the pattern again.
     */
    private var screen by mutableStateOf(SettingsScreen.Main)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by appRepo.state.collectAsState()
            val hasPattern by hiddenRepo.hasPattern.collectAsState<Boolean, Boolean?>(initial = null)
            val hidden by hiddenRepo.hiddenPackages.collectAsState(initial = emptySet())
            val settings by settingsRepo.settings.collectAsState(initial = LauncherSettings())
            // One row per package, without this launcher (as in Yomo).
            val apps = remember(state.apps) {
                state.apps.filter { it.packageName != packageName }.distinctBy { it.packageName }
            }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SettingsApp(
                    screen = screen,
                    onNavigate = { screen = it },
                    hasPattern = hasPattern,
                    apps = apps,
                    hidden = hidden,
                    settings = settings,
                    icons = icons,
                    controller = this,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        screen = SettingsScreen.Main
    }

    override suspend fun checkPattern(pattern: Pattern): Boolean = hiddenRepo.checkPattern(pattern)

    override suspend fun savePattern(pattern: Pattern) = hiddenRepo.setPattern(pattern)

    override fun setHidden(app: AppEntry, hidden: Boolean) {
        lifecycleScope.launch {
            hiddenRepo.setHidden(app.packageName, hidden)
            // As in Yomo, hiding removes the app's shortcuts from home; showing it again does not bring them back.
            if (hidden) homeRepo.removePackage(app.packageName)
        }
    }

    override fun launch(app: AppEntry) {
        if (!appRepo.launch(app, null)) toast(R.string.cannot_open_app, app.label)
    }

    override fun changeWallpaper() {
        startActivity(WallpaperActivity.intent(this, firstRun = false))
    }

    override fun setSearchBarEnabled(on: Boolean) {
        lifecycleScope.launch { settingsRepo.setSearchBarEnabled(on) }
    }

    override fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: Int, arg: String) {
        Toast.makeText(this, getString(message, arg), Toast.LENGTH_SHORT).show()
    }
}
