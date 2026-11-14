package dev.rohitshukla.pixellauncher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Eight original wallpapers, drawn in code at any size: no image files, so nothing to license.
 * The same drawing makes the small previews and the full-screen wallpaper.
 */
object BuiltInWallpapers {

    const val COUNT = 8

    fun render(index: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        when (index) {
            0 -> dusk(canvas, w, h)
            1 -> ocean(canvas, w, h)
            2 -> forest(canvas, w, h)
            3 -> aurora(canvas, w, h)
            4 -> dunes(canvas, w, h)
            5 -> night(canvas, w, h)
            6 -> bloom(canvas, w, h)
            else -> facets(canvas, w, h)
        }
        return bitmap
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun sky(canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int) {
        canvas.drawRect(0f, 0f, w, h, paint().apply { shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP) })
    }

    /** A filled band whose top edge is a sine wave. */
    private fun wave(canvas: Canvas, w: Float, h: Float, baseline: Float, amplitude: Float, waves: Float, phase: Float, color: Int) {
        val path = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                lineTo(x, baseline + amplitude * sin((x / w * waves * 2 * PI + phase).toFloat()))
                x += w / 60
            }
            lineTo(w, h)
            close()
        }
        canvas.drawPath(path, paint().apply { this.color = color })
    }

    private fun dusk(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFF2B1B4D.toInt(), 0xFFF28C6B.toInt())
        canvas.drawCircle(w * 0.5f, h * 0.62f, w * 0.28f, paint().apply { color = 0xFFFFD39A.toInt() })
        wave(canvas, w, h, h * 0.72f, h * 0.012f, 1.5f, 0f, 0xFF5A2E5E.toInt())
        wave(canvas, w, h, h * 0.8f, h * 0.015f, 2f, 1f, 0xFF3A1E45.toInt())
    }

    private fun ocean(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFF0B2545.toInt(), 0xFF13315C.toInt())
        val colors = intArrayOf(0xFF1B4F72.toInt(), 0xFF1F6F8B.toInt(), 0xFF2A9D8F.toInt(), 0xFF8DD3C7.toInt())
        colors.forEachIndexed { i, c -> wave(canvas, w, h, h * (0.45f + i * 0.12f), h * 0.02f, 1.2f + i * 0.3f, i * 1.3f, c) }
    }

    private fun forest(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFFB7D3C2.toInt(), 0xFFE8F1E4.toInt())
        val colors = intArrayOf(0xFF7FA88E.toInt(), 0xFF4F7F64.toInt(), 0xFF2E5A44.toInt(), 0xFF173A2B.toInt())
        colors.forEachIndexed { i, c -> wave(canvas, w, h, h * (0.4f + i * 0.13f), h * 0.05f, 0.8f + i * 0.2f, i * 2f, c) }
    }

    private fun aurora(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFF050B1F.toInt(), 0xFF101C3D.toInt())
        val bands = intArrayOf(0x8822D3A6.toInt(), 0x667B5CFF, 0x5533B5FF)
        bands.forEachIndexed { i, c ->
            val y = h * (0.25f + i * 0.12f)
            // Fades out at the top and bottom, so the bands have no hard edges.
            canvas.drawRect(0f, y - h * 0.15f, w, y + h * 0.15f, paint().apply {
                shader = LinearGradient(0f, y - h * 0.15f, 0f, y + h * 0.15f, intArrayOf(0, c, 0), null, Shader.TileMode.CLAMP)
            })
        }
        stars(canvas, w, h, 60, seed = 3)
    }

    private fun dunes(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFFFBE3C2.toInt(), 0xFFF6B884.toInt())
        val colors = intArrayOf(0xFFE9A46A.toInt(), 0xFFD9824B.toInt(), 0xFFB9633A.toInt(), 0xFF8E4A2C.toInt())
        colors.forEachIndexed { i, c -> wave(canvas, w, h, h * (0.5f + i * 0.11f), h * 0.04f, 0.6f + i * 0.15f, i * 0.9f, c) }
    }

    private fun night(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFF000000.toInt(), 0xFF141A2E.toInt())
        stars(canvas, w, h, 180, seed = 7)
        canvas.drawCircle(w * 0.72f, h * 0.22f, w * 0.09f, paint().apply { color = 0xFFF2F0E6.toInt() })
    }

    private fun bloom(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, paint().apply {
            shader = RadialGradient(w * 0.3f, h * 0.3f, max(w, h), 0xFFF7A1C4.toInt(), 0xFF5B2A86.toInt(), Shader.TileMode.CLAMP)
        })
        canvas.drawCircle(w * 0.8f, h * 0.75f, w * 0.5f, paint().apply { color = 0x33FFFFFF })
        canvas.drawCircle(w * 0.15f, h * 0.9f, w * 0.3f, paint().apply { color = 0x22FFFFFF })
    }

    private fun facets(canvas: Canvas, w: Float, h: Float) {
        sky(canvas, w, h, 0xFF3D4F63.toInt(), 0xFF1E2833.toInt())
        val random = Random(11)
        val columns = 4
        val rows = 8
        val cw = w / columns
        val ch = h / rows
        for (r in 0 until rows) for (c in 0 until columns) {
            val x = c * cw
            val y = r * ch
            val shade = 0x40 + random.nextInt(0x50)
            val color = (0x30 shl 24) or (shade shl 16) or (shade + 0x10 shl 8) or (shade + 0x20)
            val path = Path().apply {
                if (random.nextBoolean()) { moveTo(x, y); lineTo(x + cw, y); lineTo(x, y + ch) } else { moveTo(x + cw, y); lineTo(x + cw, y + ch); lineTo(x, y + ch) }
                close()
            }
            canvas.drawPath(path, paint().apply { this.color = color })
        }
    }

    private fun stars(canvas: Canvas, w: Float, h: Float, count: Int, seed: Int) {
        val random = Random(seed)
        val p = paint().apply { color = 0xCCFFFFFF.toInt() }
        repeat(count) { canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h * 0.8f, (0.5f + random.nextFloat() * 1.5f) * w / 400f, p) }
    }
}
