package dev.rohitshukla.pixellauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.Cell
import dev.rohitshukla.pixellauncher.data.GridSize
import dev.rohitshukla.pixellauncher.data.HomeLayout
import dev.rohitshukla.pixellauncher.data.IconCache

/** What the home screen needs to show and start drags. LauncherScreen owns the state. */
class HomeDragUi(
    /** True while any app is being dragged: empty cells show a hint. */
    val active: Boolean,
    /** The cell whose app is being dragged; it is hidden while it moves. */
    val from: Cell?,
    val callbacksFor: (AppEntry, Cell) -> DragCallbacks,
)

/**
 * Home pages, page dots and the dock, top to bottom. As in Yomo, the clock (and the search bar
 * when on) is [header], drawn in the top rows of page 1 only.
 */
@Composable
fun HomeScreen(
    searchBarEnabled: Boolean,
    header: @Composable () -> Unit,
    layout: HomeLayout,
    appsByKey: Map<String, AppEntry>,
    dock: List<AppEntry>,
    icons: IconCache,
    pagerState: PagerState,
    drag: HomeDragUi,
    menuFor: (AppEntry) -> AppMenu,
    onPagerPlaced: (bounds: Rect) -> Unit,
    onGrid: (GridSize) -> Unit,
    controller: LauncherController,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val grid = GridSize.fit(maxWidth.value, maxHeight.value, searchBarEnabled)
            HorizontalPager(
                state = pagerState,
                // Every page stays composed, so a drag that started on another page is not cut off.
                beyondViewportPageCount = HomeLayout.MAX_PAGES,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { onPagerPlaced(it.boundsInRoot()) },
            ) { page ->
                HomePage(page, grid, layout, appsByKey, icons, drag, controller, header)
            }
            LaunchedEffect(grid) { onGrid(grid) }
        }

        if (pagerState.pageCount > 1) PageDots(pagerState)

        Dock(dock, icons, menuFor, controller)
    }
}

@Composable
private fun HomePage(
    page: Int,
    grid: GridSize,
    layout: HomeLayout,
    appsByKey: Map<String, AppEntry>,
    icons: IconCache,
    drag: HomeDragUi,
    controller: LauncherController,
    header: @Composable () -> Unit,
) {
    val headerRows = if (page == 0) grid.headerRows else 0
    Column(Modifier.fillMaxSize()) {
        if (headerRows > 0) Box(Modifier.weight(headerRows.toFloat()).fillMaxWidth()) { header() }
        for (row in headerRows until grid.rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(grid.columns) { column ->
                    val cell = Cell(page, row, column)
                    // An uninstalled app leaves its cell empty.
                    val app = layout.keyAt(cell)?.let(appsByKey::get)
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            app != null -> AppIcon(
                                app = app,
                                icons = icons,
                                onClick = { controller.launch(app, it) },
                                modifier = Modifier.alpha(if (drag.from == cell) 0f else 1f),
                                drag = drag.callbacksFor(app, cell),
                            )
                            drag.active -> EmptyCellHint()
                        }
                    }
                }
            }
        }
    }
}

/** Shown in free cells while dragging, as in Yomo, so you can see where an app can go. */
@Composable
private fun EmptyCellHint() {
    Box(
        Modifier
            .size(IconCache.ICON_SIZE_DP.dp)
            .background(Color.White.copy(alpha = 0.18f), CircleShape),
    )
}

@Composable
private fun PageDots(pagerState: PagerState) {
    val description = stringResource(R.string.page_indicator_description, pagerState.currentPage + 1, pagerState.pageCount)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pagerState.pageCount) { page ->
            val alpha = if (page == pagerState.currentPage) 1f else 0.4f
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(6.dp)
                    .background(Color.White.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

/** Long-press shows the app menu; dock apps are not dragged. */
@Composable
private fun Dock(dock: List<AppEntry>, icons: IconCache, menuFor: (AppEntry) -> AppMenu, controller: LauncherController) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (dock.isEmpty()) Spacer(Modifier.size(IconCache.ICON_SIZE_DP.dp))
        dock.forEach { app ->
            AppIcon(
                app = app,
                icons = icons,
                onClick = { controller.launch(app, it) },
                showLabel = false,
                menu = menuFor(app),
            )
        }
    }
}
