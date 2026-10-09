package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.network.NetworkMonitor
import com.example.ui.components.AboutDialog
import com.example.ui.components.JametulHodaTopBar
import com.example.ui.components.NoInternetView
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JametulHodaScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = networkMonitor.isCurrentlyConnected())

    val homeUrl = stringResource(R.string.site_url)
    var currentUrl by remember { mutableStateOf(homeUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0.1f) }
    var hasError by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // File chooser callback for <input type="file">
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val clipData = result.data?.clipData
            val dataUri = result.data?.data
            val results: Array<Uri>? = when {
                clipData != null -> {
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                dataUri != null -> {
                    arrayOf(dataUri)
                }
                else -> null
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Handle back button press
    val exitMessage = stringResource(R.string.exit_confirm)
    BackHandler {
        val wv = webViewInstance
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = currentTime
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(exitMessage)
                }
            }
        }
    }

    // RTL provider for Iranian / Persian environment
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("main_scaffold"),
            topBar = {
                JametulHodaTopBar(
                    title = pageTitle,
                    isLoading = isLoading,
                    progress = progress,
                    canGoForward = canGoForward,
                    onRefresh = {
                        hasError = false
                        webViewInstance?.reload()
                    },
                    onHome = {
                        hasError = false
                        webViewInstance?.loadUrl(homeUrl)
                    },
                    onForward = {
                        if (webViewInstance?.canGoForward() == true) {
                            webViewInstance?.goForward()
                        }
                    },
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, pageTitle.ifBlank { "جامعة الهدی" })
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری آدرس"))
                    },
                    onOpenExternal = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "مرورگری یافت نشد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClearCache = {
                        webViewInstance?.clearCache(true)
                        CookieManager.getInstance().flush()
                        Toast.makeText(context, "حافظه موقت پاک شد", Toast.LENGTH_SHORT).show()
                    },
                    onAbout = {
                        showAboutDialog = true
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Main WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("jametulhoda_webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                cacheMode = if (isOnline) {
                                    WebSettings.LOAD_DEFAULT
                                } else {
                                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                                }
                                userAgentString = userAgentString.replace("; wv", "") // Clean modern mobile UA
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            // Handle downloads
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                try {
                                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                                        setMimeType(mimetype)
                                        addRequestHeader("User-Agent", userAgent)
                                        setDescription("در حال دانلود فایل از جامعة الهدی…")
                                        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                        setTitle(filename)
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                    }
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                    dm?.enqueue(request)
                                    Toast.makeText(ctx, "دانلود فایل آغاز شد", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    // Fallback to external browser
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    isLoading = newProgress < 100
                                    if (newProgress >= 80) {
                                        isInitialLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                        pageTitle = title
                                    }
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallbackParam: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = filePathCallbackParam

                                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }

                                    try {
                                        fileChooserLauncher.launch(intent)
                                        return true
                                    } catch (e: Exception) {
                                        filePathCallback?.onReceiveValue(null)
                                        filePathCallback = null
                                        return false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val urlString = uri.toString()
                                    val scheme = uri.scheme?.lowercase()

                                    // External protocols (calls, emails, telegram, whatsapp)
                                    if (scheme != null && scheme != "http" && scheme != "https") {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            ctx.startActivity(intent)
                                            return true
                                        } catch (e: Exception) {
                                            return false
                                        }
                                    }

                                    // Let internal site & common login/auth endpoints load inside WebView
                                    val host = uri.host?.lowercase() ?: ""
                                    val isAllowedDomain = host.contains("jametulhoda.vercel.app") ||
                                            host.contains("vercel.app") ||
                                            host.contains("accounts.google.com") ||
                                            host.contains("firebaseapp.com")

                                    if (isAllowedDomain) {
                                        return false
                                    }

                                    // External websites can be opened outside
                                    return try {
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        ctx.startActivity(intent)
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    hasError = false
                                    url?.let { currentUrl = it }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    isInitialLoading = false
                                    url?.let { currentUrl = it }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        // Ignore net errors if offline cache worked or minor
                                        if (!isOnline) {
                                            hasError = true
                                        } else {
                                            hasError = true
                                        }
                                        isLoading = false
                                    }
                                }
                            }

                            loadUrl(homeUrl)
                        }
                    },
                    update = { view ->
                        // React to network change
                        view.settings.cacheMode = if (isOnline) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                    }
                )

                // Error / Offline Screen
                if (hasError && !isOnline) {
                    NoInternetView(
                        onRetry = {
                            hasError = false
                            isLoading = true
                            webViewInstance?.reload()
                        },
                        onLoadCache = {
                            hasError = false
                            webViewInstance?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                            webViewInstance?.reload()
                        }
                    )
                }

                // Initial branded Splash / Loading overlay
                AnimatedVisibility(
                    visible = isInitialLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .testTag("initial_loading_splash"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_jametulhoda_logo),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }

        if (showAboutDialog) {
            AboutDialog(onDismissRequest = { showAboutDialog = false })
        }
    }
}
