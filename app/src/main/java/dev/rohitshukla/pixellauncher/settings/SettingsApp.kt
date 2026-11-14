package dev.rohitshukla.pixellauncher.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.semantics.Role
import dev.rohitshukla.pixellauncher.data.LauncherSettings
import dev.rohitshukla.pixellauncher.ui.ChoiceDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.IconCache
import dev.rohitshukla.pixellauncher.data.Pattern
import dev.rohitshukla.pixellauncher.data.PatternSetup

/** Everything the settings screens ask for. SettingsActivity implements it. */
interface SettingsController {
    suspend fun checkPattern(pattern: Pattern): Boolean
    suspend fun savePattern(pattern: Pattern)
    fun setHidden(app: AppEntry, hidden: Boolean)
    fun launch(app: AppEntry)
    fun toast(message: Int)
    fun changeWallpaper()
    fun addPage()
    fun removePage()
    fun setSearchBarEnabled(on: Boolean)
    fun setSuggestionsEnabled(on: Boolean)
    fun setLanguage(option: AppLanguage.Option)
}

enum class SettingsScreen { Main, Unlock, HiddenApps, SetPattern }

/** A pattern attempt. Not a data class, so the same pattern drawn twice still counts as a new attempt. */
private class Attempt(val pattern: Pattern)

/**
 * Settings. For now only Hide Apps, which follows Yomo: set a privacy pattern the first time,
 * unlock with it afterwards, then switch apps hidden or visible.
 */
@Composable
fun SettingsApp(
    screen: SettingsScreen,
    onNavigate: (SettingsScreen) -> Unit,
    /** Null until it has loaded; Hide Apps does nothing until then, so it cannot skip the unlock. */
    hasPattern: Boolean?,
    apps: List<AppEntry>,
    hidden: Set<String>,
    settings: LauncherSettings,
    language: AppLanguage.Option?,
    icons: IconCache,
    controller: SettingsController,
    onExit: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (screen) {
                SettingsScreen.Main -> MainScreen(
                    settings = settings,
                    language = language,
                    controller = controller,
                    onHideApps = {
                        when (hasPattern) {
                            true -> onNavigate(SettingsScreen.Unlock)
                            false -> onNavigate(SettingsScreen.HiddenApps)
                            null -> Unit
                        }
                    },
                    onBack = onExit,
                )
                SettingsScreen.Unlock -> UnlockScreen(
                    controller = controller,
                    onUnlocked = { onNavigate(SettingsScreen.HiddenApps) },
                    onBack = {
                        controller.toast(R.string.wrong_password)
                        onNavigate(SettingsScreen.Main)
                    },
                )
                SettingsScreen.HiddenApps -> HiddenAppsScreen(
                    hasPattern = hasPattern == true,
                    apps = apps,
                    hidden = hidden,
                    icons = icons,
                    controller = controller,
                    onSetPattern = { onNavigate(SettingsScreen.SetPattern) },
                    onBack = { onNavigate(SettingsScreen.Main) },
                )
                SettingsScreen.SetPattern -> SetPatternScreen(
                    controller = controller,
                    onDone = { firstTime ->
                        if (firstTime) controller.toast(R.string.launch_hidden_apps_from_here)
                        onNavigate(SettingsScreen.HiddenApps)
                    },
                    firstTime = hasPattern != true,
                    onBack = {
                        controller.toast(R.string.failed_to_set_privacy_password)
                        onNavigate(SettingsScreen.HiddenApps)
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SettingsRow(text: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        leading()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Yomo's settings rows, in its order, without its Premium, sharing, social and sign-in rows. */
@Composable
private fun MainScreen(
    settings: LauncherSettings,
    language: AppLanguage.Option?,
    controller: SettingsController,
    onHideApps: () -> Unit,
    onBack: () -> Unit,
) {
    var choosingLanguage by remember { mutableStateOf(false) }
    Header(stringResource(R.string.settings_name), onBack)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SettingsRow(stringResource(R.string.change_wallpaper), controller::changeWallpaper) { Icon(Icons.Outlined.Home, contentDescription = null) }
        SettingsRow(stringResource(R.string.add_shortcuts_page), controller::addPage) { Icon(Icons.Outlined.AddCircle, contentDescription = null) }
        SettingsRow(stringResource(R.string.remove_shortcuts_page), controller::removePage) { Icon(Icons.Outlined.Delete, contentDescription = null) }
        SettingsRow(stringResource(R.string.hide_apps), onHideApps) { Icon(Icons.Outlined.Lock, contentDescription = null) }
        SwitchRow(stringResource(R.string.enable_search_bar), settings.searchBarEnabled, controller::setSearchBarEnabled) {
            Icon(Icons.Outlined.Search, contentDescription = null)
        }
        SwitchRow(stringResource(R.string.enable_app_suggestions), settings.suggestionsEnabled, controller::setSuggestionsEnabled) {
            Icon(Icons.Outlined.Star, contentDescription = null)
        }
        SettingsRow(stringResource(R.string.change_language), { choosingLanguage = true }) { Icon(Icons.Outlined.Edit, contentDescription = null) }
        AppVersion()
    }
    if (choosingLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.choose_language),
            options = AppLanguage.Option.entries,
            selected = language,
            label = { it.nativeName },
            onPick = {
                choosingLanguage = false
                controller.setLanguage(it)
            },
            onDismiss = { choosingLanguage = false },
        )
    }
}

/** Which build is installed, so a tester can confirm they have the latest one. */
@Composable
private fun AppVersion() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    Text(
        text = stringResource(R.string.version, version),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        leading()
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun UnlockScreen(controller: SettingsController, onUnlocked: () -> Unit, onBack: () -> Unit) {
    var wrong by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf<Attempt?>(null) }

    LaunchedEffect(attempt) {
        val pattern = attempt?.pattern ?: return@LaunchedEffect
        if (controller.checkPattern(pattern)) onUnlocked() else wrong = true
    }

    Header(stringResource(R.string.unlock_hidden_apps), onBack)
    PatternScreenBody(
        title = stringResource(R.string.enter_privacy_password),
        message = null,
        error = wrong,
        onPattern = {
            wrong = false
            attempt = Attempt(it)
        },
    )
}

@Composable
private fun SetPatternScreen(
    controller: SettingsController,
    firstTime: Boolean,
    onDone: (firstTime: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val setup = remember { PatternSetup() }
    var title by remember { mutableStateOf(R.string.set_a_new_pattern) }
    var message by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf<Pattern?>(null) }

    LaunchedEffect(saving) {
        val pattern = saving ?: return@LaunchedEffect
        controller.savePattern(pattern)
        onDone(firstTime)
    }

    Header(stringResource(if (firstTime) R.string.set_privacy_password else R.string.change_privacy_password), onBack)
    PatternScreenBody(
        title = stringResource(title),
        message = message?.let { stringResource(it) },
        error = message != null,
        onPattern = { pattern ->
            when (val result = setup.submit(pattern)) {
                PatternSetup.Result.TooShort -> message = R.string.select_4_dots
                PatternSetup.Result.DrawAgain -> {
                    title = R.string.draw_pattern_again
                    message = null
                }
                PatternSetup.Result.Mismatch -> {
                    title = R.string.set_a_new_pattern
                    message = R.string.patterns_do_not_match
                }
                is PatternSetup.Result.Done -> saving = result.pattern
            }
        },
    )
}

@Composable
private fun PatternScreenBody(title: String, message: String?, error: Boolean, onPattern: (Pattern) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = message.orEmpty(),
            modifier = Modifier.padding(top = 12.dp).height(24.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))
        PatternPad(onComplete = onPattern, error = error, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
    }
}

@Composable
private fun HiddenAppsScreen(
    hasPattern: Boolean,
    apps: List<AppEntry>,
    hidden: Set<String>,
    icons: IconCache,
    controller: SettingsController,
    onSetPattern: () -> Unit,
    onBack: () -> Unit,
) {
    Header(stringResource(R.string.hide_apps), onBack)
    SettingsRow(
        text = stringResource(if (hasPattern) R.string.change_privacy_password else R.string.set_privacy_password),
        onClick = onSetPattern,
    ) { Icon(Icons.Outlined.Lock, contentDescription = null) }
    HorizontalDivider()
    // As in Yomo, the list appears only once a privacy pattern protects it.
    if (!hasPattern) return
    LazyColumn(Modifier.fillMaxSize()) {
        items(apps, key = { it.packageName }) { app ->
            HiddenAppRow(app, app.packageName in hidden, icons, controller)
        }
    }
}

/** Icon (opens the app), name with the hide switch, and an open button, as in Yomo. */
@Composable
private fun HiddenAppRow(app: AppEntry, isHidden: Boolean, icons: IconCache, controller: SettingsController) {
    var icon by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.key) { icon = icons.icon(app) }
    val openLabel = stringResource(R.string.open_app, app.label)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller.launch(app) }, modifier = Modifier.size(48.dp)) {
            icon?.let { Image(bitmap = it, contentDescription = openLabel, modifier = Modifier.size(40.dp)) }
        }
        Text(app.label, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = isHidden, onCheckedChange = { controller.setHidden(app, it) })
        IconButton(onClick = { controller.launch(app) }) {
            Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = openLabel)
        }
    }
}
