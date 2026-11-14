package dev.rohitshukla.pixellauncher.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleSizeTest {

    @Test
    fun `shrinks big photos by powers of two but never below the screen`() {
        assertEquals(1, sampleSize(1080, 2400, 1080, 2400))
        assertEquals(2, sampleSize(4000, 6000, 1080, 2400))
        assertEquals(4, sampleSize(6000, 12000, 1080, 2400))
    }

    @Test
    fun `small photos are not shrunk`() {
        assertEquals(1, sampleSize(500, 800, 1080, 2400))
    }
}
