package dev.rohitshukla.pixellauncher.ui

import android.graphics.Rect as AndroidRect
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.AppRepository
import dev.rohitshukla.pixellauncher.data.Cell
import dev.rohitshukla.pixellauncher.data.ClockAlignment
import dev.rohitshukla.pixellauncher.data.GridSize
import dev.rohitshukla.pixellauncher.data.HomeLayout
import dev.rohitshukla.pixellauncher.data.IconCache
import dev.rohitshukla.pixellauncher.data.LauncherSettings
import dev.rohitshukla.pixellauncher.data.SearchEngine
import dev.rohitshukla.pixellauncher.data.Suggestions
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Everything the screens ask the launcher to do. MainActivity implements it. */
interface LauncherController {
    fun launch(app: AppEntry, sourceBounds: AndroidRect)
    fun openAppInfo(app: AppEntry, sourceBounds: AndroidRect)
    fun uninstall(app: AppEntry)
    fun placeShortcut(app: AppEntry, cell: Cell)
    fun moveShortcut(app: AppEntry, from: Cell, to: Cell)
    fun removeShortcut(app: AppEntry, cell: Cell)

    /** False at the page limit. */
    suspend fun addPage(): Boolean
    fun fitHome(grid: GridSize)
    fun openClock()
    fun openSearch(query: String?)
    fun setClockAlignment(alignment: ClockAlignment)
    fun setSearchEngine(engine: SearchEngine)
}

private enum class HomeDialog { ClockAlignment, SearchEngine }

/** Home screen, with the app drawer sliding up over it, and drag-and-drop between them. */
@Composable
fun LauncherScreen(
    apps: AppRepository.State,
    layout: HomeLayout,
    launchCounts: Map<String, Int>,
    hiddenPackages: Set<String>,
    settings: LauncherSettings,
    icons: IconCache,
    homePresses: Int,
    controller: LauncherController,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    // Set when a drag from the drawer puts it away, so it goes without its closing animation.
    var drawerClosesInstantly by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var pagerBounds by remember { mutableStateOf(Rect.Zero) }
    var grid by remember { mutableStateOf<GridSize?>(null) }
    var removeBounds by remember { mutableStateOf(Rect.Zero) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    val pagerState = rememberPagerState { layout.pageCount }
    // Hidden apps show nowhere in the launcher: not on home, in the dock, the drawer, search or Suggested.
    val visibleApps = remember(apps.apps, hiddenPackages) { apps.apps.filter { it.packageName !in hiddenPackages } }
    val visibleDock = remember(apps.dock, hiddenPackages) { apps.dock.filter { it.packageName !in hiddenPackages } }
    val appsByKey = remember(visibleApps) { visibleApps.associateBy { it.key } }
    val suggested = remember(launchCounts, visibleApps, settings.suggestionsEnabled) {
        if (settings.suggestionsEnabled) Suggestions.mostUsed(launchCounts, visibleApps) else emptyList()
    }
    val density = LocalDensity.current
    val openAfterPx = with(density) { OPEN_SWIPE.toPx() }
    val edgePx = with(density) { PAGE_EDGE.toPx() }

    val dragging = drag != null
    val draggingFromHome = drag?.from != null
    val draggingFromDrawer = dragging && !draggingFromHome

    // Home while already home: close the drawer and go back to the first page.
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        drawerOpen = false
        pagerState.animateScrollToPage(0)
    }

    // Shortcuts outside a smaller grid move to free cells. Waits for the app list, so cells of
    // uninstalled apps are freed first.
    LaunchedEffect(grid, apps.loaded) {
        val g = grid ?: return@LaunchedEffect
        if (apps.loaded) controller.fitHome(g)
    }

    // Holding a dragged app at the pager's edge flips the page; past the last page it adds one.
    // A page is added at most once per hold: leave the edge and come back to add another.
    LaunchedEffect(dragging) {
        var edge = 0
        var since = 0L
        var addedThisHold = false
        while (true) {
            val d = drag ?: break
            val now = SystemClock.uptimeMillis()
            val current = edgeAt(d.position, pagerBounds, edgePx)
            if (current != edge) {
                edge = current
                since = now
                addedThisHold = false
            } else if (edge != 0 && now - since >= EDGE_HOLD_MS) {
                if (flipPage(pagerState, edge, controller, canAddPage = !addedThisHold) == Flip.Added) addedThisHold = true
                since = SystemClock.uptimeMillis()
            }
            delay(EDGE_POLL_MS)
        }
    }

    fun drop(d: DragSession) {
        val from = d.from
        if (from != null && removeBounds.contains(d.position)) {
            controller.removeShortcut(d.app, from)
            return
        }
        val cell = grid?.let { cellAt(d.position, pagerBounds, it, pagerState.currentPage) } ?: return
        if (from == null) controller.placeShortcut(d.app, cell) else controller.moveShortcut(d.app, from, cell)
    }

    fun dragCallbacks(app: AppEntry, from: Cell?) = DragCallbacks(
        onStart = { position ->
            drag = DragSession(app, from, position)
            if (from == null) {
                drawerClosesInstantly = true
                drawerOpen = false
            }
        },
        onMove = { position -> drag = drag?.copy(position = position) },
        onEnd = {
            drag?.let(::drop)
            drag = null
        },
        onCancel = { drag = null },
    )

    fun menuFor(app: AppEntry) = AppMenu(
        onAppInfo = { controller.openAppInfo(app, it) },
        onUninstall = if (app.isSystem || app.isSelf) null else ({ controller.uninstall(app) }),
    )

    // Back never leaves the launcher. The drawer's handler below takes precedence while it is open.
    BackHandler {}
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(openAfterPx) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, dy -> dragged += dy },
                    onDragEnd = {
                        if (dragged < -openAfterPx) {
                            drawerClosesInstantly = false
                            drawerOpen = true
                        }
                    },
                )
            },
    ) {
        OwnWallpaperBackground(settings.ownWallpaper)

        HomeScreen(
            searchBarEnabled = settings.searchBarEnabled,
            header = {
                HomeHeader(
                    settings = settings,
                    onClock = controller::openClock,
                    onClockLongPress = { dialog = HomeDialog.ClockAlignment },
                    onSearch = { controller.openSearch(null) },
                    onSearchLongPress = { dialog = HomeDialog.SearchEngine },
                )
            },
            layout = layout,
            appsByKey = appsByKey,
            dock = visibleDock,
            icons = icons,
            pagerState = pagerState,
            drag = HomeDragUi(active = dragging, from = drag?.from, callbacksFor = ::dragCallbacks),
            menuFor = ::menuFor,
            onPagerPlaced = { pagerBounds = it },
            onGrid = { grid = it },
            controller = controller,
            modifier = Modifier.systemBarsPadding(),
        )

        // Stays composed, invisible, during a drag that started in it: the drag's touch handling lives there.
        AnimatedVisibility(
            visible = drawerOpen || draggingFromDrawer,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = if (drawerClosesInstantly) ExitTransition.None else slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                apps = visibleApps,
                suggested = suggested,
                icons = icons,
                searchEngine = settings.searchEngine,
                menuFor = ::menuFor,
                dragFor = { dragCallbacks(it, from = null) },
                controller = controller,
                onClose = { drawerOpen = false },
                modifier = Modifier.graphicsLayer { alpha = if (draggingFromDrawer) 0f else 1f },
            )
        }

        if (draggingFromHome) {
            RemoveArea(
                hovered = drag?.let { removeBounds.contains(it.position) } == true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .onGloballyPositioned { removeBounds = it.boundsInRoot() },
            )
        }

        drag?.let { DragShadow(it, icons) }
    }

    when (dialog) {
        HomeDialog.ClockAlignment -> ChoiceDialog(
            title = stringResource(R.string.set_alignment),
            options = ClockAlignment.entries,
            selected = settings.clockAlignment,
            label = { alignmentLabel(it) },
            onPick = {
                controller.setClockAlignment(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        HomeDialog.SearchEngine -> ChoiceDialog(
            title = stringResource(R.string.set_search_engine),
            options = SearchEngine.entries,
            selected = settings.searchEngine,
            label = { it.label },
            onPick = {
                controller.setSearchEngine(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** -1 near the left edge, 1 near the right edge, 0 elsewhere or outside the pager. */
private fun edgeAt(point: Offset, pager: Rect, edgePx: Float): Int = when {
    pager.isEmpty || point.y !in pager.top..pager.bottom -> 0
    point.x < pager.left + edgePx -> -1
    point.x > pager.right - edgePx -> 1
    else -> 0
}

private enum class Flip { Moved, Added, None }

private suspend fun flipPage(
    pagerState: PagerState,
    direction: Int,
    controller: LauncherController,
    canAddPage: Boolean,
): Flip {
    val target = pagerState.currentPage + direction
    return when {
        target in 0 until pagerState.pageCount -> {
            pagerState.animateScrollToPage(target)
            Flip.Moved
        }
        target == pagerState.pageCount && canAddPage && controller.addPage() -> {
            // Wait for the new page to reach the pager, but don't block the edge loop forever if it lags.
            withTimeoutOrNull(NEW_PAGE_TIMEOUT_MS) { snapshotFlow { pagerState.pageCount }.first { it > target } }
            if (pagerState.pageCount > target) pagerState.animateScrollToPage(target)
            Flip.Added
        }
        else -> Flip.None
    }
}

/** Drop a home app here to remove it, as in Yomo. */
@Composable
private fun RemoveArea(hovered: Boolean, modifier: Modifier) {
    Row(
        modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(
                if (hovered) MaterialTheme.colorScheme.errorContainer else Color.Black.copy(alpha = 0.6f),
                RoundedCornerShape(28.dp),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White)
        Text(
            text = stringResource(R.string.remove),
            modifier = Modifier.padding(start = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The app's icon under the finger. */
@Composable
private fun DragShadow(drag: DragSession, icons: IconCache) {
    var icon by remember(drag.app.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(drag.app.key) { icon = icons.icon(drag.app) }
    val half = with(LocalDensity.current) { (IconCache.ICON_SIZE_DP.dp / 2).toPx() }
    icon?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset((drag.position.x - half).roundToInt(), (drag.position.y - half).roundToInt()) }
                .size(IconCache.ICON_SIZE_DP.dp)
                .graphicsLayer { alpha = 0.9f },
        )
    }
}

private val OPEN_SWIPE = 64.dp
private val PAGE_EDGE = 32.dp
private const val EDGE_HOLD_MS = 500L
private const val NEW_PAGE_TIMEOUT_MS = 1000L
private const val EDGE_POLL_MS = 50L
