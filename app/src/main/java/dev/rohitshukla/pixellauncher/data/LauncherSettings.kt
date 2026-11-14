package dev.rohitshukla.pixellauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.net.URLEncoder

/** The clock's position, as in Yomo's "Set alignment". */
enum class ClockAlignment { Left, Center, Right }

/** Yomo's "Set Search Engine" choices, in its order. */
enum class SearchEngine(val label: String) {
    Bing("Bing"),
    Yahoo("Yahoo"),
    Google("Google"),
    ;

    fun resultsUrl(query: String): String {
        val (base, param) = when (this) {
            Bing -> "https://www.bing.com/search" to "q"
            Yahoo -> "https://search.yahoo.com/search" to "p"
            Google -> "https://www.google.com/search" to "q"
        }
        return "$base?$param=" + URLEncoder.encode(query, Charsets.UTF_8.name())
    }
}

/** Yomo's defaults: clock centred, search bar off, suggestions on, Google. */
data class LauncherSettings(
    val clockAlignment: ClockAlignment = ClockAlignment.Center,
    val searchBarEnabled: Boolean = false,
    val suggestionsEnabled: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.Google,
    /** False until the first-run wallpaper screen is done. */
    val setupComplete: Boolean = false,
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<LauncherSettings> = store.data.map(::read).distinctUntilChanged()

    suspend fun setClockAlignment(value: ClockAlignment) = store.edit { it[ALIGNMENT] = value.name }

    suspend fun setSearchBarEnabled(on: Boolean) = store.edit { it[SEARCH_BAR] = on }

    suspend fun setSuggestionsEnabled(on: Boolean) = store.edit { it[SUGGESTIONS] = on }

    suspend fun setSearchEngine(value: SearchEngine) = store.edit { it[ENGINE] = value.name }

    suspend fun completeSetup() = store.edit { it[SETUP_COMPLETE] = true }

    private fun read(p: Preferences) = LauncherSettings(
        clockAlignment = enumOrDefault(p[ALIGNMENT], ClockAlignment.Center),
        searchBarEnabled = p[SEARCH_BAR] ?: false,
        suggestionsEnabled = p[SUGGESTIONS] ?: true,
        searchEngine = enumOrDefault(p[ENGINE], SearchEngine.Google),
        setupComplete = p[SETUP_COMPLETE] ?: false,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        val ALIGNMENT = stringPreferencesKey("clock_alignment")
        val SEARCH_BAR = booleanPreferencesKey("search_bar_enabled")
        val SUGGESTIONS = booleanPreferencesKey("suggestions_enabled")
        val ENGINE = stringPreferencesKey("search_engine")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }
}
