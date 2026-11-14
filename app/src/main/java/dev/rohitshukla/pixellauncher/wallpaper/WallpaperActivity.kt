package dev.rohitshukla.pixellauncher.wallpaper

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.io.File
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppData
import dev.rohitshukla.pixellauncher.data.OwnWallpaper
import dev.rohitshukla.pixellauncher.settings.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Yomo's wallpaper screens. First run: "Choose wallpaper" from the built-in ones, then Continue.
 * From Settings: the same choices plus "Set custom wallpaper" (pick a photo, crop it), then Submit.
 * Submit sets both the home screen and the lock screen wallpaper (Yomo sets home only). Phones that
 * block wallpaper changes still get it on home, drawn by the launcher; their lock screen cannot be
 * changed by any app (Yomo's FAQ says the same).
 */
class WallpaperActivity : ComponentActivity() {

    private var applying by mutableStateOf(false)

    /** Set after Submit on a phone that blocks wallpaper changes: offers the phone's own wallpaper app for the lock screen. */
    private var lockScreenOffer by mutableStateOf<Bitmap?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    WallpaperScreen(
                        firstRun = firstRun,
                        applying = applying,
                        screenWidth = screenWidth(),
                        screenHeight = screenHeight(),
                        onBuiltIn = { index ->
                            apply(firstRun, OwnWallpaper.BuiltIn(index)) { BuiltInWallpapers.render(index, screenWidth(), screenHeight()) }
                        },
                        onCustom = { make -> apply(firstRun, OwnWallpaper.Custom(System.currentTimeMillis()), make) },
                    )
                    lockScreenOffer?.let { bitmap ->
                        LockScreenOffer(
                            onSet = { openPhoneWallpaperApp(bitmap) },
                            onDismiss = ::finish,
                        )
                    }
                }
            }
        }
    }

    /**
     * Set the home and lock screen wallpaper when the phone allows it. When it doesn't (an admin or a
     * restriction blocks it, or the phone refuses), keep the choice and draw it behind home instead,
     * as Yomo does, so home still looks changed; the lock screen then stays as it is.
     */
    private fun apply(firstRun: Boolean, choice: OwnWallpaper, make: () -> Bitmap?) {
        if (applying) return
        applying = true
        lifecycleScope.launch {
            val settings = AppData.settings(this@WallpaperActivity)
            var error: Throwable? = null
            // The bitmap is kept when the launcher has to draw it, for the lock screen offer below.
            var drawnByLauncher: Bitmap? = null
            val done = withContext(Dispatchers.Default) {
                val bitmap = try {
                    make()
                } catch (e: Exception) {
                    error = e
                    null
                } ?: return@withContext false
                if (setSystemWallpaper(bitmap)) {
                    settings.setOwnWallpaper(null)
                    return@withContext true
                }
                try {
                    if (choice is OwnWallpaper.Custom) {
                        File(filesDir, OwnWallpaper.FILE_NAME).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    }
                    settings.setOwnWallpaper(choice)
                    drawnByLauncher = bitmap
                    true
                } catch (e: java.io.IOException) {
                    error = e
                    false
                }
            }
            applying = false
            if (done) {
                if (!firstRun) Toast.makeText(this@WallpaperActivity, R.string.wallpaper_changed, Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Changing the wallpaper failed", error)
                Toast.makeText(this@WallpaperActivity, getString(R.string.wallpaper_not_changed) + debugDetail(error), Toast.LENGTH_LONG).show()
            }
            // First run never traps you: it completes even when the wallpaper could not change.
            if (firstRun) settings.completeSetup()
            when {
                // Home changed but the lock screen could not: offer the phone's own wallpaper app before closing.
                drawnByLauncher != null -> lockScreenOffer = drawnByLauncher
                // From Settings, a failure leaves you on the screen to try again.
                done || firstRun -> finish()
            }
        }
    }

    /**
     * Home and lock screen together, in one write. False when the phone does not allow it or refuses
     * (Yomo treats a zero id as refused too).
     */
    private fun setSystemWallpaper(bitmap: Bitmap): Boolean {
        val manager = WallpaperManager.getInstance(this)
        if (!manager.isSetWallpaperAllowed) return false
        return try {
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) != 0
        } catch (e: Exception) {
            Log.w(TAG, "The phone refused the system wallpaper; drawing it in the launcher instead", e)
            false
        }
    }

    /**
     * Hands the wallpaper to the phone's own apps ("Set as"), where the lock screen may still be
     * changeable on managed phones (Yomo's FAQ points Samsung users to the same route). Whether
     * the phone allows it there is up to its policy.
     */
    private fun openPhoneWallpaperApp(bitmap: Bitmap) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val file = File(cacheDir, "wallpaper/lock_screen.jpg").apply { parentFile?.mkdirs() }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                FileProvider.getUriForFile(this@WallpaperActivity, "$packageName.files", file)
            }
            val setAs = Intent(Intent.ACTION_ATTACH_DATA)
                .setDataAndType(uri, "image/jpeg")
                .putExtra("mimeType", "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri(null, uri) }
            try {
                startActivity(Intent.createChooser(setAs, getString(R.string.set_lock_screen_with)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@WallpaperActivity, R.string.no_wallpaper_app, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    /** In debug builds only, the error itself, so a tester can report what went wrong. */
    private fun debugDetail(error: Throwable?): String {
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable || error == null) return ""
        return "\n(${error.javaClass.simpleName}: ${error.message})"
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private fun screenWidth() = screenSize().first

    private fun screenHeight() = screenSize().second

    companion object {
        private const val TAG = "PixelLauncher"
        private const val EXTRA_FIRST_RUN = "first_run"

        /** Its own task, so it opens over the launcher rather than inside the home task. */
        fun intent(context: Context, firstRun: Boolean): Intent =
            Intent(context, WallpaperActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_FIRST_RUN, firstRun)
    }
}

@Composable
private fun WallpaperScreen(
    firstRun: Boolean,
    applying: Boolean,
    screenWidth: Int,
    screenHeight: Int,
    onBuiltIn: (Int) -> Unit,
    onCustom: (make: () -> Bitmap) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var cropping by rememberSaveable { mutableStateOf<Uri?>(null) }
    // The system photo picker: no storage permission needed.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> cropping = uri }

    cropping?.let { uri ->
        CropScreen(
            uri = uri,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            applying = applying,
            onCancel = { cropping = null },
            onCropped = onCustom,
        )
        return
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            stringResource(if (firstRun) R.string.choose_wallpaper else R.string.change_wallpaper),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(24.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!firstRun) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = !applying) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Text(stringResource(R.string.set_custom_wallpaper), modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
            items((0 until BuiltInWallpapers.COUNT).toList()) { index ->
                Preview(index, chosen = selected == index, onClick = { selected = index })
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (applying) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { selected?.let(onBuiltIn) }, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (firstRun) R.string.continue_label else R.string.submit))
                }
            }
        }
    }
}

/**
 * Crop the picked photo to the screen's shape: drag to move it, pinch to zoom. Submit sets the part
 * inside the frame as the wallpaper, at exactly the screen's size.
 */
@Composable
private fun CropScreen(
    uri: Uri,
    screenWidth: Int,
    screenHeight: Int,
    applying: Boolean,
    onCancel: () -> Unit,
    onCropped: (make: () -> Bitmap) -> Unit,
) {
    var photo by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val marginPx = with(LocalDensity.current) { 24.dp.toPx() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(uri) {
        photo = withContext(Dispatchers.IO) { loadPhoto(context, uri, screenWidth, screenHeight) }
        if (photo == null) {
            Toast.makeText(context, R.string.wallpaper_not_changed, Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }

    val source = photo
    val image = remember(source) { source?.asImageBitmap() }
    val imageSize = source?.let { Size(it.width.toFloat(), it.height.toFloat()) }
    val frame = remember(box) { CropMath.frame(Size(box.width.toFloat(), box.height.toFloat()), screenWidth.toFloat() / screenHeight, marginPx) }
    var view by remember(imageSize, frame) {
        mutableStateOf(imageSize?.takeIf { !frame.isEmpty }?.let { CropMath.cover(it, frame) })
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { box = it }
                .pointerInput(imageSize, frame) {
                    val size = imageSize ?: return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        view = view?.let { CropMath.transform(it, centroid, pan, zoom, size, frame) }
                    }
                },
        ) {
            val v = view
            if (image == null || imageSize == null || v == null) return@Canvas
            drawImage(
                image,
                dstOffset = IntOffset(v.left.roundToInt(), v.top.roundToInt()),
                dstSize = IntSize((imageSize.width * v.scale).roundToInt(), (imageSize.height * v.scale).roundToInt()),
            )
            // Dim everything outside the frame, then outline it.
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset.Zero, Size(size.width, frame.top))
            drawRect(dim, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
            drawRect(dim, Offset(0f, frame.top), Size(frame.left, frame.height))
            drawRect(dim, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !applying, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val v = view ?: return@Button
                    if (source == null || imageSize == null) return@Button
                    val area = CropMath.sourceRect(v, frame, imageSize)
                    onCropped {
                        val cut = Bitmap.createBitmap(source, area.left, area.top, area.width, area.height)
                        Bitmap.createScaledBitmap(cut, screenWidth, screenHeight, true)
                    }
                },
                enabled = !applying && view != null,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.submit)) }
        }
    }
}

/**
 * Decodes the picked photo, scaled down to no smaller than the screen and upright (camera photos
 * store their rotation separately; Android 9+ applies it while decoding).
 */
private fun loadPhoto(context: Context, uri: Uri, screenWidth: Int, screenHeight: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height, screenWidth, screenHeight))
            // Cropping and saving the wallpaper read pixels, which hardware bitmaps do not allow.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, screenWidth, screenHeight) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
} catch (e: java.io.IOException) {
    null
} catch (e: SecurityException) {
    null
}

/** The largest power-of-two shrink that keeps both sides at least the screen's. */
internal fun sampleSize(width: Int, height: Int, screenWidth: Int, screenHeight: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= screenWidth && height / (sample * 2) >= screenHeight) sample *= 2
    return sample
}

/** After Submit on a phone that blocks wallpaper changes: home changed; the lock screen can only be tried in the phone's own app. */
@Composable
private fun LockScreenOffer(onSet: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_screen_offer_title)) },
        text = { Text(stringResource(R.string.lock_screen_offer_text)) },
        confirmButton = { TextButton(onClick = onSet) { Text(stringResource(R.string.set_on_lock_screen)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

/** As in Yomo, the chosen wallpaper grows slightly. */
@Composable
private fun Preview(index: Int, chosen: Boolean, onClick: () -> Unit) {
    var image by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index) { image = withContext(Dispatchers.Default) { BuiltInWallpapers.render(index, 270, 585).asImageBitmap() } }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .aspectRatio(270f / 585f)
            .scale(if (chosen) 1.05f else 1f)
            .clip(shape)
            .then(if (chosen) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        image?.let { Image(it, contentDescription = stringResource(R.string.wallpaper_number, index + 1), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
