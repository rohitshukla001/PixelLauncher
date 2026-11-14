package dev.rohitshukla.pixellauncher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val a = Cell(0, 0, 0)
    private val b = Cell(0, 0, 1)

    private fun TestScope.repo() = HomeRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("home.preferences_pb") },
    )

    @Test
    fun `starts with one empty page`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(HomeLayout(), repo().layout.first())
    }

    @Test
    fun `placed and moved shortcuts are read back`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        assertTrue(repo.place("x/X", a, installed = null))
        assertTrue(repo.move(a, b, installed = null))
        assertEquals(mapOf(b to "x/X"), repo.layout.first().shortcuts)
    }

    @Test
    fun `a taken cell is refused`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.place("x/X", a, installed = null)
        assertFalse(repo.place("y/Y", a, installed = null))
        assertEquals(mapOf(a to "x/X"), repo.layout.first().shortcuts)
    }

    @Test
    fun `the cell of an uninstalled app counts as free`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.place("gone/G", a, installed = null)
        assertTrue(repo.place("new/N", a, installed = setOf("new/N")))
        assertEquals(mapOf(a to "new/N"), repo.layout.first().shortcuts)
    }

    @Test
    fun `nothing is dropped while the app list is unknown`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.place("x/X", a, installed = null)
        repo.fitTo(GridSize(3, 5), installed = null)
        assertEquals(mapOf(a to "x/X"), repo.layout.first().shortcuts)
    }

    @Test
    fun `pages are added up to nine`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repeat(HomeLayout.MAX_PAGES - 1) { assertTrue(repo.addPage()) }
        assertFalse(repo.addPage())
        assertEquals(9, repo.layout.first().pageCount)
    }

    @Test
    fun `remove frees the cell`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.place("x/X", a, installed = null)
        repo.remove(a)
        assertEquals(emptyMap<Cell, String>(), repo.layout.first().shortcuts)
    }
}
