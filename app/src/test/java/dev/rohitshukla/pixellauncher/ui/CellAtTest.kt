package dev.rohitshukla.pixellauncher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.rohitshukla.pixellauncher.data.Cell
import dev.rohitshukla.pixellauncher.data.GridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellAtTest {

    // A 300 x 500 pager at (0, 100), split into 3 columns and 5 rows of 100 x 100.
    private val pager = Rect(0f, 100f, 300f, 600f)
    private val grid = GridSize(columns = 3, rows = 5)

    @Test
    fun `finds the cell under the point on the current page`() {
        assertEquals(Cell(page = 2, row = 0, column = 0), cellAt(Offset(10f, 110f), pager, grid, page = 2))
        assertEquals(Cell(page = 0, row = 4, column = 2), cellAt(Offset(299f, 599f), pager, grid, page = 0))
        assertEquals(Cell(page = 0, row = 2, column = 1), cellAt(Offset(150f, 350f), pager, grid, page = 0))
    }

    @Test
    fun `outside the pager is no cell`() {
        assertNull(cellAt(Offset(150f, 50f), pager, grid, page = 0))
        assertNull(cellAt(Offset(150f, 700f), pager, grid, page = 0))
    }

    @Test
    fun `no cell before the pager has been measured`() {
        assertNull(cellAt(Offset(10f, 10f), Rect.Zero, grid, page = 0))
    }

    @Test
    fun `page 1's clock rows take no drop`() {
        val withClock = GridSize(columns = 3, rows = 5, headerRows = 1)
        assertNull(cellAt(Offset(10f, 110f), pager, withClock, page = 0))
        assertEquals(Cell(page = 1, row = 0, column = 0), cellAt(Offset(10f, 110f), pager, withClock, page = 1))
    }
}
