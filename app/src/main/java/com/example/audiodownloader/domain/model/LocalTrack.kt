package com.example.audiodownloader.domain.model

import android.net.Uri

/**
 * Represents a single audio track discovered on the device via MediaStore.
 *
 * @param id          MediaStore row id of the audio file.
 * @param title       Track title (falls back to file name when metadata is missing).
 * @param artist      Artist name, or "Unknown Artist".
 * @param album       Album name, or "Unknown Album".
 * @param durationMs  Track duration in milliseconds.
 * @param contentUri  Playable content URI for the audio file (used by ExoPlayer).
 * @param albumArtUri Content URI for the album artwork (may not resolve for every album).
 */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?
)