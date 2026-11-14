package dev.rohitshukla.pixellauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnePerPackageTest {

    private val voice = AppEntry("Voice Search", "com.google", ".Voice")
    private val google = AppEntry("Google", "com.google", ".Search")
    private val chrome = AppEntry("Chrome", "com.chrome", ".Main")

    @Test
    fun `keeps the package's main icon, not the first listed`() {
        assertEquals(listOf(google, chrome), onePerPackage(listOf(voice, google, chrome)) { it.label == "Google" })
    }

    @Test
    fun `falls back to the first listed when no icon is the main one`() {
        assertEquals(listOf(voice), onePerPackage(listOf(voice, google)) { false })
    }

    @Test
    fun `the personal copy wins over the work copy`() {
        val personal = chrome.copy(profile = 0)
        val work = chrome.copy(profile = 10)
        assertEquals(listOf(personal), onePerPackage(listOf(personal, work)) { true })
    }

    @Test
    fun `a work-only app is kept`() {
        val workOnly = AppEntry("Music", "com.music", ".Main", profile = 10)
        assertEquals(listOf(chrome, workOnly), onePerPackage(listOf(chrome, workOnly)) { false })
    }
}
