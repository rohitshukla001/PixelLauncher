package dev.rohitshukla.pixellauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    private fun apps(vararg labels: String) = labels.map { AppEntry(it, "pkg.${it.lowercase()}", "Main") }

    private fun search(query: String, vararg labels: String) = AppSearch.filter(apps(*labels), query).map { it.label }

    @Test
    fun `blank query returns every app in order`() {
        assertEquals(listOf("Clock", "Alarm"), search("  ", "Clock", "Alarm"))
    }

    @Test
    fun `matches ignore case`() {
        assertEquals(listOf("Calculator", "Calendar"), search("CAL", "Calculator", "Calendar", "Music"))
    }

    @Test
    fun `matches ignore accents`() {
        assertEquals(listOf("Café Menu"), search("cafe", "Café Menu", "Camera"))
    }

    @Test
    fun `a match at the start of a word ranks before a match inside a word`() {
        // "Graph Tool" only contains "ph"; "Photos" and "Google Phone" have a word starting with it.
        assertEquals(
            listOf("Photos", "Google Phone", "Graph Tool"),
            search("ph", "Graph Tool", "Photos", "Google Phone"),
        )
    }

    @Test
    fun `no match returns nothing`() {
        assertEquals(emptyList<String>(), search("zzz", "Clock", "Alarm"))
    }
}
