package dev.rohitshukla.pixellauncher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.Locale

/**
 * Bundled photo wallpapers: image files placed in the app's `assets/wallpapers/` folder. They are
 * listed automatically and shown in the chooser before the code-drawn ones. Add photos you own or
 * have the right to use; none are shipped by default.
 */
object PhotoWallpapers {

    private const val DIR = "wallpapers"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** File names in `assets/wallpapers/`, image files only, in alphabetical order. */
    fun names(context: Context): List<String> = try {
        context.assets.list(DIR).orEmpty()
            .filter { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
            .sorted()
    } catch (e: java.io.IOException) {
        emptyList()
    }

    /** Decodes a bundled photo, downsampled and centre-cropped to cover [width] x [height]. */
    fun load(context: Context, asset: String, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("$DIR/$asset").use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
            }
            val full = context.assets.open("$DIR/$asset").use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            centreCrop(full, width, height)
        } catch (e: java.io.IOException) {
            null
        }
    }

    /** Largest power-of-two shrink that keeps both sides at least the target. */
    private fun sampleSize(w: Int, h: Int, targetW: Int, targetH: Int): Int {
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2
        return sample
    }

    /** Scales to cover and crops the centre to exactly [width] x [height]. */
    private fun centreCrop(src: Bitmap, width: Int, height: Int): Bitmap {
        val scale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt().coerceAtLeast(width)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(height)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = (scaledW - width) / 2
        val y = (scaledH - height) / 2
        val out = Bitmap.createBitmap(scaled, x, y, width, height)
        if (scaled != out) scaled.recycle()
        if (src != scaled && src != out) src.recycle()
        return out
    }
}
