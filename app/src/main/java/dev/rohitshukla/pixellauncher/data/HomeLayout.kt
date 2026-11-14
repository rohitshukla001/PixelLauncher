package dev.rohitshukla.pixellauncher.data

import kotlin.math.max

/** A home grid position. */
data class Cell(val page: Int, val row: Int, val column: Int)

/**
 * Columns and rows that fit the home pager, as in Yomo: every cell is at least 110 x 92 dp. The top
 * [headerRows] of page 1 hold the clock (and the search bar when it is on), so apps cannot go there.
 */
data class GridSize(val columns: Int, val rows: Int, val headerRows: Int = 0) {

    fun contains(cell: Cell): Boolean =
        cell.row in 0 until rows && cell.column in 0 until columns && !(cell.page == 0 && cell.row < headerRows)

    companion object {
        const val MIN_CELL_WIDTH_DP = 110f
        const val MIN_CELL_HEIGHT_DP = 92f

        /** Yomo's clock row: 110 dp of text plus 16 dp padding. */
        const val CLOCK_HEIGHT_DP = 126f

        /** Yomo's home search bar row. */
        const val SEARCH_BAR_HEIGHT_DP = 56f

        fun fit(widthDp: Float, heightDp: Float, searchBar: Boolean = false): GridSize {
            val rows = max(1, (heightDp / MIN_CELL_HEIGHT_DP).toInt())
            val header = CLOCK_HEIGHT_DP + if (searchBar) SEARCH_BAR_HEIGHT_DP else 0f
            val cellHeight = heightDp / rows
            return GridSize(
                columns = max(1, (widthDp / MIN_CELL_WIDTH_DP).toInt()),
                rows = rows,
                // Leave at least one app row on page 1: never let the header take every row.
                headerRows = kotlin.math.ceil(header / cellHeight).toInt().coerceIn(1, (rows - 1).coerceAtLeast(1)),
            )
        }
    }
}

/**
 * The home pages and the app in each cell. As in Yomo, the page count is stored on its own (a page
 * may be empty), there are 1 to [MAX_PAGES] pages, and the same app may sit in more than one cell.
 */
data class HomeLayout(
    val pageCount: Int = 1,
    val shortcuts: Map<Cell, String> = emptyMap(),
    /** The dock row, left to right. Fully user-managed. */
    val dock: List<String> = emptyList(),
) {

    fun keyAt(cell: Cell): String? = shortcuts[cell]

    fun isFree(cell: Cell): Boolean = cell !in shortcuts

    /**
     * Puts [key] in the dock at [index], moving it if it is already there. Null when the dock is full
     * ([maxSlots]) and [key] is new, so a full dock refuses a new app but still allows reordering.
     */
    fun placeInDock(key: String, index: Int, maxSlots: Int): HomeLayout? {
        val without = dock - key
        if (key !in dock && without.size >= maxSlots) return null
        val at = index.coerceIn(0, without.size)
        return copy(dock = without.toMutableList().apply { add(at, key) })
    }

    fun removeFromDock(key: String): HomeLayout = copy(dock = dock - key)

    /** Null when the cell is taken or on a page that does not exist. */
    fun place(key: String, cell: Cell): HomeLayout? =
        if (cell.page in 0 until pageCount && isFree(cell)) copy(shortcuts = shortcuts + (cell to key)) else null

    /** Null when nothing is at [from] or [to] is taken. Dropping back on [from] is no change. */
    fun move(from: Cell, to: Cell): HomeLayout? {
        if (from == to) return this
        val key = shortcuts[from] ?: return null
        return remove(from).place(key, to)
    }

    fun remove(cell: Cell): HomeLayout = copy(shortcuts = shortcuts - cell)

    /** Null at [MAX_PAGES]. */
    fun addPage(): HomeLayout? = if (pageCount < MAX_PAGES) copy(pageCount = pageCount + 1) else null

    /**
     * Removes the last page, as Yomo's "Remove shortcuts page" does. Its apps move to the first free
     * cells of the other pages. Null with only one page, or when they do not all fit (nothing changes then).
     */
    fun removeLastPage(grid: GridSize): HomeLayout? {
        if (pageCount <= 1) return null
        val last = pageCount - 1
        val moving = shortcuts.filterKeys { it.page == last }
        var layout = copy(pageCount = last, shortcuts = shortcuts - moving.keys)
        for ((_, key) in moving.entries.sortedWith(compareBy({ it.key.row }, { it.key.column }))) {
            val free = layout.firstFree(grid) ?: return null
            layout = layout.copy(shortcuts = layout.shortcuts + (free to key))
        }
        return layout
    }

    /** Drops every shortcut of the package, from home and the dock; used when it is hidden (as in Yomo). */
    fun withoutPackage(packageName: String): HomeLayout = copy(
        shortcuts = shortcuts.filterValues { !it.startsWith("$packageName/") },
        dock = dock.filter { !it.startsWith("$packageName/") },
    )

    /** Drops home and dock entries for apps that are no longer installed. */
    fun withoutMissing(installed: Set<String>): HomeLayout = copy(
        shortcuts = shortcuts.filterValues { it in installed },
        dock = dock.filter { it in installed },
    )

    /**
     * Moves shortcuts that are outside [grid] (the grid got smaller) to the first free cells,
     * adding pages up to [MAX_PAGES]. Any that still do not fit are kept, to come back if the grid grows.
     */
    fun fitTo(grid: GridSize): HomeLayout {
        val misplaced = shortcuts.filterKeys { !grid.contains(it) || it.page >= pageCount }
        if (misplaced.isEmpty()) return this
        var layout = copy(shortcuts = shortcuts - misplaced.keys)
        val unplaced = mutableMapOf<Cell, String>()
        for ((cell, key) in misplaced.entries.sortedWith(compareBy({ it.key.page }, { it.key.row }, { it.key.column }))) {
            var free = layout.firstFree(grid)
            while (free == null) {
                layout = layout.addPage() ?: break
                free = layout.firstFree(grid)
            }
            if (free == null) unplaced[cell] = key else layout = layout.copy(shortcuts = layout.shortcuts + (free to key))
        }
        return layout.copy(shortcuts = layout.shortcuts + unplaced)
    }

    private fun firstFree(grid: GridSize): Cell? {
        for (page in 0 until pageCount) for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
            val cell = Cell(page, row, column)
            if (grid.contains(cell) && isFree(cell)) return cell
        }
        return null
    }

    fun encodeShortcuts(): Set<String> =
        shortcuts.map { (c, key) -> "${c.page},${c.row},${c.column}$SEPARATOR$key" }.toSet()

    /** The dock as a single ordered string; keys never contain the separator. */
    fun encodeDock(): String = dock.joinToString(DOCK_SEPARATOR)

    companion object {
        const val MAX_PAGES = 9

        private const val SEPARATOR = '|'
        private const val DOCK_SEPARATOR = "\n"

        /** Skips entries it cannot read, so one bad value never empties the home screen. */
        fun decode(pageCount: Int?, shortcuts: Set<String>?, dock: String? = null): HomeLayout {
            val cells = shortcuts.orEmpty().mapNotNull { entry ->
                val position = entry.substringBefore(SEPARATOR, "").split(',').map { it.toIntOrNull() }
                val key = entry.substringAfter(SEPARATOR, "")
                val (page, row, column) = position.takeIf { it.size == 3 && it.all { n -> n != null && n >= 0 } }
                    ?.map { it!! } ?: return@mapNotNull null
                if (key.isEmpty()) null else Cell(page, row, column) to key
            }.toMap()
            val dockKeys = dock?.split(DOCK_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
            return HomeLayout((pageCount ?: 1).coerceIn(1, MAX_PAGES), cells, dockKeys)
        }
    }
}
