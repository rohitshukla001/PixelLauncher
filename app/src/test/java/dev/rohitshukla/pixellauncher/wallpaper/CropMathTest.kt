package dev.rohitshukla.pixellauncher.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {

    // A 1:2 screen frame, 400 x 800 at (100, 100), inside a 600 x 1000 box with a 100 px margin.
    private val frame = CropMath.frame(Size(600f, 1000f), aspect = 0.5f, margin = 100f)
    private val photo = Size(2000f, 2000f)

    @Test
    fun `the frame is the largest screen shape that fits, centred`() {
        assertEquals(Rect(100f, 100f, 500f, 900f), CropMath.frame(Size(600f, 1000f), 0.5f, 100f))
        assertEquals(Rect(50f, 0f, 550f, 1000f), CropMath.frame(Size(600f, 1000f), 0.5f, 0f))
        assertEquals(Rect(150f, 150f, 450f, 750f), CropMath.frame(Size(600f, 900f), 0.5f, 150f))
        assertEquals(Rect.Zero, CropMath.frame(Size(100f, 100f), 0.5f, 60f))
    }

    @Test
    fun `cropping starts with the photo just covering the frame, centred`() {
        // A square photo covers the tall frame when its height matches: scale 0.4, 800 x 800.
        assertEquals(CropView(left = -100f, top = 100f, scale = 0.4f), CropMath.cover(photo, frame))
    }

    @Test
    fun `dragging stops where the photo would stop covering the frame`() {
        val start = CropMath.cover(photo, frame)
        assertEquals(100f, CropMath.transform(start, Offset.Zero, Offset(500f, 0f), 1f, photo, frame).left)
        assertEquals(-300f, CropMath.transform(start, Offset.Zero, Offset(-500f, 0f), 1f, photo, frame).left)
        assertEquals(100f, CropMath.transform(start, Offset.Zero, Offset(0f, 300f), 1f, photo, frame).top)
    }

    @Test
    fun `zoom stays between covering and five times that`() {
        val start = CropMath.cover(photo, frame)
        assertEquals(0.4f, CropMath.transform(start, frame.center, Offset.Zero, 0.5f, photo, frame).scale)
        assertEquals(2f, CropMath.transform(start, frame.center, Offset.Zero, 10f, photo, frame).scale, 0.0001f)
    }

    @Test
    fun `zooming keeps the point under the fingers still`() {
        val start = CropMath.cover(photo, frame)
        val zoomed = CropMath.transform(start, frame.center, Offset.Zero, 2f, photo, frame)
        // The frame's centre showed photo pixel (1000, 1000) before; it still does.
        assertEquals(1000f, (frame.center.x - zoomed.left) / zoomed.scale, 0.01f)
        assertEquals(1000f, (frame.center.y - zoomed.top) / zoomed.scale, 0.01f)
    }

    @Test
    fun `the source area is the part of the photo inside the frame`() {
        // Start: 800 x 800 shown at (-100, 100); the 400 x 800 frame shows the middle half's width.
        assertEquals(IntRect(500, 0, 1500, 2000), CropMath.sourceRect(CropMath.cover(photo, frame), frame, photo))
    }
}
