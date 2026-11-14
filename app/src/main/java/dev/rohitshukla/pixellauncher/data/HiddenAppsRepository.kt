package dev.rohitshukla.pixellauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Hidden apps (by package, as in Yomo) and the privacy pattern that guards their list. There is
 * no way to recover a forgotten pattern; as in Yomo, only reinstalling clears it.
 */
class HiddenAppsRepository(private val store: DataStore<Preferences>) {

    val hiddenPackages: Flow<Set<String>> = store.data.map { it[HIDDEN].orEmpty() }.distinctUntilChanged()

    val hasPattern: Flow<Boolean> = store.data.map { it[PATTERN] != null }.distinctUntilChanged()

    suspend fun setHidden(packageName: String, hidden: Boolean) {
        store.edit { p ->
            val current = p[HIDDEN].orEmpty()
            p[HIDDEN] = if (hidden) current + packageName else current - packageName
        }
    }

    suspend fun setPattern(pattern: Pattern) {
        val hash = withContext(Dispatchers.Default) { PatternHash.create(pattern) }
        store.edit { it[PATTERN] = hash }
    }

    suspend fun checkPattern(pattern: Pattern): Boolean {
        val stored = store.data.first()[PATTERN] ?: return false
        return withContext(Dispatchers.Default) { PatternHash.matches(stored, pattern) }
    }

    private companion object {
        val HIDDEN = stringSetPreferencesKey("hidden_packages")
        val PATTERN = stringPreferencesKey("pattern_hash")
    }
}
