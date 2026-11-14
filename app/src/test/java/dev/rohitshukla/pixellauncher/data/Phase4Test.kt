package dev.rohitshukla.pixellauncher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("settings.preferences_pb") },
    )

    @Test
    fun `defaults are Yomo's`() = runTest(UnconfinedTestDispatcher()) {
        val s = repo().settings.first()
        assertEquals(ClockAlignment.Center, s.clockAlignment)
        assertFalse(s.searchBarEnabled)
        assertTrue(s.suggestionsEnabled)
        assertEquals(SearchEngine.Google, s.searchEngine)
        assertFalse(s.setupComplete)
    }

    @Test
    fun `choices are read back`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.setClockAlignment(ClockAlignment.Right)
        repo.setSearchBarEnabled(true)
        repo.setSuggestionsEnabled(false)
        repo.setSearchEngine(SearchEngine.Bing)
        repo.completeSetup()
        assertEquals(LauncherSettings(ClockAlignment.Right, true, false, SearchEngine.Bing, true), repo.settings.first())
    }
}

class SearchEngineTest {

    @Test
    fun `each engine builds its results address with the query encoded`() {
        assertEquals("https://www.google.com/search?q=red+fox+%26+co", SearchEngine.Google.resultsUrl("red fox & co"))
        assertEquals("https://www.bing.com/search?q=caf%C3%A9", SearchEngine.Bing.resultsUrl("café"))
        assertEquals("https://search.yahoo.com/search?p=a%2Fb", SearchEngine.Yahoo.resultsUrl("a/b"))
    }

    @Test
    fun `engines are offered in Yomo's order`() {
        assertEquals(listOf("Bing", "Yahoo", "Google"), SearchEngine.entries.map { it.label })
    }
}

class GridHeaderTest {

    @Test
    fun `the clock takes the top rows of page 1 only`() {
        // 411 x 600 dp: 3 columns, 6 rows of 100 dp; the 126 dp clock needs 2 rows.
        val grid = GridSize.fit(411f, 600f)
        assertEquals(GridSize(columns = 3, rows = 6, headerRows = 2), grid)
        assertFalse(grid.contains(Cell(0, 1, 0)))
        assertTrue(grid.contains(Cell(0, 2, 0)))
        assertTrue(grid.contains(Cell(1, 0, 0)))
    }

    @Test
    fun `the search bar can take another row`() {
        // Rows of 100 dp: the clock already needs 2, and the 56 dp search bar fits in them.
        assertEquals(2, GridSize.fit(411f, 600f, searchBar = true).headerRows)
        // A 2-row screen: the header is capped at 1 row so at least one app row always remains,
        // even with the search bar on.
        assertEquals(1, GridSize.fit(411f, 260f).headerRows)
        assertEquals(1, GridSize.fit(411f, 260f, searchBar = true).headerRows)
    }

    @Test
    fun `turning the search bar on moves apps out of the new header row`() {
        val withoutBar = GridSize(3, 6, headerRows = 1)
        val withBar = GridSize(3, 6, headerRows = 2)
        val layout = HomeLayout(shortcuts = mapOf(Cell(0, 1, 0) to "a/A")).fitTo(withoutBar)
        assertEquals(mapOf(Cell(0, 1, 0) to "a/A"), layout.shortcuts)
        assertEquals(mapOf(Cell(0, 2, 0) to "a/A"), layout.fitTo(withBar).shortcuts)
    }
}

class RemovePageTest {

    private val grid = GridSize(columns = 2, rows = 2, headerRows = 1)

    @Test
    fun `the only page cannot go`() {
        assertNull(HomeLayout().removeLastPage(grid))
    }

    @Test
    fun `an empty last page just goes`() {
        assertEquals(HomeLayout(pageCount = 1), HomeLayout(pageCount = 2).removeLastPage(grid))
    }

    @Test
    fun `its apps move to free cells on the other pages`() {
        val layout = HomeLayout(pageCount = 2, shortcuts = mapOf(Cell(1, 0, 1) to "b/B"))
        assertEquals(HomeLayout(pageCount = 1, shortcuts = mapOf(Cell(0, 1, 0) to "b/B")), layout.removeLastPage(grid))
    }

    @Test
    fun `refused when the apps do not fit`() {
        // Page 1 has two free cells (row 0 is the clock); three apps cannot move there.
        val layout = HomeLayout(pageCount = 2, shortcuts = mapOf(Cell(1, 0, 0) to "a/A", Cell(1, 0, 1) to "b/B", Cell(1, 1, 0) to "c/C"))
        assertNull(layout.removeLastPage(grid))
    }
}

class HomeRepositoryPhase4Test {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo() = HomeRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("home.preferences_pb") },
    )

    private val grid = GridSize(columns = 3, rows = 6, headerRows = 2)

    @Test
    fun `the settings shortcut goes top right under the clock, once`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.seedSettingsShortcut("me/.Settings", grid)
        assertEquals(mapOf(Cell(0, 2, 2) to "me/.Settings"), repo.layout.first().shortcuts)
        repo.remove(Cell(0, 2, 2))
        repo.seedSettingsShortcut("me/.Settings", grid)
        assertEquals(emptyMap<Cell, String>(), repo.layout.first().shortcuts)
    }

    @Test
    fun `when top right is taken the settings shortcut takes the first free cell`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.place("x/X", Cell(0, 2, 2), installed = null)
        repo.seedSettingsShortcut("me/.Settings", grid)
        assertEquals(mapOf(Cell(0, 2, 2) to "x/X", Cell(0, 2, 0) to "me/.Settings"), repo.layout.first().shortcuts)
    }

    @Test
    fun `remove page uses the grid home last measured`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.addPage()
        repo.place("b/B", Cell(1, 0, 0), installed = null)
        // Home has not measured yet: the page's app has nowhere known to go.
        assertEquals(HomeRepository.PageRemoval.NoRoom, repo.removeLastPage())
        repo.fitTo(grid, installed = null)
        assertEquals(HomeRepository.PageRemoval.Removed, repo.removeLastPage())
        assertEquals(HomeLayout(pageCount = 1, shortcuts = mapOf(Cell(0, 2, 0) to "b/B")), repo.layout.first())
        assertEquals(HomeRepository.PageRemoval.OnlyPage, repo.removeLastPage())
    }
}
