package dev.rohitshukla.pixellauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * How often each app was opened from the launcher. Counts only launcher launches, so it needs no
 * usage-access permission (Yomo does not ask for one either).
 */
class UsageRepository(private val store: DataStore<Preferences>) {

    /** App key to launch count. */
    val launchCounts: Flow<Map<String, Int>> = store.data.map { p ->
        p.asMap().mapNotNull { (key, value) ->
            val app = key.name.removePrefix(PREFIX)
            if (key.name.startsWith(PREFIX) && value is Int) app to value else null
        }.toMap()
    }.distinctUntilChanged()

    suspend fun recordLaunch(appKey: String) {
        val key = intPreferencesKey(PREFIX + appKey)
        store.edit { it[key] = (it[key] ?: 0) + 1 }
    }

    private companion object {
        const val PREFIX = "launches:"
    }
}

/** The drawer's Suggested row: the most-launched installed apps. */
object Suggestions {

    const val LIMIT = 4

    /** Ties keep the A–Z order of [apps]. Apps never launched are not suggested. */
    fun mostUsed(counts: Map<String, Int>, apps: List<AppEntry>, limit: Int = LIMIT): List<AppEntry> =
        apps.filter { (counts[it.key] ?: 0) > 0 }
            .sortedByDescending { counts[it.key] }
            .take(limit)
}
