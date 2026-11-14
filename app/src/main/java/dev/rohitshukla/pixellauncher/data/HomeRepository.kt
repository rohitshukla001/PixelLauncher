package dev.rohitshukla.pixellauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reads and writes the [HomeLayout]. Calls that take `installed` first free the cells of apps that
 * were uninstalled; pass null while the app list is still loading, so nothing is dropped by mistake.
 */
class HomeRepository(private val store: DataStore<Preferences>) {

    val layout: Flow<HomeLayout> = store.data.map(::read).distinctUntilChanged()

    /** False when the cell is taken or off-grid. */
    suspend fun place(key: String, cell: Cell, installed: Set<String>?): Boolean =
        update(installed, validateCell = cell) { it.place(key, cell) }

    /** False when the target cell is taken or off-grid. */
    suspend fun move(from: Cell, to: Cell, installed: Set<String>?): Boolean =
        update(installed, validateCell = to) { it.move(from, to) }

    suspend fun remove(cell: Cell) {
        update(installed = null) { it.remove(cell) }
    }

    /** Removes the package's shortcuts from home, for an app being hidden. */
    suspend fun removePackage(packageName: String) {
        update(installed = null) { it.withoutPackage(packageName) }
    }

    /** False at the page limit. */
    suspend fun addPage(): Boolean = update(installed = null) { it.addPage() }

    enum class PageRemoval { Removed, OnlyPage, NoRoom }

    /** Removes the last page, moving its apps onto the other pages (see [HomeLayout.removeLastPage]). */
    suspend fun removeLastPage(): PageRemoval {
        var result = PageRemoval.NoRoom
        store.edit { p ->
            val layout = read(p)
            if (layout.pageCount <= 1) {
                result = PageRemoval.OnlyPage
                return@edit
            }
            // The grid as last measured by home; without it, only an empty page can go.
            val grid = readGrid(p) ?: GridSize(columns = 0, rows = 0)
            val next = layout.removeLastPage(grid) ?: return@edit
            write(p, next)
            result = PageRemoval.Removed
        }
        return result
    }

    /** Also remembers [grid], so Settings can remove pages without the home screen open. One transaction. */
    suspend fun fitTo(grid: GridSize, installed: Set<String>?) {
        store.edit { p ->
            val stored = read(p)
            val ready = installed?.takeIf { it.isNotEmpty() }
            val next = (ready?.let(stored::withoutMissing) ?: stored).fitTo(grid)
            if (next != stored) write(p, next)
            p[GRID_COLUMNS] = grid.columns
            p[GRID_ROWS] = grid.rows
            p[GRID_HEADER] = grid.headerRows
        }
    }

    /**
     * A new home screen gets the Settings shortcut on page 1, top right under the clock, as in
     * Yomo. Happens once; removing the shortcut later is respected.
     */
    suspend fun seedSettingsShortcut(settingsKey: String, grid: GridSize) {
        store.edit { p ->
            if (p[SEEDED] == true) return@edit
            p[SEEDED] = true
            val layout = read(p)
            val preferred = Cell(page = 0, row = grid.headerRows, column = grid.columns - 1)
            val next = (if (grid.contains(preferred)) layout.place(settingsKey, preferred) else null)
                // Taken: park it just outside the grid, and fitTo moves it to the first free cell.
                ?: layout.copy(shortcuts = layout.shortcuts + (Cell(0, grid.rows, 0) to settingsKey)).fitTo(grid)
            write(p, next)
        }
    }

    private suspend fun update(installed: Set<String>?, validateCell: Cell? = null, change: (HomeLayout) -> HomeLayout?): Boolean {
        var done = false
        store.edit { p ->
            // Reject a cell outside the grid last measured by home (header rows, off-grid).
            if (validateCell != null) {
                val grid = readGrid(p)
                if (grid != null && !grid.contains(validateCell)) return@edit
            }
            val stored = read(p)
            // An empty set means the app list is not ready; treat it like null so nothing is dropped.
            val ready = installed?.takeIf { it.isNotEmpty() }
            val current = ready?.let(stored::withoutMissing) ?: stored
            val next = change(current) ?: return@edit
            done = true
            if (next != stored) write(p, next)
        }
        return done
    }

    enum class DockResult { Done, Full }

    /**
     * Drops [key] into the dock at [index] (reorder if it is already there). When it comes from a home
     * cell, that cell is freed. Returns [DockResult.Full] when the dock is full and the app is new.
     */
    suspend fun dropOnDock(key: String, index: Int, fromCell: Cell?, maxSlots: Int, installed: Set<String>?): DockResult {
        var result = DockResult.Full
        store.edit { p ->
            val ready = installed?.takeIf { it.isNotEmpty() }
            val stored = ready?.let(read(p)::withoutMissing) ?: read(p)
            val base = if (fromCell != null) stored.remove(fromCell) else stored
            val next = base.placeInDock(key, index, maxSlots) ?: return@edit
            write(p, next)
            result = DockResult.Done
        }
        return result
    }

    /** Moves [key] out of the dock onto home cell [cell]. False when the cell is off-grid or taken. */
    suspend fun dockToHome(key: String, cell: Cell, installed: Set<String>?): Boolean {
        var done = false
        store.edit { p ->
            val grid = readGrid(p)
            if (grid != null && !grid.contains(cell)) return@edit
            val ready = installed?.takeIf { it.isNotEmpty() }
            val stored = ready?.let(read(p)::withoutMissing) ?: read(p)
            val next = stored.removeFromDock(key).place(key, cell) ?: return@edit
            write(p, next)
            done = true
        }
        return done
    }

    suspend fun removeFromDock(key: String) {
        store.edit { p -> write(p, read(p).removeFromDock(key)) }
    }

    /** Fills the dock once with the phone's default apps, so it starts like Yomo's "Big Four". */
    suspend fun seedDock(keys: List<String>) {
        store.edit { p ->
            if (p[DOCK_SEEDED] == true) return@edit
            p[DOCK_SEEDED] = true
            if (keys.isNotEmpty()) p[DOCK] = keys.joinToString("\n")
        }
    }

    private fun read(p: Preferences) = HomeLayout.decode(p[PAGES], p[SHORTCUTS], p[DOCK])

    private fun readGrid(p: Preferences): GridSize? {
        val columns = p[GRID_COLUMNS] ?: return null
        val rows = p[GRID_ROWS] ?: return null
        return GridSize(columns, rows, p[GRID_HEADER] ?: 0)
    }

    private fun write(p: MutablePreferences, layout: HomeLayout) {
        p[PAGES] = layout.pageCount
        p[SHORTCUTS] = layout.encodeShortcuts()
        p[DOCK] = layout.encodeDock()
    }

    private companion object {
        val PAGES = intPreferencesKey("home_pages")
        val SHORTCUTS = stringSetPreferencesKey("home_shortcuts")
        val DOCK = stringPreferencesKey("home_dock")
        val DOCK_SEEDED = booleanPreferencesKey("dock_seeded")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val GRID_HEADER = intPreferencesKey("grid_header_rows")
        val SEEDED = booleanPreferencesKey("settings_shortcut_seeded")
    }
}
