package dev.rohitshukla.pixellauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DockAppsTest {

    private val dialer = AppEntry("Phone", "com.phone", "Main")
    private val chrome = AppEntry("Chrome", "com.chrome", "Main")
    private val camera = AppEntry("Camera", "com.camera", "Main")
    private val apps = listOf(camera, chrome, dialer)

    @Test
    fun `keeps the order of the default handlers`() {
        assertEquals(listOf(dialer, chrome, camera), DockApps.pick(listOf("com.phone", "com.chrome", "com.camera"), apps))
    }

    @Test
    fun `skips handlers that are missing or not launchable`() {
        // null: no default set. "android": the system chooser, which has no launcher entry.
        assertEquals(listOf(dialer, camera), DockApps.pick(listOf("com.phone", null, "android", "com.camera"), apps))
    }

    @Test
    fun `an app that handles two roles shows once`() {
        assertEquals(listOf(chrome), DockApps.pick(listOf("com.chrome", "com.chrome"), apps))
    }
}
