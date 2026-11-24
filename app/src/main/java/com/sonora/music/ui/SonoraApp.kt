package com.sonora.music.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.SonoraViewModel
import com.sonora.music.model.AppTab
import com.sonora.music.model.Artist
import com.sonora.music.model.DetailRoute
import com.sonora.music.model.DownloadStatus
import com.sonora.music.model.Playlist
import com.sonora.music.model.Track
import com.sonora.music.ui.components.AddToPlaylistSheet
import com.sonora.music.ui.components.BottomNavBar
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.ImportExportPlaylistDialog
import com.sonora.music.ui.components.OpeningIntroOverlay
import com.sonora.music.ui.components.SonoraArtwork
import com.sonora.music.ui.components.TrackActionSheet
import com.sonora.music.ui.player.AudioStudioSheet
import com.sonora.music.ui.player.FullPlayerSheet
import com.sonora.music.ui.player.LyricsSheet
import com.sonora.music.ui.player.MiniPlayer
import com.sonora.music.ui.player.QueueSheet
import com.sonora.music.ui.screens.AiPlaylistSheet
import com.sonora.music.ui.screens.DetailScreenHost
import com.sonora.music.ui.screens.HomeScreen
import com.sonora.music.ui.screens.LibraryScreen
import com.sonora.music.ui.screens.SearchScreen
import com.sonora.music.ui.screens.WrappedScreen
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianCardElevated
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.SonoraTheme
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary

@Composable
fun SonoraApp(
    viewModel: SonoraViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var overflowTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var pendingTrackForNewPlaylist by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var shareImportPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var openGenericImportDialog by remember { mutableStateOf(false) }
    var openJamImportExportDialog by remember { mutableStateOf(false) }
    var addTracksToPlaylistTarget by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    // Back navigation hierarchy for non-ModalBottomSheet screens and overlays
    BackHandler(
        enabled = uiState.isWrappedOpen ||
            uiState.isFullPlayerOpen ||
            uiState.detail.route != DetailRoute.None ||
            uiState.currentTab != AppTab.HOME
    ) {
        when {
            uiState.isWrappedOpen -> viewModel.closeWrapped()
            uiState.isFullPlayerOpen -> viewModel.closeFullPlayer()
            uiState.detail.route != DetailRoute.None -> viewModel.closeDetail()
            uiState.currentTab != AppTab.HOME -> viewModel.selectTab(AppTab.HOME)
        }
    }

    SonoraTheme(dynamicAccent = uiState.dominantAccent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            Scaffold(
                containerColor = ObsidianBg,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(
                                bottom = if (uiState.playback.currentTrack != null && !uiState.isFullPlayerOpen) {
                                    146.dp
                                } else if (!uiState.isFullPlayerOpen) {
                                    82.dp
                                } else {
                                    16.dp
                                }
                            )
                    ) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = ObsidianCardElevated,
                            contentColor = TextPrimary,
                            actionColor = uiState.dominantAccent,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.detail.route != DetailRoute.None) {
                        DetailScreenHost(
                            state = uiState,
                            actions = viewModel,
                            onTrackOverflow = { overflowTrack = it },
                            onOpenAddTrackPicker = { addTracksToPlaylistTarget = it },
                            onOpenRenamePlaylist = { editingPlaylist = it },
                            onOpenShareImportPlaylist = { shareImportPlaylist = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AnimatedContent(
                            targetState = uiState.currentTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                            },
                            label = "main_tab_content",
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            when (tab) {
                                AppTab.HOME -> HomeScreen(
                                    state = uiState,
                                    actions = viewModel,
                                    onTrackOverflow = { overflowTrack = it },
                                    modifier = Modifier.fillMaxSize()
                                )

                                AppTab.SEARCH -> SearchScreen(
                                    state = uiState,
                                    actions = viewModel,
                                    onTrackOverflow = { overflowTrack = it },
                                    modifier = Modifier.fillMaxSize()
                                )

                                AppTab.LIBRARY -> LibraryScreen(
                                    state = uiState,
                                    actions = viewModel,
                                    onTrackOverflow = { overflowTrack = it },
                                    onOpenCreatePlaylist = {
                                        pendingTrackForNewPlaylist = null
                                        showCreatePlaylistDialog = true
                                    },
                                    onOpenImportDialog = { openGenericImportDialog = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Docked opaque #121212 MiniPlayer + BottomNavBar
                    if (!uiState.isFullPlayerOpen && !uiState.isWrappedOpen) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(ObsidianBg)
                        ) {
                            AnimatedVisibility(
                                visible = uiState.playback.currentTrack != null,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                val currentTrack = uiState.playback.currentTrack
                                val isLiked = currentTrack != null && currentTrack.id in uiState.likedTrackIds
                                MiniPlayer(
                                    playback = uiState.playback,
                                    isLiked = isLiked,
                                    accentColor = uiState.dominantAccent,
                                    onOpenFullPlayer = viewModel::openFullPlayer,
                                    onTogglePlayPause = viewModel::togglePlayPause,
                                    onSkipNext = viewModel::skipNext,
                                    onSkipPrev = viewModel::skipPrev,
                                    onToggleLike = {
                                        currentTrack?.let { viewModel.toggleLike(it) }
                                    }
                                )
                            }

                            BottomNavBar(
                                currentTab = uiState.currentTab,
                                accentColor = uiState.dominantAccent,
                                onSelectTab = viewModel::selectTab
                            )
                        }
                    }
                }
            }

            // Full-Screen Player Overlay
            AnimatedVisibility(
                visible = uiState.isFullPlayerOpen && uiState.playback.currentTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(240)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(220))
            ) {
                FullPlayerSheet(
                    state = uiState,
                    actions = viewModel,
                    onTrackOverflow = { overflowTrack = it },
                    onAddToPlaylist = { addToPlaylistTrack = it }
                )
            }

            // Wrapped Story Viewer Overlay
            AnimatedVisibility(
                visible = uiState.isWrappedOpen,
                enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.96f),
                exit = fadeOut(tween(220))
            ) {
                WrappedScreen(
                    stats = uiState.wrappedStats,
                    onClose = viewModel::closeWrapped,
                    onShare = viewModel::shareWrapped
                )
            }

            // Lyrics Modal Sheet
            if (uiState.isLyricsOpen) {
                LyricsSheet(
                    track = uiState.playback.currentTrack,
                    lyrics = uiState.currentLyrics,
                    isLoading = uiState.isLoadingLyrics,
                    positionMs = uiState.playback.positionMs,
                    accentColor = uiState.dominantAccent,
                    onSeekTo = viewModel::seekTo,
                    onDismiss = viewModel::closeLyrics
                )
            }

            // Queue & Jam Sheet
            if (uiState.isQueueOpen) {
                QueueSheet(
                    playback = uiState.playback,
                    accentColor = uiState.dominantAccent,
                    onPlayTrackInQueue = viewModel::playTrack,
                    onMoveItem = viewModel::moveQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onClearUpNext = viewModel::clearQueue,
                    onOpenJamShare = { openJamImportExportDialog = true },
                    onDismiss = viewModel::closeQueue
                )
            }

            // Audio Studio & 5-Band EQ Sheet
            if (uiState.isAudioStudioOpen) {
                AudioStudioSheet(
                    audioSettings = uiState.audioSettings,
                    sleepTimer = uiState.playback.sleepTimer,
                    playbackSpeed = uiState.playback.playbackSpeed,
                    outputDevices = uiState.outputDevices,
                    accentColor = uiState.dominantAccent,
                    onUpdateSettings = viewModel::updateAudioSettings,
                    onApplyPreset = viewModel::applyEqPreset,
                    onSetSleepTimer = viewModel::setSleepTimer,
                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                    onRefreshOutputDevices = viewModel::refreshOutputDevices,
                    onOpenOutputPicker = viewModel::openOutputPicker,
                    onDismiss = viewModel::closeAudioStudio
                )
            }

            // AI Mood Playlist Generator Sheet
            if (uiState.isAiGeneratorOpen) {
                AiPlaylistSheet(
                    searchState = uiState.search,
                    onDismiss = viewModel::closeAiGenerator,
                    onGenerate = viewModel::generateAiPlaylist,
                    onPlayGenerated = { playlist ->
                        if (playlist.tracks.isNotEmpty()) {
                            viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                        }
                        viewModel.closeAiGenerator()
                    },
                    onSaveGenerated = viewModel::saveGeneratedPlaylist
                )
            }

            // Track Overflow Action Sheet
            overflowTrack?.let { track ->
                val dlState = uiState.downloads[track.id]
                TrackActionSheet(
                    track = track,
                    isLiked = track.id in uiState.likedTrackIds,
                    downloadState = dlState,
                    onDismiss = { overflowTrack = null },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddToQueue = { viewModel.addToQueue(track) },
                    onAddToPlaylist = { addToPlaylistTrack = track },
                    onStartRadio = { viewModel.startRadio(track) },
                    onDownloadToggle = {
                        if (dlState?.status == DownloadStatus.DOWNLOADED) {
                            viewModel.deleteDownload(track.id)
                        } else {
                            viewModel.downloadTrack(track)
                        }
                    },
                    onGoToArtist = {
                        viewModel.closeFullPlayer()
                        viewModel.openDetail(
                            DetailRoute.ArtistDetail(
                                Artist(
                                    id = track.artistId,
                                    name = track.displayArtist
                                )
                            )
                        )
                    },
                    onShare = { viewModel.shareTrack(track) },
                    onOpenFullTrack = { viewModel.openFullTrackExternal(track) }
                )
            }

            // Add Track to Playlist Sheet
            addToPlaylistTrack?.let { track ->
                AddToPlaylistSheet(
                    track = track,
                    playlists = uiState.library.userPlaylists,
                    onDismiss = { addToPlaylistTrack = null },
                    onSelectPlaylist = { pl -> viewModel.addTrackToPlaylist(pl.id, track) },
                    onCreateNewClick = {
                        pendingTrackForNewPlaylist = track
                        showCreatePlaylistDialog = true
                    }
                )
            }

            // Create Playlist Dialog
            if (showCreatePlaylistDialog) {
                CreatePlaylistDialog(
                    onDismiss = {
                        showCreatePlaylistDialog = false
                        pendingTrackForNewPlaylist = null
                    },
                    onConfirm = { name, desc, colorStart, colorEnd ->
                        viewModel.createPlaylist(
                            name = name,
                            description = desc,
                            colorStart = colorStart,
                            colorEnd = colorEnd,
                            initialTrack = pendingTrackForNewPlaylist
                        )
                        showCreatePlaylistDialog = false
                        pendingTrackForNewPlaylist = null
                    }
                )
            }

            // Rename Playlist Dialog
            editingPlaylist?.let { playlist ->
                CreatePlaylistDialog(
                    initialName = playlist.name,
                    initialDescription = playlist.description,
                    isEditMode = true,
                    onDismiss = { editingPlaylist = null },
                    onConfirm = { newName, newDesc, _, _ ->
                        viewModel.renamePlaylist(playlist.id, newName, newDesc)
                        editingPlaylist = null
                    }
                )
            }

            // Import / Export / Jam Code Dialog
            val activeSharePlaylist = shareImportPlaylist
            if (activeSharePlaylist != null || openGenericImportDialog || openJamImportExportDialog) {
                val (dialogTitle, exportCode) = when {
                    activeSharePlaylist != null -> {
                        "Share \"${activeSharePlaylist.name}\"" to viewModel.exportPlaylistCode(activeSharePlaylist)
                    }

                    openJamImportExportDialog -> {
                        "Jam Queue Share" to viewModel.exportQueueCode()
                    }

                    else -> {
                        "Import Playlist or Jam" to null
                    }
                }
                ImportExportPlaylistDialog(
                    title = dialogTitle,
                    exportCode = exportCode,
                    onDismiss = {
                        shareImportPlaylist = null
                        openGenericImportDialog = false
                        openJamImportExportDialog = false
                    },
                    onShareExport = { code ->
                        sharePlaylistCodeText(context, dialogTitle, code)
                    },
                    onImportCode = { code, mergeIntoJam ->
                        viewModel.importPlaylistCode(code, mergeIntoJam)
                    }
                )
            }

            // Add Tracks Picker for PlaylistDetail
            addTracksToPlaylistTarget?.let { targetPlaylist ->
                val latestPlaylist = uiState.library.userPlaylists.firstOrNull {
                    it.id == targetPlaylist.id
                } ?: targetPlaylist
                val candidates = remember(
                    uiState.library.likedSongs,
                    uiState.home.quickPicks,
                    uiState.home.phTopTracks,
                    uiState.home.opmTracks,
                    uiState.home.globalTopTracks,
                    uiState.home.trendingTracks,
                    uiState.library.localTracks
                ) {
                    (
                        uiState.library.likedSongs +
                            uiState.home.quickPicks +
                            uiState.home.phTopTracks +
                            uiState.home.opmTracks +
                            uiState.home.globalTopTracks +
                            uiState.home.trendingTracks +
                            uiState.library.localTracks
                        ).distinctBy { it.id }
                }
                AddTracksToPlaylistPickerSheet(
                    playlist = latestPlaylist,
                    candidates = candidates,
                    onAddTrack = { track -> viewModel.addTrackToPlaylist(latestPlaylist.id, track) },
                    onDismiss = { addTracksToPlaylistTarget = null }
                )
            }

            // Launch Resonance Intro Overlay
            OpeningIntroOverlay(
                visible = uiState.showIntroOverlay,
                onFinished = viewModel::dismissIntroOverlay
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTracksToPlaylistPickerSheet(
    playlist: Playlist,
    candidates: List<Track>,
    onAddTrack: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    val filtered = remember(filter, candidates) {
        if (filter.isBlank()) {
            candidates
        } else {
            candidates.filter {
                it.title.contains(filter, ignoreCase = true) ||
                    it.displayArtist.contains(filter, ignoreCase = true)
            }
        }
    }
    val existingIds = remember(playlist.tracks, playlist.trackIds) {
        (playlist.trackIds + playlist.tracks.map { it.id }).toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Add Tracks to \"${playlist.name}\"",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter liked, trending or local tracks...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = ObsidianStroke,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching tracks found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { track ->
                        val added = track.id in existingIds
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, ObsidianStroke),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !added) { onAddTrack(track) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SonoraArtwork(
                                    artworkUrl = track.artworkUrl,
                                    title = track.title,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.displayArtist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = if (added) "Added" else "Add track",
                                    tint = if (added) MaterialTheme.colorScheme.primary else TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sharePlaylistCodeText(context: Context, title: String, code: String) {
    val deepLink = "sonora://playlist?code=${Uri.encode(code)}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(
            Intent.EXTRA_TEXT,
            "Join my mix on Sonora!\nOpen link: $deepLink\n\nOr paste code:\n$code"
        )
    }
    val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}
