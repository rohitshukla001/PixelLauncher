package dev.rohitshukla.pixellauncher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OwnWallpaperTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `choices round trip`() {
        listOf(OwnWallpaper.BuiltIn(3), OwnWallpaper.Custom(1_700_000_000_000)).forEach {
            assertEquals(it, OwnWallpaper.decode(OwnWallpaper.encode(it)))
        }
    }

    @Test
    fun `unreadable values mean the system wallpaper`() {
        listOf(null, "", "builtin", "builtin:x", "custom:", "photo:1").forEach { assertNull(it, OwnWallpaper.decode(it)) }
    }

    private fun TestScope.repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("settings.preferences_pb") },
    )

    @Test
    fun `the system wallpaper shows until the launcher has to draw one`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        assertNull(repo.settings.first().ownWallpaper)
        repo.setOwnWallpaper(OwnWallpaper.BuiltIn(2))
        assertEquals(OwnWallpaper.BuiltIn(2), repo.settings.first().ownWallpaper)
        repo.setOwnWallpaper(null)
        assertNull(repo.settings.first().ownWallpaper)
    }
}
