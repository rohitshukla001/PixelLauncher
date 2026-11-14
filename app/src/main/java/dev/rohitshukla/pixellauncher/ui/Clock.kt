package dev.rohitshukla.pixellauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.rohitshukla.pixellauncher.data.ClockAlignment
import dev.rohitshukla.pixellauncher.ui.theme.TextOnWallpaperShadow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Large time with the date under it. Follows the phone's 12/24-hour setting and locale. A tap opens
 * the clock app; a long-press is Yomo's "Set alignment".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Clock(alignment: ClockAlignment, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberNow()
    val is24h = DateFormat.is24HourFormat(context)
    val time = remember(locale, is24h) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm"), locale)
    }
    val date = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale)
    }

    val horizontal = when (alignment) {
        ClockAlignment.Left -> Alignment.Start
        ClockAlignment.Center -> Alignment.CenterHorizontally
        ClockAlignment.Right -> Alignment.End
    }
    Column(
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = horizontal,
    ) {
        Text(
            text = now.format(time),
            style = MaterialTheme.typography.displayLarge.copy(shadow = TextOnWallpaperShadow),
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = now.format(date),
            style = MaterialTheme.typography.titleMedium.copy(shadow = TextOnWallpaperShadow),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The current minute. Uses the system's minute tick rather than a timer, because timers pause
 * while the phone sleeps and the clock would show a stale time when the screen turns on.
 */
@Composable
private fun rememberNow(): LocalDateTime {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                now = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now = LocalDateTime.now()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}
