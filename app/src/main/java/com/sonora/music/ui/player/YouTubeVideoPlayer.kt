package com.sonora.music.ui.player

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sonora.music.player.SonoraPlayerBridge
import com.sonora.music.player.YouTubePlaybackBridge
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

private const val PLAYER_ORIGIN_URL = "https://com.sonora.music/"
private const val PLAYER_ORIGIN_PARAM = "https://com.sonora.music"
private const val JS_BRIDGE_NAME = "SonoraYouTubeBridge"
private val VideoSurfaceBg = Color(0xFF101014)
private val FallbackCardBg = Color(0xFF1A1A22)

/**
 * Persistent embedded official YouTube IFrame Player API surface inside Android WebView.
 * Reports playback progress & state to YouTubePlaybackBridge + Media3 Notification/PiP
 * and continues playing across Picture-in-Picture & MiniPlayer transitions without re-buffering.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeVideoPlayer(
    videoId: String,
    title: String,
    artist: String,
    isInPictureInPicture: Boolean = false,
    isMiniMode: Boolean = false,
    onMiniTapExpand: (() -> Unit)? = null,
    onVideoEnded: () -> Unit,
    onFallbackOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeVideoId = remember(videoId) {
        videoId.trim().replace(Regex("[^A-Za-z0-9_-]"), "")
    }
    val watchUrl = remember(safeVideoId) {
        if (safeVideoId.isNotBlank()) "https://www.youtube.com/watch?v=$safeVideoId" else "https://www.youtube.com"
    }

    var errorCode by remember(safeVideoId) { mutableStateOf<Int?>(null) }
    var isPlayerReady by remember(safeVideoId) { mutableStateOf(false) }

    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val currentOnFallbackExternal by rememberUpdatedState(onFallbackOpenExternal)
    val currentInPip by rememberUpdatedState(isInPictureInPicture)
    val currentOnMiniTap by rememberUpdatedState(onMiniTapExpand)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (safeVideoId.isBlank() || errorCode != null) {
        if (isMiniMode && !isInPictureInPicture) {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(FallbackCardBg)
                    .clickable { currentOnMiniTap?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.OndemandVideo,
                    contentDescription = null,
                    tint = EmeraldAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            YouTubeFallbackCard(
                title = title,
                artist = artist,
                errorCode = errorCode,
                onOpenExternal = { currentOnFallbackExternal(watchUrl) },
                modifier = modifier
            )
        }
        return
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fullscreenDialog by remember { mutableStateOf<Dialog?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    DisposableEffect(webViewRef) {
        val wv = webViewRef
        if (wv != null) {
            YouTubePlaybackBridge.onCommandPlayPause = { play ->
                SonoraPlayerBridge.syncYouTubeState(
                    isPlaying = play,
                    positionMs = YouTubePlaybackBridge.positionMs.value,
                    durationMs = YouTubePlaybackBridge.durationMs.value
                )
                mainHandler.post {
                    runCatching {
                        if (play) {
                            wv.onResume()
                        }
                        wv.evaluateJavascript("if(window.sonoraSetPlaying){window.sonoraSetPlaying($play);}", null)
                    }
                }
            }
            YouTubePlaybackBridge.onCommandSeekTo = { posMs ->
                val sec = (posMs.coerceAtLeast(0L) / 1000.0)
                mainHandler.post {
                    runCatching {
                        wv.evaluateJavascript("if(window.sonoraSeekTo){window.sonoraSeekTo($sec);}", null)
                    }
                }
            }
        }
        onDispose {
            YouTubePlaybackBridge.onCommandPlayPause = null
            YouTubePlaybackBridge.onCommandSeekTo = null
        }
    }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                // Keep WebView running during ON_PAUSE so Picture-in-Picture transitions smoothly!
                Lifecycle.Event.ON_STOP -> {
                    if (!currentInPip) {
                        runCatching {
                            wv.evaluateJavascript("if(window.sonoraSetPlaying){window.sonoraSetPlaying(false);}", null)
                            wv.onPause()
                        }
                    }
                }
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> {
                    runCatching { wv.onResume() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                customViewCallback?.onCustomViewHidden()
                fullscreenDialog?.dismiss()
            }
            customViewCallback = null
            fullscreenDialog = null
            webViewRef?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.onPause()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeJavascriptInterface(JS_BRIDGE_NAME)
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
            webViewRef = null
        }
    }

    val cornerShape = when {
        isInPictureInPicture -> RoundedCornerShape(0.dp)
        isMiniMode -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(14.dp)
    }

    Box(
        modifier = modifier
            .clip(cornerShape)
            .background(VideoSurfaceBg),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#101014"))
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = WebSettings.LOAD_DEFAULT
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }

                    addJavascriptInterface(
                        SonoraYouTubeBridge(
                            mainHandler = mainHandler,
                            onReady = { isPlayerReady = true },
                            onEnded = { currentOnVideoEnded() },
                            onError = { code -> errorCode = code }
                        ),
                        JS_BRIDGE_NAME
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            if (url.isBlank() || url == PLAYER_ORIGIN_URL || url == "about:blank") {
                                return false
                            }
                            if (request?.isForMainFrame == true) {
                                currentOnFallbackExternal(url)
                                return true
                            }
                            return false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                errorCode = -1
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        private val posterBitmap: Bitmap by lazy {
                            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(AndroidColor.parseColor("#101014"))
                            }
                        }

                        override fun getDefaultVideoPoster(): Bitmap = posterBitmap

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view == null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            runCatching {
                                customViewCallback?.onCustomViewHidden()
                                fullscreenDialog?.dismiss()
                            }
                            val container = FrameLayout(context).apply {
                                setBackgroundColor(AndroidColor.BLACK)
                                addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                            val dialog = Dialog(
                                context,
                                android.R.style.Theme_Black_NoTitleBar_Fullscreen
                            ).apply {
                                setContentView(container)
                                setOnDismissListener {
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                    fullscreenDialog = null
                                }
                                show()
                            }
                            customViewCallback = callback
                            fullscreenDialog = dialog
                        }

                        override fun onHideCustomView() {
                            runCatching {
                                customViewCallback?.onCustomViewHidden()
                                fullscreenDialog?.dismiss()
                            }
                            customViewCallback = null
                            fullscreenDialog = null
                        }
                    }

                    tag = safeVideoId
                    loadDataWithBaseURL(
                        PLAYER_ORIGIN_URL,
                        buildYouTubePlayerHtml(safeVideoId),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
                if (webView.tag != safeVideoId) {
                    webView.tag = safeVideoId
                    isPlayerReady = false
                    errorCode = null
                    webView.loadDataWithBaseURL(
                        PLAYER_ORIGIN_URL,
                        buildYouTubePlayerHtml(safeVideoId),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        if (!isPlayerReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VideoSurfaceBg.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = EmeraldAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(if (isMiniMode) 18.dp else 30.dp)
                )
            }
        }

        if (isMiniMode && !isInPictureInPicture) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { currentOnMiniTap?.invoke() }
            )
        }
    }
}

@Composable
private fun YouTubeFallbackCard(
    title: String,
    artist: String,
    errorCode: Int?,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reasonText = when (errorCode) {
        101, 150 -> "This official music video restricts embedded playback."
        100 -> "This YouTube video is unavailable or private."
        -1 -> "Unable to load YouTube player — check your connection."
        null -> "No YouTube video ID linked for this track."
        else -> "Embedded video playback unavailable (code $errorCode)."
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        color = FallbackCardBg,
        border = BorderStroke(1.dp, ObsidianStroke)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF242430),
                            FallbackCardBg
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OndemandVideo,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "YOUTUBE MUSIC VIDEO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = EmeraldAccent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = title.ifBlank { "Official Music Video" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenExternal,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open in YouTube",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private class SonoraYouTubeBridge(
    private val mainHandler: Handler,
    private val onReady: () -> Unit,
    private val onEnded: () -> Unit,
    private val onError: (Int) -> Unit
) {
    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onStateChanged(state: Int) {
        val playing = state == 1 || state == 3
        if (state == 1 || state == 2) {
            val posMs = YouTubePlaybackBridge.positionMs.value
            val durMs = YouTubePlaybackBridge.durationMs.value
            YouTubePlaybackBridge.reportPlayerState(state == 1, posMs, durMs)
            SonoraPlayerBridge.syncYouTubeState(state == 1, posMs, durMs)
        } else if (state == 0) {
            mainHandler.post { onEnded() }
        }
        if (playing) {
            mainHandler.post { onReady() }
        }
    }

    @JavascriptInterface
    fun onProgressUpdate(playing: Boolean, positionSec: Double, durationSec: Double) {
        val posMs = (positionSec * 1000.0).toLong().coerceAtLeast(0L)
        val durMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L)
        YouTubePlaybackBridge.reportPlayerState(playing, posMs, durMs)
        SonoraPlayerBridge.syncYouTubeState(playing, posMs, durMs)
    }

    @JavascriptInterface
    fun onPlayerError(errorCode: Int) {
        mainHandler.post { onError(errorCode) }
    }
}

private fun buildYouTubePlayerHtml(videoId: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <meta name="referrer" content="strict-origin-when-cross-origin" />
      <style>
        html, body {
          margin: 0;
          padding: 0;
          width: 100%;
          height: 100%;
          background-color: #101014;
          overflow: hidden;
        }
        #player, iframe {
          width: 100%;
          height: 100%;
          border: 0;
        }
      </style>
    </head>
    <body>
      <div id="player"></div>
      <script src="https://www.youtube.com/iframe_api" onerror="if(window.$JS_BRIDGE_NAME){window.$JS_BRIDGE_NAME.onPlayerError(-1);}"></script>
      <script>
        var player;
        var progressTimer = null;

        window.sonoraSetPlaying = function(shouldPlay) {
          if (!player) return;
          try {
            if (shouldPlay) {
              player.playVideo();
            } else {
              player.pauseVideo();
            }
          } catch (e) {}
        };

        window.sonoraSeekTo = function(sec) {
          if (!player) return;
          try {
            player.seekTo(sec, true);
          } catch (e) {}
        };

        function emitProgress() {
          if (!player || !window.$JS_BRIDGE_NAME) return;
          try {
            var state = typeof player.getPlayerState === 'function' ? player.getPlayerState() : -1;
            var pos = typeof player.getCurrentTime === 'function' ? (player.getCurrentTime() || 0) : 0;
            var dur = typeof player.getDuration === 'function' ? (player.getDuration() || 0) : 0;
            window.$JS_BRIDGE_NAME.onProgressUpdate(state === 1 || state === 3, pos, dur);
          } catch (e) {}
        }

        function onYouTubeIframeAPIReady() {
          try {
            player = new YT.Player('player', {
              videoId: '$videoId',
              width: '100%',
              height: '100%',
              playerVars: {
                autoplay: 1,
                playsinline: 1,
                rel: 0,
                modestbranding: 1,
                controls: 1,
                fs: 1,
                enablejsapi: 1,
                origin: '$PLAYER_ORIGIN_PARAM'
              },
              events: {
                onReady: function(event) {
                  if (window.$JS_BRIDGE_NAME) {
                    window.$JS_BRIDGE_NAME.onPlayerReady();
                  }
                  try { event.target.playVideo(); } catch (e) {}
                  if (progressTimer) clearInterval(progressTimer);
                  progressTimer = setInterval(emitProgress, 800);
                },
                onStateChange: function(event) {
                  if (window.$JS_BRIDGE_NAME) {
                    window.$JS_BRIDGE_NAME.onStateChanged(event.data);
                    emitProgress();
                  }
                },
                onError: function(event) {
                  if (window.$JS_BRIDGE_NAME) {
                    window.$JS_BRIDGE_NAME.onPlayerError(event.data);
                  }
                }
              }
            });
          } catch (err) {
            if (window.$JS_BRIDGE_NAME) {
              window.$JS_BRIDGE_NAME.onPlayerError(-1);
            }
          }
        }
      </script>
    </body>
    </html>
""".trimIndent()
