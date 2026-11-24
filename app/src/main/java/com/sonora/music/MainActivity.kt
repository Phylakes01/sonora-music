package com.sonora.music

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sonora.music.ui.SonoraApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SonoraViewModel by viewModels()

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP_MEDIA_CONTROL) return
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, -1)) {
                CONTROL_PREV -> viewModel.skipPrev()
                CONTROL_PLAY_PAUSE -> viewModel.togglePlayPause()
                CONTROL_NEXT -> viewModel.skipNext()
            }
        }
    }

    private val localAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onLocalAudioPermissionResult(granted)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Optional media notification permission; playback continues either way.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(ACTION_PIP_MEDIA_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        viewModel.registerLocalPermissionRequester {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            localAudioPermissionLauncher.launch(permission)
        }
        viewModel.registerPipRequester {
            enterPipIfPossible()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state ->
                        val hasVideo = state.isVideoMode && !state.playback.currentTrack?.youtubeVideoId.isNullOrBlank()
                        val autoEnter = hasVideo && state.playback.isPlaying
                        Triple(hasVideo, autoEnter, state.playback.isPlaying)
                    }
                    .distinctUntilChanged()
                    .collect { (hasVideo, autoEnter, isPlaying) ->
                        if (hasVideo || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                setPictureInPictureParams(
                                    buildPipParams(autoEnterEnabled = autoEnter, isPlaying = isPlaying)
                                )
                            }
                        }
                    }
            }
        }

        requestNotificationsPermissionIfNeeded()
        if (savedInstanceState == null) {
            viewModel.handleDeepLink(intent)
        }

        setContent {
            SonoraApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleDeepLink(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && viewModel.shouldAutoEnterPictureInPicture()) {
            enterPipIfPossible()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPictureInPictureMode(isInPictureInPictureMode)
    }

    override fun onStop() {
        viewModel.onActivityStopped(isInPictureInPictureMode)
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipActionReceiver) }
        super.onDestroy()
    }

    private fun enterPipIfPossible() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val isPlaying = viewModel.uiState.value.playback.isPlaying
        runCatching {
            enterPictureInPictureMode(
                buildPipParams(
                    autoEnterEnabled = viewModel.shouldAutoEnterPictureInPicture(),
                    isPlaying = isPlaying
                )
            )
        }
    }

    private fun buildPipParams(autoEnterEnabled: Boolean, isPlaying: Boolean): PictureInPictureParams {
        val prevIntent = PendingIntent.getBroadcast(
            this,
            CONTROL_PREV,
            Intent(ACTION_PIP_MEDIA_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL_TYPE, CONTROL_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getBroadcast(
            this,
            CONTROL_PLAY_PAUSE,
            Intent(ACTION_PIP_MEDIA_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL_TYPE, CONTROL_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            this,
            CONTROL_NEXT,
            Intent(ACTION_PIP_MEDIA_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL_TYPE, CONTROL_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actions = listOf(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                "Previous",
                "Previous track",
                prevIntent
            ),
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                ),
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause playback" else "Resume playback",
                toggleIntent
            ),
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_next),
                "Next",
                "Next track",
                nextIntent
            )
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val ACTION_PIP_MEDIA_CONTROL = "com.sonora.music.PIP_MEDIA_CONTROL"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_PREV = 1
        private const val CONTROL_PLAY_PAUSE = 2
        private const val CONTROL_NEXT = 3
    }
}
