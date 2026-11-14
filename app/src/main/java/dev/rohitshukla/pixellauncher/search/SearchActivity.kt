package dev.rohitshukla.pixellauncher.search

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
    // The registrable domain of the search engine, e.g. "google.com". Only pages on it stay in the app.
    val allowedDomain = remember(url) { Uri.parse(url).host?.split('.')?.takeLast(2)?.joinToString(".").orEmpty() }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // Incognito: no cookies, no cache. Search pages need JavaScript; nothing in the app is exposed to them.
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                setAcceptCookie(false)
            }
            WebView(context).apply {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = ResultsClient(context, allowedDomain, onLoading)
                onCreated(this)
            }
        },
        // Load each distinct query once; in-page navigation and redirects are left alone.
        update = { view -> if (view.tag != url) { view.tag = url; view.loadUrl(url) } },
        onRelease = { it.clearHistory(); it.clearCache(true); it.destroy() },
    )
}

/**
 * Keeps only pages on the search engine's own domain in the app; every other main-frame navigation
 * (a tapped link, or a cross-site JavaScript/meta redirect that could be a phishing page) opens in
 * the real browser, which has an address bar. Only http/https are launched.
 */
private class ResultsClient(
    private val context: Context,
    private val allowedDomain: String,
    private val onLoading: (Boolean) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Sub-frame (iframe) navigations load normally; only main-frame navigation is gated.
        if (!request.isForMainFrame) return false
        val url = request.url
        val sameSite = url.host?.let { it == allowedDomain || it.endsWith(".$allowedDomain") } == true
        if (sameSite) return false
        if (url.scheme == "http" || url.scheme == "https") {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.browser_not_found, Toast.LENGTH_SHORT).show()
            }
        }
        // Non-http schemes (intent://, market:// …) are dropped: never load them in the chromeless view.
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) = onLoading(true)

    override fun onPageFinished(view: WebView, url: String?) = onLoading(false)
}
