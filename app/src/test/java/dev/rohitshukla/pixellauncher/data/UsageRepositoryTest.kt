package dev.rohitshukla.pixellauncher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UsageRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo() = UsageRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("usage.preferences_pb") },
    )

    @Test
    fun `starts with no launches`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(emptyMap<String, Int>(), repo().launchCounts.first())
    }

    @Test
    fun `each launch adds one`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repeat(3) { repo.recordLaunch("com.a/com.a.Main") }
        repo.recordLaunch("com.b/.Main")
        assertEquals(mapOf("com.a/com.a.Main" to 3, "com.b/.Main" to 1), repo.launchCounts.first())
    }
}

class SuggestionsTest {

    private val apps = listOf("Alarm", "Books", "Camera", "Drive", "Email", "Files").map { AppEntry(it, "p.$it", "Main") }
    private fun key(label: String) = "p.$label/Main"
    private fun labels(counts: Map<String, Int>) = Suggestions.mostUsed(counts, apps).map { it.label }

    @Test
    fun `most launched first, at most four`() {
        val counts = mapOf(key("Alarm") to 1, key("Books") to 9, key("Camera") to 5, key("Drive") to 7, key("Email") to 3)
        assertEquals(listOf("Books", "Drive", "Camera", "Email"), labels(counts))
    }

    @Test
    fun `ties keep A to Z order`() {
        assertEquals(listOf("Camera", "Files"), labels(mapOf(key("Files") to 2, key("Camera") to 2)))
    }

    @Test
    fun `never launched and uninstalled apps are not suggested`() {
        assertEquals(listOf("Drive"), labels(mapOf(key("Drive") to 1, key("Alarm") to 0, "gone.app/Main" to 50)))
    }
}
