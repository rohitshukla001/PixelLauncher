package dev.rohitshukla.pixellauncher.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternPadTest {

    // 300 x 300 pad: dots at 50, 150 and 250 on each axis.
    private val size = Size(300f, 300f)

    @Test
    fun `dots are numbered left to right, top to bottom`() {
        assertEquals(0, dotAt(Offset(50f, 50f), size))
        assertEquals(4, dotAt(Offset(150f, 150f), size))
        assertEquals(8, dotAt(Offset(250f, 250f), size))
        assertEquals(5, dotAt(Offset(260f, 140f), size))
    }

    @Test
    fun `between dots and outside the pad hits nothing`() {
        assertNull(dotAt(Offset(100f, 100f), size))
        assertNull(dotAt(Offset(-5f, 50f), size))
        assertNull(dotAt(Offset(50f, 320f), size))
    }
}
