package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ClipPipelineState
import com.example.model.SavedClip
import com.example.ui.components.PipelineStatusCard
import com.example.ui.components.SilenceKeepTimelineCard
import com.example.ui.components.formatTime
import com.example.ui.theme.DeckContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.ViralYellow
import com.example.viewmodel.SilenceRemoverUiState
import kotlin.math.roundToInt

@Composable
fun SilenceRemoverScreen(
    state: SilenceRemoverUiState,
    onPickVideo: (Uri) -> Unit,
    onNoiseDbChanged: (Int) -> Unit,
    onMinSilenceChanged: (Float) -> Unit,
    onRescanSilence: () -> Unit,
    onRemoveSilence: () -> Unit,
    onPlayClip: (SavedClip) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickVideo(uri)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCut,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                            Text(
                                text = "1. Automatic Silence Remover",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Detects quiet pauses below your dBFS noise gate and jump-cuts dead air out cleanly while preserving 0.12s natural speech padding.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )

                        Button(
                            onClick = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("silence_pick_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedVideoUri == null) "Pick Talking Video" else "Change Video"
                            )
                        }

                        if (state.selectedVideoUri != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = DeckContainer,
                                border = BorderStroke(1.dp, MintSuccess.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = state.videoTitle.ifBlank { "Selected Video" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatTime(state.videoDurationSec)} • ${state.videoWidth}x${state.videoHeight}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NeonCyan
                                        )
                                    }
                                    if (state.isAnalyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = ViralYellow,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Analyzed",
                                            tint = MintSuccess
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.analysisResult?.let { analysis ->
                item {
                    SilenceKeepTimelineCard(analysis = analysis)
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "2. Silence Threshold Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Silence Threshold (dBFS)", color = MutedSlate)
                                Text("${state.noiseDb} dB", color = ViralYellow, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = state.noiseDb.toFloat(),
                                onValueChange = { onNoiseDbChanged(it.roundToInt()) },
                                valueRange = -60f..-20f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ViralYellow,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("silence_db_slider")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Min Silence Duration", color = MutedSlate)
                                Text(
                                    text = "${"%.2f".format(state.minSilenceSec)}s",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = state.minSilenceSec,
                                onValueChange = { onMinSilenceChanged(((it * 10).roundToInt()) / 10f) },
                                valueRange = 0.3f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("silence_duration_slider")
                            )
                        }

                        if (state.selectedVideoUri != null) {
                            OutlinedButton(
                                onClick = onRescanSilence,
                                enabled = !state.isAnalyzing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("silence_rescan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview Speech Cuts")
                            }
                        }
                    }
                }
            }

            item {
                val isBusy = state.pipelineState is ClipPipelineState.AnalyzingAudio ||
                    state.pipelineState is ClipPipelineState.RenderingClip

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRemoveSilence,
                        enabled = !isBusy && state.selectedVideoUri != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ViralYellow,
                            contentColor = ObsidianBg,
                            disabledContainerColor = DeckContainer,
                            disabledContentColor = MutedSlate
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("remove_silence_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ObsidianBg,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Cutting Dead Air...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Jump-Cut MP4",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    PipelineStatusCard(
                        pipelineState = state.pipelineState,
                        peakReason = null,
                        onPlayClip = onPlayClip,
                        playButtonText = "Watch Jump-Cut Video"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
