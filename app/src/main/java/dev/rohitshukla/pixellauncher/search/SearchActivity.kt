package dev.rohitshukla.pixellauncher.search

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.rohitshukla.pixellauncher.R
import dev.rohitshukla.pixellauncher.data.AppData
import dev.rohitshukla.pixellauncher.settings.AppLanguage
import dev.rohitshukla.pixellauncher.data.SearchEngine

/**
 * Yomo's search screen: a box labelled with the chosen engine, and its results shown inside the
 * app. Any link tapped on the results opens in the phone's browser instead, so pages that ask for
 * passwords or payment are never shown without the browser's address bar.
 */
class SearchActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialQuery = intent.getStringExtra(EXTRA_QUERY)
        val settingsFlow = AppData.settings(this).settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    settings?.let { SearchScreen(it.searchEngine, initialQuery, ::finish) }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_QUERY = "query"

        /** Its own task, like Settings, so it opens over the launcher rather than inside the home task. */
        fun intent(context: Context, query: String?): Intent =
            Intent(context, SearchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_QUERY, query)
    }
}

@Composable
private fun SearchScreen(engine: SearchEngine, initialQuery: String?, onExit: () -> Unit) {
    var input by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var searched by rememberSaveable { mutableStateOf(initialQuery?.takeIf { it.isNotBlank() }) }
    var loading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (searched == null) inputFocus.requestFocus() }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onExit()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(inputFocus),
            leadingIcon = { Text(engine.label, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelLarge) },
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { input = "" }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear)) }
                }
            },
            placeholder = { Text(stringResource(R.string.search)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (input.isNotBlank()) {
                    focus.clearFocus()
                    keyboard?.hide()
                    searched = input.trim()
                }
            }),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        val query = searched
        if (query == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    stringResource(R.string.search_with, engine.label),
                    modifier = Modifier.padding(top = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ResultsView(
                url = engine.resultsUrl(query),
                onLoading = { loading = it },
                onCreated = { webView = it },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ResultsView(url: String, onLoading: (Boolean) -> Unit, onCreated: (WebView) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Search pages need JavaScript; nothing in the app is exposed to them.
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = ResultsClient(context, onLoading)
                onCreated(this)
            }
        },
        update = { view -> if (view.url != url && view.originalUrl != url) view.loadUrl(url) },
        onRelease = { it.destroy() },
    )
}

/** Keeps the results page and its own redirects in the app; anything the user taps goes to the browser. */
private class ResultsClient(private val context: Context, private val onLoading: (Boolean) -> Unit) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame || !request.hasGesture()) return false
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.browser_not_found, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) = onLoading(true)

    override fun onPageFinished(view: WebView, url: String?) = onLoading(false)
}
