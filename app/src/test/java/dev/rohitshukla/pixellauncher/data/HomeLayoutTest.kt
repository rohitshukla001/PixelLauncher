package dev.rohitshukla.pixellauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomeLayoutTest {

    private val a = Cell(0, 0, 0)
    private val b = Cell(0, 0, 1)

    @Test
    fun `starts with one empty page`() {
        assertEquals(HomeLayout(pageCount = 1, shortcuts = emptyMap()), HomeLayout())
    }

    @Test
    fun `place puts the app in a free cell`() {
        assertEquals("x/X", HomeLayout().place("x/X", a)!!.keyAt(a))
    }

    @Test
    fun `place refuses a taken cell`() {
        assertNull(HomeLayout().place("x/X", a)!!.place("y/Y", a))
    }

    @Test
    fun `place refuses a page that does not exist`() {
        assertNull(HomeLayout(pageCount = 1).place("x/X", Cell(1, 0, 0)))
    }

    @Test
    fun `the same app may sit in two cells, as in Yomo`() {
        val layout = HomeLayout().place("x/X", a)!!.place("x/X", b)!!
        assertEquals(mapOf(a to "x/X", b to "x/X"), layout.shortcuts)
    }

    @Test
    fun `move to a free cell frees the old one`() {
        val moved = HomeLayout().place("x/X", a)!!.move(a, b)!!
        assertEquals(mapOf(b to "x/X"), moved.shortcuts)
    }

    @Test
    fun `move onto a taken cell is refused`() {
        val layout = HomeLayout().place("x/X", a)!!.place("y/Y", b)!!
        assertNull(layout.move(a, b))
    }

    @Test
    fun `dropping back on the same cell changes nothing`() {
        val layout = HomeLayout().place("x/X", a)!!
        assertSame(layout, layout.move(a, a))
    }

    @Test
    fun `remove frees the cell and keeps the page`() {
        val layout = HomeLayout(pageCount = 2, shortcuts = mapOf(Cell(1, 0, 0) to "x/X")).remove(Cell(1, 0, 0))
        assertEquals(HomeLayout(pageCount = 2), layout)
    }

    @Test
    fun `pages stop at nine`() {
        val nine = (1 until HomeLayout.MAX_PAGES).fold(HomeLayout()) { l, _ -> l.addPage()!! }
        assertEquals(9, nine.pageCount)
        assertNull(nine.addPage())
    }

    @Test
    fun `uninstalled apps free their cells`() {
        val layout = HomeLayout(shortcuts = mapOf(a to "kept/K", b to "gone/G")).withoutMissing(setOf("kept/K"))
        assertEquals(mapOf(a to "kept/K"), layout.shortcuts)
    }

    @Test
    fun `a smaller grid moves outside shortcuts to the first free cells`() {
        val layout = HomeLayout(shortcuts = mapOf(a to "a/A", Cell(0, 0, 3) to "d/D", Cell(0, 5, 0) to "low/L"))
        val fitted = layout.fitTo(GridSize(columns = 3, rows = 5))
        assertEquals(mapOf(a to "a/A", b to "d/D", Cell(0, 0, 2) to "low/L"), fitted.shortcuts)
    }

    @Test
    fun `fitting adds a page when the first is full`() {
        val grid = GridSize(columns = 1, rows = 1)
        val layout = HomeLayout(shortcuts = mapOf(a to "a/A", b to "b/B")).fitTo(grid)
        assertEquals(2, layout.pageCount)
        assertEquals(mapOf(a to "a/A", Cell(1, 0, 0) to "b/B"), layout.shortcuts)
    }

    @Test
    fun `fitting a layout that already fits changes nothing`() {
        val layout = HomeLayout(shortcuts = mapOf(a to "a/A"))
        assertSame(layout, layout.fitTo(GridSize(3, 5)))
    }

    @Test
    fun `encode and decode round trip`() {
        val layout = HomeLayout(pageCount = 3, shortcuts = mapOf(a to "com.a/com.a.Main", Cell(2, 4, 1) to "com.b/.Launcher"))
        assertEquals(layout, HomeLayout.decode(layout.pageCount, layout.encodeShortcuts()))
    }

    @Test
    fun `decode skips unreadable entries and clamps the page count`() {
        val layout = HomeLayout.decode(
            pageCount = 40,
            shortcuts = setOf("0,1,2|ok/Ok", "x,1,2|bad/B", "0,-1,0|neg/N", "0,1|short/S", "0,0,0|", "nothing"),
        )
        assertEquals(HomeLayout(pageCount = 9, shortcuts = mapOf(Cell(0, 1, 2) to "ok/Ok")), layout)
        assertEquals(HomeLayout(), HomeLayout.decode(null, null))
    }
}

class GridSizeTest {

    @Test
    fun `cells are at least 110 by 92 dp, as in Yomo`() {
        // Both also reserve 2 rows of page 1 for the 126 dp clock.
        assertEquals(GridSize(columns = 3, rows = 5, headerRows = 2), GridSize.fit(widthDp = 411f, heightDp = 500f))
        assertEquals(GridSize(columns = 7, rows = 9, headerRows = 2), GridSize.fit(widthDp = 800f, heightDp = 840f))
    }

    @Test
    fun `never fewer than one column and row`() {
        assertEquals(GridSize(1, 1, headerRows = 1), GridSize.fit(widthDp = 50f, heightDp = 40f))
    }
}
