package dev.rohitshukla.pixellauncher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.Cell
import dev.rohitshukla.pixellauncher.data.GridSize

/**
 * One drag in progress. [from] is the home cell it started on; [fromDock] is its dock index; both
 * null when it comes from the drawer.
 */
data class DragSession(val app: AppEntry, val from: Cell?, val fromDock: Int?, val position: Offset)

/** Drag events, with positions in root coordinates. */
class DragCallbacks(
    val onStart: (Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

/** The home cell under [point], or null outside the pager or over page 1's clock rows. */
fun cellAt(point: Offset, pagerBounds: Rect, grid: GridSize, page: Int): Cell? {
    if (pagerBounds.isEmpty || !pagerBounds.contains(point)) return null
    val column = ((point.x - pagerBounds.left) / (pagerBounds.width / grid.columns)).toInt()
    val row = ((point.y - pagerBounds.top) / (pagerBounds.height / grid.rows)).toInt()
    val cell = Cell(page, row.coerceIn(0, grid.rows - 1), column.coerceIn(0, grid.columns - 1))
    // The clock and search bar rows on page 1 take no apps.
    return cell.takeIf(grid::contains)
}

/**
 * Tap, long-press, and a drag that continues from the long-press without lifting the finger.
 * Moving before the long-press fires is left to the parent (scrolling the pager or the drawer).
 */
internal suspend fun PointerInputScope.detectAppGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    drag: () -> DragCallbacks?,
    toRoot: (Offset) -> Offset,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var tapped = false
    val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                tapped = !change.isConsumed
                if (tapped) change.consume()
                break
            }
            if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
        }
        false
    } == null

    if (!longPressed) {
        if (tapped) onTap()
        return@awaitEachGesture
    }

    onLongPress()
    val callbacks = drag() ?: return@awaitEachGesture
    var dragging = false
    var ended = false
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                if (dragging) {
                    ended = true
                    callbacks.onEnd()
                }
                break
            }
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                callbacks.onStart(toRoot(change.position))
            }
            if (dragging) {
                change.consume()
                callbacks.onMove(toRoot(change.position))
            }
        }
    } finally {
        // The finger left in a way that is not a drop (gesture cancelled): the drag is abandoned.
        if (dragging && !ended) callbacks.onCancel()
    }
}
