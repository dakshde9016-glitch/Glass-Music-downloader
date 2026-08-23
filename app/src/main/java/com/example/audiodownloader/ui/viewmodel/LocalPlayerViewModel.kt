package com.example.audiodownloader.ui.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.audiodownloader.domain.model.LocalTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel backing the local music player tab.
 *
 * Responsibilities:
 *  - Query [MediaStore] for all music files on the device.
 *  - Own the [ExoPlayer] instance and expose its state as Compose-friendly StateFlows.
 *  - Provide playback controls (play/pause, seek, next/previous).
 */
class LocalPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val player: ExoPlayer = ExoPlayer.Builder(application)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Whether the loaded track list has already been handed to ExoPlayer as a playlist. */
    private var playlistSynced = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentTrackIndex.value = player.currentMediaItemIndex
            _positionMs.value = 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }
        }
    }

    init {
        // Poll playback position while playing so the UI seek bar stays in sync.
        player.addListener(playerListener)
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Re-scans MediaStore for music on the device and (first time only) loads
     * the result into ExoPlayer as a playlist so next/previous work across the library.
     */
    fun refreshTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = queryTracksFromMediaStore()
            _tracks.value = loaded
            syncPlaylistWithPlayer(loaded)
            _isLoading.value = false
        }
    }

    /** Starts playback of the track at [index] within the current library list. */
    fun playTrack(index: Int) {
        val trackList = _tracks.value
        if (index !in trackList.indices) return
        if (!playlistSynced) syncPlaylistWithPlayer(trackList)

        if (player.currentMediaItemIndex == index && player.playbackState != Player.STATE_IDLE) {
            player.play()
        } else {
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
        }
        _currentTrackIndex.value = index
        _positionMs.value = 0L
    }

    /** Toggles between play and pause; starts the first track if nothing is active yet. */
    fun togglePlayPause() {
        when {
            player.isPlaying -> player.pause()
            _tracks.value.isEmpty() -> Unit
            player.playbackState == Player.STATE_IDLE -> {
                val startIndex = _currentTrackIndex.value
                    .takeIf { it in _tracks.value.indices }
                    ?: 0
                playTrack(startIndex)
            }
            else -> {
                player.prepare()
                player.play()
            }
        }
    }

    /** Seeks the active track to [positionMs]. No-op when nothing is loaded. */
    fun seekTo(positionMs: Long) {
        if (player.playbackState == Player.STATE_IDLE) return
        player.seekTo(positionMs)
        _positionMs.value = positionMs.coerceIn(0L, _durationMs.value)
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.prepare()
            player.play()
        }
    }

    private fun syncPlaylistWithPlayer(trackList: List<LocalTrack>) {
        if (playlistSynced || trackList.isEmpty()) return
        val mediaItems = trackList.map { track ->
            MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems)
        playlistSynced = true
    }

    private fun queryTracksFromMediaStore(): List<LocalTrack> {
        val context = getApplication<Application>()
        val tracks = mutableListOf<LocalTrack>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val durationMs =
                            if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L

                        tracks.add(
                            LocalTrack(
                                id = id,
                                title = cursor.getString(titleColumn) ?: "Unknown Title",
                                artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                                album = cursor.getString(albumColumn) ?: "Unknown Album",
                                durationMs = durationMs,
                                contentUri = ContentUris.withAppendedId(collection, id),
                                albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)
                            )
                        )
                    }
                }
        } catch (_: SecurityException) {
            // Permission was revoked mid-session; fail soft with an empty list.
        } catch (_: IllegalArgumentException) {
            // Invalid column/URI on some OEM builds; fail soft with an empty list.
        }

        return tracks
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L
        private const val SEEK_INCREMENT_MS = 10_000L
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}