package com.sonora.music.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sonora.music.R
import com.sonora.music.data.network.HttpClientProvider
import com.sonora.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object SonoraPlayerBridge {
    val audioSessionIdFlow = MutableStateFlow(0)

    @Volatile
    var activePlayer: ExoPlayer? = null

    @Volatile
    var youtubeMirrorPlayer: YouTubeMirrorPlayer? = null

    @Volatile
    var activeSession: MediaSession? = null

    @Volatile
    var activeService: SonoraPlaybackService? = null

    @Volatile
    var isYouTubeModeActive: Boolean = false

    @Volatile
    private var pendingYouTubeActivation: Triple<List<Track>, Int, Boolean>? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSyncedPlayState: Boolean? = null
    private var lastSyncedPosMs: Long = -1L

    fun activateExoPlayer() {
        val action = Runnable {
            pendingYouTubeActivation = null
            isYouTubeModeActive = false
            val session = activeSession
            val exo = activePlayer
            if (session != null && exo != null && session.player !== exo) {
                session.setPlayer(exo)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    fun activateYouTubePlayer(queue: List<Track>, index: Int, isPlaying: Boolean = true) {
        val action = Runnable {
            isYouTubeModeActive = true
            lastSyncedPlayState = isPlaying
            lastSyncedPosMs = 0L
            activePlayer?.pause()

            val mirror = youtubeMirrorPlayer
            val session = activeSession
            val service = activeService
            if (mirror != null && session != null) {
                pendingYouTubeActivation = null
                val current = queue.getOrNull(index)
                val fallbackDuration = if (current == null || current.isLive) {
                    0L
                } else {
                    (current.durationSec * 1000L).coerceAtLeast(0L)
                }
                mirror.updateFromBridge(
                    queue = queue,
                    currentIndex = index,
                    isPlaying = isPlaying,
                    positionMs = 0L,
                    durationMs = fallbackDuration
                )
                if (session.player !== mirror) {
                    session.setPlayer(mirror)
                }
                service?.let { svc ->
                    runCatching {
                        svc.onUpdateNotification(session, isPlaying)
                    }
                }
            } else {
                pendingYouTubeActivation = Triple(queue, index, isPlaying)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    fun syncYouTubeState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        if (!isYouTubeModeActive) return
        val playChanged = lastSyncedPlayState != isPlaying
        val posDelta = kotlin.math.abs(positionMs - lastSyncedPosMs)
        if (!playChanged && posDelta < 1_800L) return

        lastSyncedPlayState = isPlaying
        lastSyncedPosMs = positionMs
        mainHandler.post {
            if (!isYouTubeModeActive) return@post
            val mirror = youtubeMirrorPlayer ?: return@post
            mirror.updateProgressState(isPlaying, positionMs, durationMs)
            val session = activeSession
            val service = activeService
            if (playChanged && session != null && service != null) {
                runCatching {
                    service.onUpdateNotification(session, isPlaying)
                }
            }
        }
    }

    internal fun applyPendingIfNeeded() {
        val pending = pendingYouTubeActivation ?: return
        pendingYouTubeActivation = null
        activateYouTubePlayer(pending.first, pending.second, pending.third)
    }
}

@OptIn(UnstableApi::class)
class SonoraBitmapLoader(private val context: Context) : BitmapLoader {
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/", ignoreCase = true)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable {
            decodeScaledBitmap(data) ?: throw IllegalArgumentException("Failed to decode artwork bytes")
        })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable {
            val scheme = uri.scheme.orEmpty()
            val bytes = when {
                scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true) -> {
                    val req = Request.Builder()
                        .url(uri.toString())
                        .header("User-Agent", "Sonora/4.1 (Android)")
                        .build()
                    HttpClientProvider.client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        resp.body?.bytes() ?: throw IllegalStateException("Empty artwork body")
                    }
                }
                else -> {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        input.copyTo(out)
                        out.toByteArray()
                    } ?: throw IllegalArgumentException("Unable to open artwork URI: $uri")
                }
            }
            decodeScaledBitmap(bytes) ?: throw IllegalStateException("Invalid bitmap data for $uri")
        })

    private fun decodeScaledBitmap(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 768) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }
}

@OptIn(UnstableApi::class)
class SonoraPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayerInstance: ExoPlayer? = null
    private var youtubeMirrorInstance: YouTubeMirrorPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                SonoraPlayerBridge.audioSessionIdFlow.value = audioSessionId
            }
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    item.buildUpon()
                        .setUri(item.requestMetadata.mediaUri)
                        .build()
                }
            }
            return Futures.immediateFuture(resolved)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_launcher_monochrome)
            }
        setMediaNotificationProvider(notificationProvider)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Sonora/4.1 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val mirror = YouTubeMirrorPlayer(Looper.getMainLooper())

        player.addListener(playerListener)
        exoPlayerInstance = player
        youtubeMirrorInstance = mirror
        SonoraPlayerBridge.activePlayer = player
        SonoraPlayerBridge.youtubeMirrorPlayer = mirror
        SonoraPlayerBridge.activeService = this

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET && player.audioSessionId > 0) {
            SonoraPlayerBridge.audioSessionIdFlow.value = player.audioSessionId
        }

        val launchIntent = Intent().apply {
            component = ComponentName(this@SonoraPlaybackService, "com.sonora.music.MainActivity")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(SonoraBitmapLoader(this))
            .build()
        SonoraPlayerBridge.activeSession = mediaSession
        SonoraPlayerBridge.applyPendingIfNeeded()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val active = mediaSession?.player
        val playing = active != null &&
            active.playWhenReady &&
            active.playbackState != Player.STATE_IDLE &&
            active.playbackState != Player.STATE_ENDED
        if (!playing) {
            exoPlayerInstance?.pause()
            YouTubePlaybackBridge.requestPlayPause(false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        SonoraPlayerBridge.activeService = null
        SonoraPlayerBridge.activeSession = null
        SonoraPlayerBridge.activePlayer = null
        SonoraPlayerBridge.youtubeMirrorPlayer = null
        SonoraPlayerBridge.isYouTubeModeActive = false
        SonoraPlayerBridge.audioSessionIdFlow.value = 0

        mediaSession?.run {
            exoPlayerInstance?.removeListener(playerListener)
            exoPlayerInstance?.release()
            youtubeMirrorInstance?.release()
            exoPlayerInstance = null
            youtubeMirrorInstance = null
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val MEDIA_NOTIFICATION_CHANNEL_ID = "sonora_media_playback"
    }
}
