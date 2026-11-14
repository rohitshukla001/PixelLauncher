package dev.rohitshukla.pixellauncher.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the photo is drawn on the crop screen: its top-left corner and how much it is scaled. */
data class CropView(val left: Float, val top: Float, val scale: Float)

/**
 * The crop screen's geometry. A frame shaped like the phone's screen stays fixed; the photo moves
 * and zooms behind it and always covers it, so the crop never has empty edges.
 */
object CropMath {

    const val MAX_ZOOM = 5f

    /** The largest screen-shaped frame that fits [box] with [margin] around it, centred. */
    fun frame(box: Size, aspect: Float, margin: Float): Rect {
        val width = box.width - 2 * margin
        val height = box.height - 2 * margin
        if (width <= 0f || height <= 0f || aspect <= 0f) return Rect.Zero
        val (w, h) = if (width / height > aspect) height * aspect to height else width to width / aspect
        val left = (box.width - w) / 2
        val top = (box.height - h) / 2
        return Rect(left, top, left + w, top + h)
    }

    /** The photo just covering the frame, centred: where cropping starts. */
    fun cover(image: Size, frame: Rect): CropView {
        val scale = minScale(image, frame)
        return clamp(CropView(frame.center.x - image.width * scale / 2, frame.center.y - image.height * scale / 2, scale), image, frame)
    }

    /** Applies a drag ([pan]) and pinch ([zoom] around [centroid]), keeping the frame covered. */
    fun transform(view: CropView, centroid: Offset, pan: Offset, zoom: Float, image: Size, frame: Rect): CropView {
        val min = minScale(image, frame)
        val scale = (view.scale * zoom).coerceIn(min, min * MAX_ZOOM)
        val ratio = scale / view.scale
        val left = centroid.x - (centroid.x - view.left) * ratio + pan.x
        val top = centroid.y - (centroid.y - view.top) * ratio + pan.y
        return clamp(CropView(left, top, scale), image, frame)
    }

    /** The part of the photo, in its own pixels, that the frame shows. */
    fun sourceRect(view: CropView, frame: Rect, image: Size): IntRect {
        val imageWidth = image.width.toInt()
        val imageHeight = image.height.toInt()
        val left = ((frame.left - view.left) / view.scale).roundToInt().coerceIn(0, imageWidth - 1)
        val top = ((frame.top - view.top) / view.scale).roundToInt().coerceIn(0, imageHeight - 1)
        val width = (frame.width / view.scale).roundToInt().coerceIn(1, imageWidth - left)
        val height = (frame.height / view.scale).roundToInt().coerceIn(1, imageHeight - top)
        return IntRect(left, top, left + width, top + height)
    }

    private fun minScale(image: Size, frame: Rect) = max(frame.width / image.width, frame.height / image.height)

    private fun clamp(view: CropView, image: Size, frame: Rect): CropView {
        val width = image.width * view.scale
        val height = image.height * view.scale
        return view.copy(
            left = view.left.coerceIn(frame.right - width, frame.left),
            top = view.top.coerceIn(frame.bottom - height, frame.top),
        )
    }
}
