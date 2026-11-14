package dev.rohitshukla.pixellauncher.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.data.Pattern
import kotlinx.coroutines.delay

/**
 * A 3 x 3 pattern pad. Drag across dots to draw; lifting the finger calls [onComplete] with the
 * dots in order. The drawing stays on screen briefly ([error] colours it as wrong), then clears.
 */
@Composable
fun PatternPad(onComplete: (Pattern) -> Unit, error: Boolean, modifier: Modifier = Modifier) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var finished by remember { mutableStateOf(false) }
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        delay(CLEAR_AFTER_MS)
        selected.clear()
        finished = false
    }

    val dotColor = MaterialTheme.colorScheme.onSurface
    val lineColor = if (error && finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun add(dot: Int) {
                    if (dot in selected) return
                    // As standard Android patterns do: a straight line over an unselected dot selects it too.
                    val last = selected.lastOrNull()
                    if (last != null) {
                        val mid = midpointDot(last, dot)
                        if (mid != null && mid !in selected) selected += mid
                    }
                    selected += dot
                }
                fun hit(point: Offset) {
                    val dot = dotAt(point, Size(size.width.toFloat(), size.height.toFloat())) ?: return
                    add(dot)
                }
                detectDragGestures(
                    onDragStart = { start ->
                        selected.clear()
                        finished = false
                        finger = start
                        hit(start)
                    },
                    onDrag = { change, _ ->
                        finger = change.position
                        hit(change.position)
                    },
                    onDragEnd = {
                        finger = null
                        if (selected.isNotEmpty()) {
                            finished = true
                            currentOnComplete(selected.toList())
                        }
                    },
                    onDragCancel = {
                        finger = null
                        selected.clear()
                    },
                )
            },
    ) {
        val cell = size.width / 3
        val stroke = 6.dp.toPx()
        fun center(dot: Int) = Offset((dot % 3 + 0.5f) * cell, (dot / 3 + 0.5f) * cell)

        selected.zipWithNext().forEach { (a, b) ->
            drawLine(lineColor, center(a), center(b), stroke, StrokeCap.Round)
        }
        val last = selected.lastOrNull()
        val current = finger
        if (last != null && current != null) drawLine(lineColor, center(last), current, stroke, StrokeCap.Round)

        repeat(9) { dot ->
            val on = dot in selected
            drawCircle(if (on) lineColor else dotColor, radius = if (on) 12.dp.toPx() else 6.dp.toPx(), center = center(dot))
        }
    }
}

/**
 * The dot exactly between [a] and [b] on the 3x3 grid when the line crosses one (e.g. 0 to 2 crosses 1,
 * 0 to 8 crosses 4), else null.
 */
internal fun midpointDot(a: Int, b: Int): Int? {
    val ra = a / 3
    val ca = a % 3
    val rb = b / 3
    val cb = b % 3
    if ((ra + rb) % 2 != 0 || (ca + cb) % 2 != 0) return null
    val mid = (ra + rb) / 2 * 3 + (ca + cb) / 2
    return if (mid != a && mid != b) mid else null
}

/** The dot whose touch area contains [point], or null between dots. */
internal fun dotAt(point: Offset, size: Size): Int? {
    val cell = size.width / 3
    val column = (point.x / cell).toInt()
    val row = (point.y / cell).toInt()
    if (column !in 0..2 || row !in 0..2) return null
    val center = Offset((column + 0.5f) * cell, (row + 0.5f) * cell)
    return if ((point - center).getDistance() <= cell * HIT_RADIUS) row * 3 + column else null
}

private const val HIT_RADIUS = 0.35f
private const val CLEAR_AFTER_MS = 500L
