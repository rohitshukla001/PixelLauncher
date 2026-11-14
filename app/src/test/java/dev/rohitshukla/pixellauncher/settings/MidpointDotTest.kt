package dev.rohitshukla.pixellauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MidpointDotTest {

    @Test
    fun `a straight line across one dot returns the crossed dot`() {
        assertEquals(1, midpointDot(0, 2)) // top row
        assertEquals(3, midpointDot(0, 6)) // left column
        assertEquals(4, midpointDot(0, 8)) // main diagonal
        assertEquals(4, midpointDot(2, 6)) // anti-diagonal
        assertEquals(7, midpointDot(6, 8)) // bottom row
        assertEquals(5, midpointDot(2, 8)) // right column
    }

    @Test
    fun `adjacent or knight-move dots cross nothing`() {
        assertNull(midpointDot(0, 1)) // adjacent
        assertNull(midpointDot(0, 4)) // adjacent diagonal
        assertNull(midpointDot(0, 5)) // knight move
        assertNull(midpointDot(1, 6)) // knight move
    }
}
