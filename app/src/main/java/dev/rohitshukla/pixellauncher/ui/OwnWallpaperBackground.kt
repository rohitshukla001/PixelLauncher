package dev.rohitshukla.pixellauncher.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.rohitshukla.pixellauncher.data.OwnWallpaper
import dev.rohitshukla.pixellauncher.wallpaper.BuiltInWallpapers
import dev.rohitshukla.pixellauncher.wallpaper.PhotoWallpapers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The wallpaper the launcher draws itself on phones that block wallpaper changes, as Yomo does:
 * full screen behind home, cropped to fill. Nothing when the system wallpaper is in use.
 */
@Composable
fun OwnWallpaperBackground(wallpaper: OwnWallpaper?) {
    if (wallpaper == null) return
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        var image by remember(wallpaper) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(wallpaper, width, height) {
            image = withContext(Dispatchers.Default) { load(context, wallpaper, width, height) }
        }
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun load(context: Context, wallpaper: OwnWallpaper, width: Int, height: Int): ImageBitmap? = when (wallpaper) {
    is OwnWallpaper.BuiltIn -> BuiltInWallpapers.render(wallpaper.index, width, height).asImageBitmap()
    is OwnWallpaper.Photo -> PhotoWallpapers.load(context, wallpaper.asset, width, height)?.asImageBitmap()
    is OwnWallpaper.Custom -> BitmapFactory.decodeFile(File(context.filesDir, OwnWallpaper.FILE_NAME).path)?.asImageBitmap()
}
