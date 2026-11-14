package dev.rohitshukla.pixellauncher.ui

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppEntry
import dev.rohitshukla.pixellauncher.data.IconCache
import dev.rohitshukla.pixellauncher.ui.theme.TextOnWallpaperShadow

/** The long-press menu, as in Yomo: App Info, and Uninstall unless it is a system app. */
class AppMenu(val onAppInfo: (sourceBounds: Rect) -> Unit, val onUninstall: (() -> Unit)?)

/**
 * Icon with an optional label. A tap opens the app. A long-press opens [menu] if given; moving the
 * finger after the long-press starts [drag] if given (and closes the menu).
 */
@Composable
fun AppIcon(
    app: AppEntry,
    icons: IconCache,
    onClick: (sourceBounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    menu: AppMenu? = null,
    drag: DragCallbacks? = null,
) {
    var icon by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.key) { icon = icons.icon(app) }
    var menuOpen by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentMenu by rememberUpdatedState(menu)
    val currentDrag by rememberUpdatedState(drag)

    fun bounds(): Rect = coords?.boundsInWindow()?.let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) } ?: Rect()
    val longPress = { if (currentMenu != null) menuOpen = true }

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .onGloballyPositioned { coords = it }
                .pointerInput(app.key) {
                    detectAppGestures(
                        onTap = { currentOnClick(bounds()) },
                        onLongPress = longPress,
                        drag = {
                            currentDrag?.let { d ->
                                DragCallbacks(
                                    onStart = { menuOpen = false; d.onStart(it) },
                                    onMove = d.onMove,
                                    onEnd = d.onEnd,
                                    onCancel = d.onCancel,
                                )
                            }
                        },
                        toRoot = { coords?.localToRoot(it) ?: Offset.Zero },
                    )
                }
                .semantics {
                    role = Role.Button
                    onClick(label = app.label) { currentOnClick(bounds()); true }
                    if (menu != null) onLongClick { longPress(); true }
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(IconCache.ICON_SIZE_DP.dp), contentAlignment = Alignment.Center) {
                icon?.let { Image(bitmap = it, contentDescription = if (showLabel) null else app.label) }
            }
            if (showLabel) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = TextOnWallpaperShadow),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        menu?.let { m ->
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                    Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_info)) },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        m.onAppInfo(bounds())
                    },
                )
                m.onUninstall?.let { uninstall ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.uninstall)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            uninstall()
                        },
                    )
                }
            }
        }
    }
}
