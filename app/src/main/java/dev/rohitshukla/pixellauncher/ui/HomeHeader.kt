package dev.rohitshukla.pixellauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.ClockAlignment
import dev.rohitshukla.pixellauncher.data.LauncherSettings

/** Page 1's top rows: the clock, and the search bar when it is on (as in Yomo). */
@Composable
fun HomeHeader(
    settings: LauncherSettings,
    onClock: () -> Unit,
    onClockLongPress: () -> Unit,
    onSearch: () -> Unit,
    onSearchLongPress: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Clock(
            alignment = settings.clockAlignment,
            onClick = onClock,
            onLongClick = onClockLongPress,
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings.searchBarEnabled) SearchBar(onSearch, onSearchLongPress)
    }
}

/** Tap to search with the chosen engine; long-press is Yomo's "Set Search Engine". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchBar(onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.DarkGray)
        Text(stringResource(R.string.search), color = Color.DarkGray, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A single-choice dialog; picking an option applies it and closes, as Yomo's dialogs do. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onPick(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
    )
}

@Composable
fun alignmentLabel(alignment: ClockAlignment): String = stringResource(
    when (alignment) {
        ClockAlignment.Left -> R.string.align_left
        ClockAlignment.Center -> R.string.align_center
        ClockAlignment.Right -> R.string.align_right
    },
)
