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

class UsagePruneTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo() = UsageRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("usage.preferences_pb") },
    )

    @Test
    fun `prune drops counts for apps no longer installed`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.recordLaunch("keep/K")
        repo.recordLaunch("gone/G")
        repo.prune(setOf("keep/K"))
        assertEquals(mapOf("keep/K" to 1), repo.launchCounts.first())
    }
}
