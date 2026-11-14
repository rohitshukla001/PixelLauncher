package dev.rohitshukla.pixellauncher.settings

import android.os.SystemClock
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.semantics.Role
import dev.rohitshukla.pixellauncher.data.LauncherSettings
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.IconCache
import dev.rohitshukla.pixellauncher.data.Pattern
import dev.rohitshukla.pixellauncher.data.PatternSetup
import kotlinx.coroutines.delay

/** Everything the settings screens ask for. SettingsActivity implements it. */
interface SettingsController {
    suspend fun checkPattern(pattern: Pattern): Boolean
    suspend fun savePattern(pattern: Pattern)
    fun setHidden(app: AppEntry, hidden: Boolean)
    fun launch(app: AppEntry)
    fun toast(message: Int)
    fun changeWallpaper()
    fun setSearchBarEnabled(on: Boolean)
}

enum class SettingsScreen { Main, Unlock, HiddenApps, SetPattern }

/** A pattern attempt. Not a data class, so the same pattern drawn twice still counts as a new attempt. */
private class Attempt(val pattern: Pattern)

private const val ATTEMPTS_BEFORE_LOCKOUT = 5
private const val LOCKOUT_MILLIS = 30_000L

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
    icons: IconCache,
    controller: SettingsController,
    onExit: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (screen) {
                SettingsScreen.Main -> MainScreen(
                    settings = settings,
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
/** The settings rows, without Yomo's Premium, sharing, social and sign-in rows. */
@Composable
private fun MainScreen(
    settings: LauncherSettings,
    controller: SettingsController,
    onHideApps: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.settings_name), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsRow(stringResource(R.string.change_wallpaper), controller::changeWallpaper) { Icon(Icons.Outlined.Home, contentDescription = null) }
            SettingsRow(stringResource(R.string.hide_apps), onHideApps) { Icon(Icons.Outlined.Lock, contentDescription = null) }
            SwitchRow(stringResource(R.string.enable_search_bar), settings.searchBarEnabled, controller::setSearchBarEnabled) {
                Icon(Icons.Outlined.Search, contentDescription = null)
            }
        }
        Footer()
    }
}

/** Centred credit line pinned to the bottom. */
@Composable
private fun Footer() {
    val prefix = stringResource(R.string.footer_credit_prefix)
    val name = stringResource(R.string.footer_author)
    // "Rohit Shukla" is a bold link (no underline, same colour), opening the author's site.
    val credit = remember(prefix, name) {
        buildAnnotatedString {
            append(prefix)
            withLink(LinkAnnotation.Url(AUTHOR_URL, TextLinkStyles(style = SpanStyle(fontWeight = FontWeight.Bold)))) {
                append(name)
            }
        }
    }
    Text(
        text = credit,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val AUTHOR_URL = "https://info.rohitshukla.net/"

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
    var failures by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var locked by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        val pattern = attempt?.pattern ?: return@LaunchedEffect
        if (controller.checkPattern(pattern)) {
            onUnlocked()
        } else {
            wrong = true
            failures++
            // After a few wrong tries, lock input for a growing spell, so the pattern can't be brute-forced by hand.
            if (failures >= ATTEMPTS_BEFORE_LOCKOUT) {
                lockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_MILLIS * (failures - ATTEMPTS_BEFORE_LOCKOUT + 1)
            }
        }
    }

    LaunchedEffect(lockedUntil) {
        val wait = lockedUntil - SystemClock.elapsedRealtime()
        if (wait <= 0) return@LaunchedEffect
        locked = true
        delay(wait)
        locked = false
    }

    Header(stringResource(R.string.unlock_hidden_apps), onBack)
    PatternScreenBody(
        title = stringResource(R.string.enter_privacy_password),
        message = if (locked) stringResource(R.string.too_many_attempts) else null,
        error = wrong || locked,
        onPattern = {
            if (locked) return@PatternScreenBody
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
