package dev.rohitshukla.pixellauncher.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.homeStore by preferencesDataStore(name = "home")
private val Context.usageStore by preferencesDataStore(name = "usage")
private val Context.privacyStore by preferencesDataStore(name = "privacy")
private val Context.settingsStore by preferencesDataStore(name = "settings")

/** One instance of each repository per process. */
object AppData {
    @Volatile private var iconCache: IconCache? = null
    @Volatile private var appRepo: AppRepository? = null
    @Volatile private var homeRepo: HomeRepository? = null
    @Volatile private var usageRepo: UsageRepository? = null
    @Volatile private var hiddenRepo: HiddenAppsRepository? = null
    @Volatile private var settingsRepo: SettingsRepository? = null

    fun icons(context: Context): IconCache =
        iconCache ?: synchronized(this) {
            iconCache ?: IconCache(context.applicationContext).also { iconCache = it }
        }

    fun apps(context: Context): AppRepository =
        appRepo ?: synchronized(this) {
            appRepo ?: AppRepository(context.applicationContext, icons(context)).also { appRepo = it }
        }

    fun home(context: Context): HomeRepository =
        homeRepo ?: synchronized(this) {
            homeRepo ?: HomeRepository(context.applicationContext.homeStore).also { homeRepo = it }
        }

    fun usage(context: Context): UsageRepository =
        usageRepo ?: synchronized(this) {
            usageRepo ?: UsageRepository(context.applicationContext.usageStore).also { usageRepo = it }
        }

    fun hidden(context: Context): HiddenAppsRepository =
        hiddenRepo ?: synchronized(this) {
            hiddenRepo ?: HiddenAppsRepository(context.applicationContext.privacyStore).also { hiddenRepo = it }
        }

    fun settings(context: Context): SettingsRepository =
        settingsRepo ?: synchronized(this) {
            settingsRepo ?: SettingsRepository(context.applicationContext.settingsStore).also { settingsRepo = it }
        }
}
