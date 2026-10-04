package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SpotifyWebRepository
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import kotlinx.coroutines.launch

/**
 * Zero-Client-ID Spotify Authentication & Sync Screen.
 * Uses a Jetpack Compose WebView loading Spotify Login to capture the sp_dc session cookie,
 * or allows direct manual cookie entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginDialog(
    spotifyRepository: SpotifyWebRepository,
    onDismiss: () -> Unit,
    onImportCompleted: (SpotifyWebRepository.ImportResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var isImporting by remember { mutableStateOf(false) }
    var importStatusText by remember { mutableStateOf("Logging in to Spotify...") }
    var importResult by remember { mutableStateOf<SpotifyWebRepository.ImportResult?>(null) }
    var showManualCookieInput by remember { mutableStateOf(false) }
    var manualCookieText by remember { mutableStateOf("") }
    var hasCapturedCookie by remember { mutableStateOf(false) }

    fun executeImport(spDcCookie: String) {
        if (hasCapturedCookie) return
        hasCapturedCookie = true
        isImporting = true
        importStatusText = "Minting token & importing your Liked Songs, Playlists & Taste Profile..."

        coroutineScope.launch {
            val result = spotifyRepository.importSpotifyData(spDcCookie.trim())
            importResult = result
            isImporting = false
            if (result.isSuccess) {
                onImportCompleted(result)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OneUIDarkBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = modifier.testTag("spotify_login_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1DB954), shape = RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♫",
                            color = Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Spotify Zero-Config Sync",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Text(
                            text = "Import Playlists, Liked Songs & Taste Profile",
                            fontSize = 11.sp,
                            color = OneUITextSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = OneUITextPrimary)
                }
            }

            // Status Banner / Progress
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OneUIDarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF1DB954),
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Importing Spotify Library",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = importStatusText,
                            fontSize = 13.sp,
                            color = OneUITextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (importResult != null) {
                val res = importResult!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = OneUICardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (res.isSuccess) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(60.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Spotify Import Successful!",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUITextPrimary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "• ${res.likedSongsCount} Liked Songs\n• ${res.playlistsCount} User Playlists\n• ${res.topArtistsCount} Personalized Taste Affinities",
                                    fontSize = 14.sp,
                                    color = OneUITextSecondary,
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Explore Imported Library", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    text = "Import Encountered an Issue",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = res.errorMessage ?: "Unknown error",
                                    fontSize = 13.sp,
                                    color = OneUITextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        importResult = null
                                        hasCapturedCookie = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Retry Login", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            } else if (showManualCookieInput) {
                // Manual Cookie Input Fallback Option
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Paste Spotify 'sp_dc' Cookie",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If you have your sp_dc cookie from your browser or Spotube, paste it here for instant direct sync without logging in via WebView.",
                        fontSize = 12.sp,
                        color = OneUITextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = manualCookieText,
                        onValueChange = { manualCookieText = it },
                        placeholder = { Text("AQB... (sp_dc cookie string)", fontSize = 13.sp) },
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OneUISurfaceDark,
                            unfocusedContainerColor = OneUISurfaceDark,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = OneUITextPrimary,
                            unfocusedTextColor = OneUITextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (manualCookieText.isNotBlank()) {
                                executeImport(manualCookieText.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Import", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showManualCookieInput = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to WebView Login", color = OneUITextSecondary)
                    }
                }
            } else {
                // Jetpack Compose WebView loading Spotify Login
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Log in to your Spotify account below to sync.",
                            fontSize = 12.sp,
                            color = OneUITextSecondary
                        )

                        TextButton(onClick = { showManualCookieInput = true }) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Cookie", fontSize = 12.sp, color = Color(0xFF1DB954))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.WHITE)
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.setSupportMultipleWindows(false)
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                    webViewClient = object : WebViewClient() {
                                        private fun interceptAndExtractSession(url: String?): Boolean {
                                            if (url.isNullOrBlank() || hasCapturedCookie) return false
                                            try {
                                                val uri = Uri.parse(url)

                                                // 1. Intercept redirect URIs carrying authorization code or token in query params
                                                val spDcParam = uri.getQueryParameter("sp_dc")
                                                val tokenParam = uri.getQueryParameter("access_token")
                                                val codeParam = uri.getQueryParameter("code")

                                                // 2. Intercept OAuth tokens or codes in URL fragment (e.g. #access_token=...&token_type=Bearer)
                                                var fragmentToken: String? = null
                                                var fragmentSpDc: String? = null
                                                var fragmentCode: String? = null
                                                val fragment = uri.fragment
                                                if (!fragment.isNullOrBlank()) {
                                                    fragment.split("&").forEach { pair ->
                                                        val parts = pair.split("=", limit = 2)
                                                        if (parts.size == 2) {
                                                            val key = parts[0]
                                                            val value = Uri.decode(parts[1])
                                                            if (key == "access_token") fragmentToken = value
                                                            else if (key == "sp_dc") fragmentSpDc = value
                                                            else if (key == "code") fragmentCode = value
                                                        }
                                                    }
                                                }

                                                val tokenCandidate = spDcParam ?: fragmentSpDc ?: tokenParam ?: fragmentToken ?: codeParam ?: fragmentCode
                                                if (!tokenCandidate.isNullOrBlank()) {
                                                    executeImport(tokenCandidate)
                                                    return true
                                                }

                                                // 3. Check cookies across all Spotify endpoints
                                                val cookies = cookieManager.getCookie("https://spotify.com")
                                                    ?: cookieManager.getCookie("https://open.spotify.com")
                                                    ?: cookieManager.getCookie("https://accounts.spotify.com")
                                                    ?: (cookieManager.getCookie(url))
                                                    ?: ""

                                                val spDc = extractCookieValue(cookies, "sp_dc")
                                                if (!spDc.isNullOrBlank() && !hasCapturedCookie) {
                                                    executeImport(spDc)
                                                    return true
                                                }

                                                // 4. Intercept redirect URIs (localhost, loopback, custom app schemes)
                                                val host = uri.host?.lowercase() ?: ""
                                                val scheme = uri.scheme?.lowercase() ?: ""
                                                val isRedirectUri = host == "localhost" || host == "127.0.0.1" ||
                                                        scheme == "spotube" || scheme == "com.example" || scheme == "spotify" ||
                                                        url.contains("/callback") || url.contains("redirect_uri")

                                                if (isRedirectUri) {
                                                    cookieManager.flush()
                                                    val flushedCookies = cookieManager.getCookie("https://spotify.com")
                                                        ?: cookieManager.getCookie("https://open.spotify.com")
                                                        ?: cookieManager.getCookie("https://accounts.spotify.com")
                                                        ?: ""
                                                    val flushedSpDc = extractCookieValue(flushedCookies, "sp_dc")
                                                    if (!flushedSpDc.isNullOrBlank()) {
                                                        executeImport(flushedSpDc)
                                                    }
                                                    return true // Consume redirect to prevent ERR_CONNECTION_REFUSED / blank screen
                                                }
                                            } catch (_: Exception) {}
                                            return false
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString() ?: return false
                                            if (interceptAndExtractSession(targetUrl)) {
                                                return true
                                            }
                                            if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                                return false
                                            }
                                            return try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                                ctx.startActivity(intent)
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        }

                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            if (url == null) return false
                                            if (interceptAndExtractSession(url)) {
                                                return true
                                            }
                                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                                return false
                                            }
                                            return try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                                                ctx.startActivity(intent)
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            interceptAndExtractSession(url)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            cookieManager.flush()
                                            interceptAndExtractSession(url)
                                        }

                                        override fun onLoadResource(view: WebView?, url: String?) {
                                            super.onLoadResource(view, url)
                                            interceptAndExtractSession(url)
                                        }

                                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            interceptAndExtractSession(url)
                                        }
                                    }

                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            if (newProgress > 50) {
                                                cookieManager.flush()
                                                val curUrl = view?.url
                                                if (!curUrl.isNullOrBlank()) {
                                                    val cookies = cookieManager.getCookie("https://spotify.com")
                                                        ?: cookieManager.getCookie("https://open.spotify.com")
                                                        ?: cookieManager.getCookie("https://accounts.spotify.com")
                                                        ?: ""
                                                    val spDc = extractCookieValue(cookies, "sp_dc")
                                                    if (!spDc.isNullOrBlank() && !hasCapturedCookie) {
                                                        executeImport(spDc)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    loadUrl("https://accounts.spotify.com/en/login")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracts a specific cookie value from a standard cookie header string.
 */
private fun extractCookieValue(cookieString: String, key: String): String? {
    if (cookieString.isBlank()) return null
    val pairs = cookieString.split(";")
    for (pair in pairs) {
        val trimmed = pair.trim()
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx != -1) {
            val name = trimmed.substring(0, eqIdx).trim()
            if (name.equals(key, ignoreCase = true)) {
                return trimmed.substring(eqIdx + 1).trim()
            }
        }
    }
    return null
}
