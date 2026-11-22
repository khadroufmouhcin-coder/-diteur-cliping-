package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CaptionStyle
import com.example.model.ClipPipelineState
import com.example.model.MontageClipItem
import com.example.model.SavedClip
import com.example.model.TransitionType
import com.example.ui.components.BackgroundMusicCard
import com.example.ui.components.CaptionStyleSelector
import com.example.ui.components.PipelineStatusCard
import com.example.ui.components.StudioToggleRow
import com.example.ui.components.TransitionSelector
import com.example.ui.components.formatTime
import com.example.ui.theme.DeckContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.ViralYellow
import com.example.viewmodel.MontageUiState
import kotlin.math.roundToInt

@Composable
fun MontageScreen(
    state: MontageUiState,
    onAddVideos: (List<Uri>) -> Unit,
    onRemoveClip: (String) -> Unit,
    onMoveClip: (String, Int) -> Unit,
    onSegLenChanged: (Int) -> Unit,
    onToggleAutoBest: (Boolean) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onSelectTransition: (TransitionType) -> Unit,
    onToggleBlur: (Boolean) -> Unit,
    onToggleCaptions: (Boolean) -> Unit,
    onSelectCaptionStyle: (CaptionStyle) -> Unit,
    onToggleHook: (Boolean) -> Unit,
    onHookTextChanged: (String) -> Unit,
    onMusicPicked: (Uri) -> Unit,
    onClearMusic: () -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onGenerateMontage: () -> Unit,
    onPlayClip: (SavedClip) -> Unit,
    modifier: Modifier = Modifier
) {
    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) onAddVideos(uris)
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
                                imageVector = Icons.Filled.ViewCarousel,
                                contentDescription = null,
                                tint = ElectricViolet
                            )
                            Text(
                                text = "1. Multi-Clip Storyboard",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Add multiple clips, reorder them, pick each clip's loudest moment, and render into one synced 9:16 montage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )
                        Button(
                            onClick = {
                                multiPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = !state.isLoadingClips,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("montage_add_videos_button")
                        ) {
                            if (state.isLoadingClips) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning Audio Peaks...")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AddCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Clips to Montage (${state.clips.size})")
                            }
                        }
                    }
                }
            }

            if (state.clips.isNotEmpty()) {
                itemsIndexed(state.clips, key = { _, item -> item.id }) { idx, clip ->
                    MontageClipRow(
                        index = idx,
                        total = state.clips.size,
                        clip = clip,
                        onMoveUp = { onMoveClip(clip.id, -1) },
                        onMoveDown = { onMoveClip(clip.id, 1) },
                        onRemove = { onRemoveClip(clip.id) }
                    )
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
                            text = "2. Pacing, Transitions & Audio",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Seconds per Clip",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlate
                                )
                                Text(
                                    text = "${state.segLenSec}s",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ViralYellow
                                )
                            }
                            Slider(
                                value = state.segLenSec.toFloat(),
                                onValueChange = { onSegLenChanged(it.roundToInt()) },
                                valueRange = 3f..30f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ViralYellow,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("montage_seglen_slider")
                            )
                        }

                        StudioToggleRow(
                            title = "Auto-Pick Best Moment per Clip",
                            subtitle = "Automatically centers each clip on its loudest RMS audio peak",
                            checked = state.autoBestMoment,
                            onCheckedChange = onToggleAutoBest,
                            testTag = "montage_auto_best_switch"
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Playback Speed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlate
                                )
                                Text(
                                    text = "${"%.2f".format(state.speed)}x",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                            }
                            Slider(
                                value = state.speed,
                                onValueChange = { onSpeedChanged(((it * 20).roundToInt()) / 20f) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("montage_speed_slider")
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Cut Transition",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedSlate
                            )
                            TransitionSelector(
                                selected = state.transition,
                                onSelect = onSelectTransition
                            )
                        }

                        StudioToggleRow(
                            title = "9:16 Vertical Blur Background",
                            subtitle = "Keeps full framing with blurred backdrop bars",
                            checked = state.verticalBlur,
                            onCheckedChange = onToggleBlur,
                            testTag = "montage_blur_switch"
                        )

                        StudioToggleRow(
                            title = "Dynamic AI Captions",
                            subtitle = "Transcribes joined track via Gemini REST API",
                            checked = state.enableCaptions,
                            onCheckedChange = onToggleCaptions,
                            testTag = "montage_captions_switch"
                        )

                        AnimatedVisibility(visible = state.enableCaptions) {
                            CaptionStyleSelector(
                                selected = state.captionStyle,
                                onSelect = onSelectCaptionStyle
                            )
                        }

                        StudioToggleRow(
                            title = "Top Hook Header",
                            subtitle = "Displays a bold hook badge across the montage",
                            checked = state.enableHook,
                            onCheckedChange = onToggleHook,
                            testTag = "montage_hook_switch"
                        )

                        AnimatedVisibility(visible = state.enableHook) {
                            OutlinedTextField(
                                value = state.hookText,
                                onValueChange = onHookTextChanged,
                                label = { Text("Hook Headline (optional — AI fills if blank)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricViolet,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("montage_hook_input")
                            )
                        }

                        BackgroundMusicCard(
                            musicTitle = state.musicTitle,
                            musicVolume = state.musicVolume,
                            onPickMusic = { audioPicker.launch(arrayOf("audio/*")) },
                            onClearMusic = onClearMusic,
                            onVolumeChange = onMusicVolumeChanged
                        )
                    }
                }
            }

            item {
                val isBusy = state.pipelineState is ClipPipelineState.AnalyzingAudio ||
                    state.pipelineState is ClipPipelineState.GeneratingCaptions ||
                    state.pipelineState is ClipPipelineState.RenderingClip

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onGenerateMontage,
                        enabled = !isBusy && state.clips.isNotEmpty(),
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
                            .testTag("generate_montage_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ObsidianBg,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Rendering Montage...",
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
                                text = "Render 9:16 Montage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    PipelineStatusCard(
                        pipelineState = state.pipelineState,
                        peakReason = state.lastPeakReason,
                        onPlayClip = onPlayClip,
                        playButtonText = "Watch Montage"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MontageClipRow(
    index: Int,
    total: Int,
    clip: MontageClipItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = DeckContainer,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                if (clip.thumbnail != null) {
                    Image(
                        bitmap = clip.thumbnail.asImageBitmap(),
                        contentDescription = clip.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ViralYellow
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${clip.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTime(clip.durationSec)} • Peak @ ${clip.peakStartSec.toInt()}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }

            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("montage_move_up_$index")
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "Move clip up",
                    tint = if (index > 0) Color.White else MutedSlate.copy(alpha = 0.35f)
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = index < total - 1,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("montage_move_down_$index")
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = "Move clip down",
                    tint = if (index < total - 1) Color.White else MutedSlate.copy(alpha = 0.35f)
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("montage_remove_$index")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove clip",
                    tint = PeakRed
                )
            }
        }
    }
}
