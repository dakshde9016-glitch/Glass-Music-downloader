package com.example.audiodownloader.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiodownloader.domain.model.AudioQuality
import com.example.audiodownloader.domain.model.DownloadState
import com.example.audiodownloader.ui.components.*
import com.example.audiodownloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectDownloadTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val queryInput by viewModel.queryInput.collectAsStateWithLifecycle()
    val songChips by viewModel.songChips.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Multi-line Input Box
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Add Songs or URLs",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type titles or paste links (one per line or comma-separated):",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                GlassTextField(
                    value = queryInput,
                    onValueChange = viewModel::onQueryInputChange,
                    placeholder = "e.g. Starboy The Weeknd\nMidnight City M83",
                    singleLine = false,
                    modifier = Modifier.height(90.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF10B981)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    GlassButton(
                        text = "Add to Queue",
                        onClick = viewModel::addCurrentQueryToQueue,
                        enabled = queryInput.isNotBlank() && !isDownloading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // Queued Song Chips (FlowRow)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queued Tracks (${songChips.size})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (songChips.isNotEmpty()) {
                        TextButton(
                            onClick = viewModel::clearQueue,
                            enabled = !isDownloading
                        ) {
                            Text("Clear All", color = Color(0xFFF87171), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (songChips.isEmpty()) {
                    Text(
                        text = "No songs in queue. Add songs above or extract from Spotify.",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        songChips.forEachIndexed { index, song ->
                            GlassChip(
                                title = song,
                                onRemove = { viewModel.removeChip(index) }
                            )
                        }
                    }
                }
            }
        }

        // Quality Selector & Actions
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio Quality",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Target stream bitrate",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }

                    GlassDropdownMenu(
                        selectedOption = when (selectedQuality) {
                            AudioQuality.LOW -> AudioQualityOption.LOW
                            AudioQuality.NORMAL -> AudioQualityOption.NORMAL
                            AudioQuality.HIGH -> AudioQualityOption.HIGH
                        },
                        onOptionSelected = { option ->
                            val quality = when (option) {
                                AudioQualityOption.LOW -> AudioQuality.LOW
                                AudioQualityOption.NORMAL -> AudioQuality.NORMAL
                                AudioQualityOption.HIGH -> AudioQuality.HIGH
                            }
                            viewModel.onQualitySelected(quality)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        text = if (isDownloading) "Downloading..." else "Download All",
                        onClick = viewModel::startBatchDownload,
                        enabled = songChips.isNotEmpty() && !isDownloading,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    GlassButton(
                        text = "Clear",
                        onClick = viewModel::clearQueue,
                        enabled = songChips.isNotEmpty() && !isDownloading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // Real-Time Progress Card
        item {
            AnimatedVisibility(visible = downloadState !is DownloadState.Idle) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Download Engine Status",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (val state = downloadState) {
                        is DownloadState.Queued -> {
                            Text(
                                text = "⏳ Queued: ${state.songTitle}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp
                            )
                        }
                        is DownloadState.Downloading -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = state.progressPercent / 100f,
                                label = "progress"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = state.songTitle,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${state.progressPercent.toInt()}% (${state.speed})",
                                        color = Color(0xFF10B981),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF10B981),
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                            }
                        }
                        is DownloadState.Converting -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "🔄 Converting to MP3 (FFmpeg): ${state.songTitle}",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 14.sp
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFFFBBF24),
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                            }
                        }
                        is DownloadState.Completed -> {
                            Text(
                                text = "✅ Completed: ${state.songTitle}",
                                color = Color(0xFF10B981),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        is DownloadState.Failed -> {
                            Text(
                                text = "❌ Failed: ${state.songTitle}\n${state.errorMessage}",
                                color = Color(0xFFF87171),
                                fontSize = 13.sp
                            )
                        }
                        DownloadState.Idle -> {}
                    }
                }
            }
        }
    }
}