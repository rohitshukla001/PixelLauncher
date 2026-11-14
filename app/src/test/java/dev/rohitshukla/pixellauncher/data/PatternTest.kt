package dev.rohitshukla.pixellauncher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatternSetupTest {

    private val l = listOf(0, 3, 6, 7)

    @Test
    fun `fewer than four dots is refused, as in Yomo`() {
        val setup = PatternSetup()
        assertEquals(PatternSetup.Result.TooShort, setup.submit(listOf(0, 1, 2)))
        assertFalse(setup.confirming)
    }

    @Test
    fun `draw twice to set it`() {
        val setup = PatternSetup()
        assertEquals(PatternSetup.Result.DrawAgain, setup.submit(l))
        assertTrue(setup.confirming)
        assertEquals(PatternSetup.Result.Done(l), setup.submit(l))
    }

    @Test
    fun `a mismatch starts over from the first drawing`() {
        val setup = PatternSetup()
        setup.submit(l)
        assertEquals(PatternSetup.Result.Mismatch, setup.submit(listOf(2, 5, 8, 7)))
        assertFalse(setup.confirming)
        assertEquals(PatternSetup.Result.DrawAgain, setup.submit(listOf(2, 5, 8, 7)))
    }
}

class PatternHashTest {

    private val pattern = listOf(0, 4, 8, 5)

    @Test
    fun `the same pattern matches and others do not`() {
        val stored = PatternHash.create(pattern)
        assertTrue(PatternHash.matches(stored, pattern))
        assertFalse(PatternHash.matches(stored, listOf(0, 4, 8)))
        assertFalse(PatternHash.matches(stored, listOf(5, 8, 4, 0)))
    }

    @Test
    fun `the stored value never contains the dots and is salted`() {
        val a = PatternHash.create(pattern)
        val b = PatternHash.create(pattern)
        assertFalse(a.contains("0,4,8,5") || a.contains("0485"))
        assertNotEquals(a, b)
    }

    @Test
    fun `unreadable stored values never match`() {
        listOf("", "0485", "v1:only-two", "v2:AAAA:AAAA", "v1:!!!:###").forEach {
            assertFalse(it, PatternHash.matches(it, pattern))
        }
    }
}

class HiddenAppsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo() = HiddenAppsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("privacy.preferences_pb") },
    )

    @Test
    fun `starts with no pattern and nothing hidden`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        assertFalse(repo.hasPattern.first())
        assertEquals(emptySet<String>(), repo.hiddenPackages.first())
        assertFalse(repo.checkPattern(listOf(0, 1, 2, 3)))
    }

    @Test
    fun `a saved pattern is checked`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.setPattern(listOf(0, 1, 2, 5))
        assertTrue(repo.hasPattern.first())
        assertTrue(repo.checkPattern(listOf(0, 1, 2, 5)))
        assertFalse(repo.checkPattern(listOf(0, 1, 2, 4)))
    }

    @Test
    fun `packages are hidden and shown again`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.setHidden("com.a", true)
        repo.setHidden("com.b", true)
        repo.setHidden("com.a", false)
        assertEquals(setOf("com.b"), repo.hiddenPackages.first())
    }
}

class HomeLayoutHideTest {

    @Test
    fun `hiding a package removes all its shortcuts and nothing else`() {
        val layout = HomeLayout(
            shortcuts = mapOf(
                Cell(0, 0, 0) to "com.a/.Main",
                Cell(0, 0, 1) to "com.a/.Other",
                Cell(0, 0, 2) to "com.ab/.Main",
            ),
        )
        assertEquals(mapOf(Cell(0, 0, 2) to "com.ab/.Main"), layout.withoutPackage("com.a").shortcuts)
    }
}
