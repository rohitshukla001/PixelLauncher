package dev.rohitshukla.pixellauncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.text.Collator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Installed launchable apps in every profile on the phone (personal and work), kept current as
 * apps are installed, updated or removed. As in Yomo, each package shows once, personal first.
 */
class AppRepository(context: Context, private val icons: IconCache) {

    data class State(val loaded: Boolean = false, val apps: List<AppEntry> = emptyList(), val dock: List<AppEntry> = emptyList())

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val me: UserHandle = Process.myUserHandle()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
    }

    init {
        // Lives as long as the process, like this repository, so it is never unregistered.
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** Reloads the app list and the dock. */
    fun refresh() {
        scope.launch {
            refreshLock.withLock {
                val collator = Collator.getInstance()
                val apps = launcherApps.profiles
                    .sortedBy { if (it == me) 0 else 1 }
                    .flatMap { user ->
                        val serial = userManager.getSerialNumberForUser(user)
                        launcherApps.getActivityList(null, user).map {
                            AppEntry(
                                label = it.label.toString(),
                                packageName = it.componentName.packageName,
                                className = it.componentName.className,
                                isSystem = it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                                isSelf = it.componentName.packageName == appContext.packageName,
                                profile = serial,
                            )
                        }
                    }
                    .distinctBy { it.packageName }
                    .sortedWith { a, b -> collator.compare(a.label, b.label) }
                val dock = DockApps.pick(DockApps.defaultPackages(appContext.packageManager), apps)
                _state.value = State(loaded = true, apps = apps, dock = dock)
            }
        }
    }

    /** Opens the app. Returns false when it can no longer be started (removed or disabled). */
    fun launch(app: AppEntry, sourceBounds: Rect?): Boolean = try {
        launcherApps.startMainActivity(app.component, userFor(app), sourceBounds, null)
        true
    } catch (e: SecurityException) {
        false
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }

    /** Opens Android's App info screen for the app. */
    fun openAppInfo(app: AppEntry, sourceBounds: Rect?): Boolean = try {
        launcherApps.startAppDetailsActivity(app.component, userFor(app), sourceBounds, null)
        true
    } catch (e: SecurityException) {
        false
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }

    /** The profile the app lives in; personal if that profile is gone. */
    fun userFor(app: AppEntry): UserHandle = userManager.getUserForSerialNumber(app.profile) ?: me

    private fun changed(packageName: String) {
        icons.invalidate(packageName)
        refresh()
    }
}
