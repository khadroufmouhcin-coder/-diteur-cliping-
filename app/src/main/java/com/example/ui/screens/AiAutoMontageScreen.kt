package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CaptionStyle
import com.example.model.ClipPipelineState
import com.example.model.SavedClip
import com.example.model.TransitionType
import com.example.ui.components.BackgroundMusicCard
import com.example.ui.components.CaptionStyleSelector
import com.example.ui.components.PipelineStatusCard
import com.example.ui.components.Short916PreviewCard
import com.example.ui.components.StudioToggleRow
import com.example.ui.components.TransitionSelector
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
import com.example.viewmodel.AiAutoMontageUiState
import kotlin.math.roundToInt

@Composable
fun AiAutoMontageScreen(
    state: AiAutoMontageUiState,
    onPickVideo: (Uri) -> Unit,
    onYoutubeUrlChanged: (String) -> Unit,
    onAnalyzeYoutubeUrl: () -> Unit,
    onNClipsChanged: (Int) -> Unit,
    onSegLenChanged: (Int) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onSelectTransition: (TransitionType) -> Unit,
    onToggleBlur: (Boolean) -> Unit,
    onToggleCaptions: (Boolean) -> Unit,
    onSelectCaptionStyle: (CaptionStyle) -> Unit,
    onToggleAutoHook: (Boolean) -> Unit,
    onHookTextChanged: (String) -> Unit,
    onMusicPicked: (Uri) -> Unit,
    onClearMusic: () -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onGenerateAiAutoMontage: () -> Unit,
    onPlayClip: (SavedClip) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickVideo(uri)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onMusicPicked(uri)
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
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = ViralYellow
                            )
                            Text(
                                text = "1. Long Video Highlight Finder",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Scans 70% audio loudness + 30% visual scene action to auto-extract top non-overlapping moments into a fast-paced mashup.",
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
                                .testTag("ai_montage_pick_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedVideoUri == null) "Pick Long Video" else "Change Source Video"
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
                                            text = state.videoTitle.ifBlank { "Long Source Video" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Duration: ${formatTime(state.videoDurationSec)} • Target ~${state.nClips * state.segLenSec}s mashup",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NeonCyan
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Video ready",
                                        tint = MintSuccess
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.youtubeUrlInput,
                            onValueChange = onYoutubeUrlChanged,
                            label = { Text("YouTube URL (Optional AI Hook Inspiration)") },
                            placeholder = { Text(stringResource(R.string.youtube_url_hint)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = MutedSlate)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricViolet,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_montage_yt_input")
                        )

                        OutlinedButton(
                            onClick = onAnalyzeYoutubeUrl,
                            enabled = !state.isAnalyzingYoutubeUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("ai_montage_yt_inspect_button")
                        ) {
                            Text(if (state.isAnalyzingYoutubeUrl) "Inspecting..." else "Fetch Title & Suggest Hook")
                        }

                        state.youtubeStatusMessage?.let { msg ->
                            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                        }
                    }
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
                            text = "2. Highlight Mashup Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Number of Highlights", color = MutedSlate)
                                Text("${state.nClips} clips", color = ViralYellow, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = state.nClips.toFloat(),
                                onValueChange = { onNClipsChanged(it.roundToInt()) },
                                valueRange = 2f..10f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ViralYellow,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("ai_montage_nclips_slider")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Seconds per Highlight", color = MutedSlate)
                                Text("${state.segLenSec}s", color = ViralYellow, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = state.segLenSec.toFloat(),
                                onValueChange = { onSegLenChanged(it.roundToInt()) },
                                valueRange = 4f..20f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ViralYellow,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("ai_montage_seglen_slider")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Playback Speed", color = MutedSlate)
                                Text("${"%.2f".format(state.speed)}x", color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = state.speed,
                                onValueChange = { onSpeedChanged(((it * 20).roundToInt()) / 20f) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("ai_montage_speed_slider")
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Transition Effect", style = MaterialTheme.typography.labelLarge, color = MutedSlate)
                            TransitionSelector(
                                selected = state.transition,
                                onSelect = onSelectTransition
                            )
                        }

                        StudioToggleRow(
                            title = "9:16 Vertical Blur Background",
                            subtitle = "Blurred background with full horizontal highlight framing",
                            checked = state.verticalBlur,
                            onCheckedChange = onToggleBlur,
                            testTag = "ai_montage_blur_switch"
                        )

                        StudioToggleRow(
                            title = "Dynamic Captions (Karaoke Ready)",
                            subtitle = "Highlights each active spoken word in Viral Yellow",
                            checked = state.enableCaptions,
                            onCheckedChange = onToggleCaptions,
                            testTag = "ai_montage_captions_switch"
                        )

                        AnimatedVisibility(visible = state.enableCaptions) {
                            CaptionStyleSelector(
                                selected = state.captionStyle,
                                onSelect = onSelectCaptionStyle
                            )
                        }

                        StudioToggleRow(
                            title = "AI Intro Hook (First 4 Seconds)",
                            subtitle = "Generates a scroll-stopping headline from speech if blank",
                            checked = state.autoHook,
                            onCheckedChange = onToggleAutoHook,
                            testTag = "ai_montage_hook_switch"
                        )

                        AnimatedVisibility(visible = state.autoHook) {
                            OutlinedTextField(
                                value = state.hookText,
                                onValueChange = onHookTextChanged,
                                label = { Text("Custom Hook (leave blank for Gemini auto-hook)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricViolet,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_montage_hook_input")
                            )
                        }

                        BackgroundMusicCard(
                            musicTitle = state.musicTitle,
                            musicVolume = state.musicVolume,
                            onPickMusic = { audioPicker.launch(arrayOf("audio/*")) },
                            onClearMusic = onClearMusic,
                            onVolumeChange = onMusicVolumeChanged
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Short916PreviewCard(
                                thumbnail = state.videoThumbnail,
                                hookEnabled = state.autoHook,
                                hookText = state.hookText.ifBlank { "AI AUTO-HOOK (4S)" },
                                captionsEnabled = state.enableCaptions,
                                captionStyle = state.captionStyle,
                                verticalBlur = state.verticalBlur,
                                previewCaption = "TOP VIRAL MOMENTS"
                            )
                        }
                    }
                }
            }

            item {
                val isBusy = state.pipelineState is ClipPipelineState.AnalyzingAudio ||
                    state.pipelineState is ClipPipelineState.GeneratingCaptions ||
                    state.pipelineState is ClipPipelineState.RenderingClip

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onGenerateAiAutoMontage,
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
                            .testTag("generate_ai_montage_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ObsidianBg,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Building Highlight Reel...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate AI Highlight Montage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    PipelineStatusCard(
                        pipelineState = state.pipelineState,
                        peakReason = state.lastPeakReason,
                        onPlayClip = onPlayClip,
                        playButtonText = "Watch AI Highlight Reel"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
