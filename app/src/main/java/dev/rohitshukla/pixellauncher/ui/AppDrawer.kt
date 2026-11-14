package dev.rohitshukla.pixellauncher.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.AppSearch
import dev.rohitshukla.pixellauncher.data.IconCache
import dev.rohitshukla.pixellauncher.data.SearchEngine

/**
 * Suggested (most-used) apps, then all apps A–Z, with a search field. Pulling down from the top of
 * the list closes it.
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    suggested: List<AppEntry>,
    icons: IconCache,
    searchEngine: SearchEngine,
    menuFor: (AppEntry) -> AppMenu,
    dragFor: (AppEntry) -> DragCallbacks,
    controller: LauncherController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(apps, query) { AppSearch.filter(apps, query) }
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current
    val closeAfterPx = with(LocalDensity.current) { CLOSE_PULL.toPx() }

    // A new query starts at the top of the results.
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    val pullToClose = remember(closeAfterPx) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Downward drag the list could not use: it is already at the top.
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > closeAfterPx) {
                        pulled = 0f
                        onClose()
                    }
                } else if (available.y < 0 || consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    fun open(app: AppEntry, bounds: Rect) {
        focus.clearFocus()
        controller.launch(app, bounds)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .safeDrawingPadding(),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            // Enter opens the top result, as in Yomo.
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { open(it, Rect()) } }),
        )

        if (results.isEmpty() && query.isNotBlank()) {
            NoResults(
                searchEngine = searchEngine,
                onSearchWeb = {
                    focus.clearFocus()
                    controller.openSearch(query.trim())
                },
            )
        } else {
            // Suggested shows only on the full list, not while searching.
            val showSuggested = query.isBlank() && suggested.isNotEmpty()
            LazyVerticalGrid(
                columns = GridCells.Fixed(DRAWER_COLUMNS),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToClose),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (showSuggested) {
                    sectionHeader(R.string.drawer_section_suggested)
                    // The same app also appears in All apps, so these keys get a prefix.
                    items(suggested, key = { "suggested:${it.key}" }) { app ->
                        DrawerIcon(app, icons, menuFor(app), dragFor(app), ::open)
                    }
                    sectionHeader(R.string.drawer_section_all_apps)
                }
                items(results, key = { it.key }) { app ->
                    DrawerIcon(app, icons, menuFor(app), dragFor(app), ::open)
                }
            }
        }
    }
}

/** Long-press shows the app menu; moving the finger then drags the app onto home, as in Yomo. */
@Composable
private fun DrawerIcon(
    app: AppEntry,
    icons: IconCache,
    menu: AppMenu,
    drag: DragCallbacks,
    open: (AppEntry, Rect) -> Unit,
) {
    AppIcon(
        app = app,
        icons = icons,
        onClick = { open(app, it) },
        menu = menu,
        drag = drag,
    )
}

private fun LazyGridScope.sectionHeader(title: Int) {
    item(key = "header:$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(title),
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Yomo's empty search: "No apps found", with "Search with <engine>" for the same words. */
@Composable
private fun NoResults(searchEngine: SearchEngine, onSearchWeb: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.drawer_no_search_results),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onSearchWeb) { Text(stringResource(R.string.search_with, searchEngine.label)) }
    }
}

private const val DRAWER_COLUMNS = 4
private val CLOSE_PULL = 96.dp
