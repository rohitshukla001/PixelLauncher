package dev.rohitshukla.pixellauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockTest {

    private fun dock(vararg keys: String) = HomeLayout(dock = keys.toList())

    @Test
    fun `place appends and inserts at the index`() {
        assertEquals(listOf("a/A"), dock().placeInDock("a/A", 0, maxSlots = 4)!!.dock)
        assertEquals(listOf("a/A", "b/B"), dock("a/A").placeInDock("b/B", 5, maxSlots = 4)!!.dock)
        assertEquals(listOf("x/X", "a/A", "b/B"), dock("a/A", "b/B").placeInDock("x/X", 0, maxSlots = 4)!!.dock)
    }

    @Test
    fun `placing an app already in the dock moves it`() {
        assertEquals(listOf("b/B", "a/A"), dock("a/A", "b/B").placeInDock("a/A", 2, maxSlots = 4)!!.dock)
    }

    @Test
    fun `a full dock refuses a new app but still reorders`() {
        val full = dock("a/A", "b/B", "c/C")
        assertNull(full.placeInDock("d/D", 0, maxSlots = 3))
        assertEquals(listOf("c/C", "a/A", "b/B"), full.placeInDock("c/C", 0, maxSlots = 3)!!.dock)
    }

    @Test
    fun `remove and round trip`() {
        assertEquals(listOf("a/A"), dock("a/A", "b/B").removeFromDock("b/B").dock)
        val layout = HomeLayout(pageCount = 2, shortcuts = mapOf(Cell(0, 0, 0) to "h/H"), dock = listOf("a/A", "b/B"))
        assertEquals(layout, HomeLayout.decode(layout.pageCount, layout.encodeShortcuts(), layout.encodeDock()))
    }

    @Test
    fun `hiding and uninstalling drop the app from the dock too`() {
        val layout = dock("com.a/.M", "com.b/.M")
        assertEquals(listOf("com.b/.M"), layout.withoutPackage("com.a").dock)
        assertEquals(listOf("com.a/.M"), layout.withoutMissing(setOf("com.a/.M")).dock)
    }
}
