package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.model.AudioEnergyProfile
import com.example.model.CaptionStyle
import com.example.model.ClipDurationOption
import com.example.model.ClipPipelineState
import com.example.model.HypeLevel
import com.example.model.SavedClip
import com.example.model.SilenceAnalysisResult
import com.example.model.StudioToolMode
import com.example.model.TransitionType
import com.example.ui.theme.DeckContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HypeOrange
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.SubtleSlate
import com.example.ui.theme.ViralYellow
import kotlin.math.max
import kotlin.math.min

@Composable
fun StudioToolModeSelector(
    selected: StudioToolMode,
    onSelect: (StudioToolMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StudioToolMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val icon = when (mode) {
                StudioToolMode.VIRAL_CLIP -> Icons.Filled.MovieCreation
                StudioToolMode.MONTAGE -> Icons.Filled.ViewCarousel
                StudioToolMode.AI_AUTO_MONTAGE -> Icons.Filled.AutoAwesome
                StudioToolMode.SILENCE_REMOVER -> Icons.Filled.ContentCut
            }
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(mode) }
                    .testTag(mode.testTag),
                shape = RoundedCornerShape(50),
                color = if (isSelected) ElectricViolet else StudioSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) ViralYellow.copy(alpha = 0.85f) else HairlineBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) ViralYellow else MutedSlate,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MutedSlate,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun StudioHeroBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, HairlineBorder),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero),
                contentDescription = "Viral Clipper Studio Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.92f),
                                ObsidianBg.copy(alpha = 0.72f),
                                ObsidianBg.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ElectricViolet.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = ViralYellow,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "DIGICLIPS 9:16 ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ViralYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.studio_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DurationSegmentedRow(
    selected: ClipDurationOption,
    onSelect: (ClipDurationOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ClipDurationOption.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(option) }
                    .testTag("duration_chip_${option.seconds}s"),
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) ElectricViolet else DeckContainer,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ElectricViolet else HairlineBorder
                )
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSelected) Color.White else MutedSlate
                    )
                }
            }
        }
    }
}

@Composable
fun CaptionStyleSelector(
    selected: CaptionStyle,
    onSelect: (CaptionStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CaptionStyle.entries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { style ->
                    val isSelected = style == selected
                    val accentColor = Color(style.composeColorHex)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(style) }
                            .testTag("caption_style_${style.name.lowercase()}"),
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) accentColor.copy(alpha = 0.16f) else DeckContainer,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else HairlineBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = style.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accentColor else MutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransitionSelector(
    selected: TransitionType,
    onSelect: (TransitionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransitionType.entries.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { trans ->
                    val isSelected = trans == selected
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(trans) }
                            .testTag("transition_${trans.name.lowercase()}"),
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) ElectricViolet.copy(alpha = 0.24f) else DeckContainer,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ElectricViolet else HairlineBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = trans.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MutedSlate,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundMusicCard(
    musicTitle: String?,
    musicVolume: Float,
    onPickMusic: () -> Unit,
    onClearMusic: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = DeckContainer,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Music (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Text(
                            text = musicTitle?.ifBlank { null } ?: "No track selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (musicTitle != null) NeonCyan else MutedSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (musicTitle != null) {
                    IconButton(
                        onClick = onClearMusic,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_music_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear background music",
                            tint = PeakRed
                        )
                    }
                }
                OutlinedButton(
                    onClick = onPickMusic,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pick_music_button")
                ) {
                    Text(if (musicTitle == null) "Add MP3" else "Change")
                }
            }

            if (musicTitle != null) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Music Volume",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSlate
                        )
                        Text(
                            text = "${(musicVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = ViralYellow
                        )
                    }
                    Slider(
                        value = musicVolume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = ElectricViolet
                        ),
                        modifier = Modifier.testTag("music_volume_slider")
                    )
                }
            }
        }
    }
}

@Composable
fun SilenceKeepTimelineCard(
    analysis: SilenceAnalysisResult,
    modifier: Modifier = Modifier
) {
    val savedSec = (analysis.originalDurationSec - analysis.newDurationSec).coerceAtLeast(0.0)
    val savedPct = if (analysis.originalDurationSec > 0.0) {
        ((savedSec / analysis.originalDurationSec) * 100.0).toInt().coerceIn(0, 100)
    } else 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioSurface,
        border = BorderStroke(1.dp, MintSuccess.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speech Map (${analysis.keepIntervals.size} active clips)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "-$savedPct% dead air",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintSuccess
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(ObsidianBg)
                    .testTag("silence_timeline_canvas")
            ) {
                val total = max(0.1, analysis.originalDurationSec).toFloat()
                analysis.keepIntervals.forEach { range ->
                    val left = ((range.startSec / total).toFloat() * size.width).coerceIn(0f, size.width)
                    val right = ((range.endSec / total).toFloat() * size.width).coerceIn(left, size.width)
                    drawRect(
                        color = MintSuccess,
                        topLeft = Offset(left, 0f),
                        size = Size(max(3f, right - left), size.height)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Before: ${formatTime(analysis.originalDurationSec)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSlate
                )
                Text(
                    text = "After: ${formatTime(analysis.newDurationSec)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }
        }
    }
}

@Composable
fun AudioEnergyWaveformCard(
    profile: AudioEnergyProfile,
    selectedStartSec: Double,
    clipDurationSec: Int,
    onStartChanged: (Double) -> Unit,
    onSnapToPeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = ViralYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Audio RMS Peak Detector",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onSnapToPeak,
                    modifier = Modifier.testTag("snap_peak_button")
                ) {
                    Text(
                        text = "Peak ${profile.peakStartSec.toInt()}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViralYellow
                    )
                }
            }

            val totalDur = max(1.0, profile.durationSec)
            val windowEnd = min(totalDur, selectedStartSec + clipDurationSec)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(ObsidianBg)
                    .pointerInput(profile, clipDurationSec) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            val target = (ratio * totalDur) - (clipDurationSec / 2.0)
                            onStartChanged(target.coerceIn(0.0, max(0.0, totalDur - clipDurationSec)))
                        }
                    }
                    .testTag("audio_waveform_canvas")
            ) {
                val bars = profile.secondEnergies
                if (bars.isNotEmpty()) {
                    val sampleStep = max(1, bars.size / 72)
                    val sampled = bars.chunked(sampleStep).map { chunk -> chunk.maxOrNull() ?: 0f }
                    val barCount = sampled.size
                    val barSlot = size.width / barCount.toFloat()
                    val barWidth = max(2f, barSlot * 0.65f)

                    val startFrac = (selectedStartSec / totalDur).toFloat().coerceIn(0f, 1f)
                    val endFrac = (windowEnd / totalDur).toFloat().coerceIn(startFrac, 1f)

                    drawRect(
                        color = ElectricViolet.copy(alpha = 0.20f),
                        topLeft = Offset(startFrac * size.width, 0f),
                        size = Size((endFrac - startFrac) * size.width, size.height)
                    )

                    sampled.forEachIndexed { idx, energy ->
                        val frac = idx.toFloat() / barCount.toFloat()
                        val inWindow = frac in startFrac..endFrac
                        val barH = max(6f, energy * (size.height - 14f))
                        val left = idx * barSlot + (barSlot - barWidth) / 2f
                        val top = (size.height - barH) / 2f
                        drawRoundRect(
                            color = if (inWindow) ViralYellow else SubtleSlate.copy(alpha = 0.55f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Clip Window: ${formatTime(selectedStartSec)} – ${formatTime(windowEnd)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                Text(
                    text = "Total ${formatTime(totalDur)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSlate
                )
            }
        }
    }
}

@Composable
fun Short916PreviewCard(
    thumbnail: Bitmap?,
    hookEnabled: Boolean,
    hookText: String,
    captionsEnabled: Boolean,
    captionStyle: CaptionStyle,
    verticalBlur: Boolean,
    previewCaption: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(196.dp)
            .aspectRatio(9f / 16f),
        shape = MaterialTheme.shapes.large,
        color = ObsidianBg,
        border = BorderStroke(1.5.dp, HairlineBorder),
        tonalElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                if (verticalBlur) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.26f
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBg.copy(alpha = 0.45f))
                    )
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "9:16 Video Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "9:16 Video Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(DeckContainer, ObsidianBg)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "9:16\nPREVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        color = SubtleSlate,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (hookEnabled && hookText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 26.dp, start = 14.dp, end = 14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg.copy(alpha = 0.86f))
                        .border(1.dp, ElectricViolet.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = hookText.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (captionsEnabled) {
                val sampleText = (previewCaption?.ifBlank { null } ?: "DYNAMIC AI CAPTIONS").uppercase()
                val captionDisplay = if (captionStyle == CaptionStyle.KARAOKE) {
                    val words = sampleText.split(Regex("\\s+")).filter { it.isNotBlank() }.take(3)
                    buildAnnotatedString {
                        words.forEachIndexed { idx, w ->
                            if (idx > 0) append(" ")
                            val isHighlight = idx == words.size / 2
                            withStyle(
                                SpanStyle(
                                    color = if (isHighlight) ViralYellow else Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) {
                                append(w)
                            }
                        }
                    }
                } else {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(captionStyle.composeColorHex))) {
                            append(sampleText)
                        }
                    }
                }

                Text(
                    text = captionDisplay,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp, start = 14.dp, end = 14.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun StudioToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricViolet
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun PipelineStatusCard(
    pipelineState: ClipPipelineState,
    peakReason: String?,
    onPlayClip: (SavedClip) -> Unit,
    playButtonText: String = "Watch Ready 9:16 Clip"
) {
    when (pipelineState) {
        ClipPipelineState.Idle -> Unit
        is ClipPipelineState.AnalyzingAudio -> {
            StatusBannerSurface(
                borderColor = NeonCyan,
                title = pipelineState.stepMessage,
                progress = null
            )
        }
        is ClipPipelineState.GeneratingCaptions -> {
            StatusBannerSurface(
                borderColor = ElectricViolet,
                title = pipelineState.stepMessage,
                progress = null
            )
        }
        is ClipPipelineState.RenderingClip -> {
            StatusBannerSurface(
                borderColor = ViralYellow,
                title = pipelineState.stepMessage,
                progress = pipelineState.progress
            )
        }
        is ClipPipelineState.Success -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurface,
                border = BorderStroke(1.5.dp, MintSuccess)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MintSuccess
                        )
                        Text(
                            text = pipelineState.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MintSuccess
                        )
                    }
                    if (!peakReason.isNullOrBlank()) {
                        Text(
                            text = peakReason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )
                    }
                    Button(
                        onClick = { onPlayClip(pipelineState.clip) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("play_generated_clip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(playButtonText)
                    }
                }
            }
        }
        is ClipPipelineState.Error -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurface,
                border = BorderStroke(1.5.dp, PeakRed)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = PeakRed
                    )
                    Text(
                        text = pipelineState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PeakRed
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBannerSurface(
    borderColor: Color,
    title: String,
    progress: Float?
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = borderColor,
                    trackColor = ObsidianBg
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = borderColor,
                    trackColor = ObsidianBg
                )
            }
        }
    }
}

@Composable
fun HypeMeterCard(
    countIn30s: Int,
    threshold: Int,
    hypeLevel: HypeLevel,
    velocityHistory: List<Int>,
    modifier: Modifier = Modifier
) {
    val ratio = (countIn30s.toFloat() / max(1, threshold).toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(),
        label = "hype_progress"
    )
    val targetColor = when (hypeLevel) {
        HypeLevel.PEAK_VIRAL -> HypeOrange
        HypeLevel.HIGH_VELOCITY -> PeakRed
        HypeLevel.BUILDING -> ElectricViolet
        HypeLevel.CALM -> MutedSlate
    }
    val accentColor by animateColorAsState(targetValue = targetColor, label = "hype_color")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioSurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Chat Hype Level",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = hypeLevel.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ElectricViolet, accentColor)
                            )
                        )
                )
            }

            if (velocityHistory.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(ObsidianBg)
                ) {
                    val maxVal = max(threshold, velocityHistory.maxOrNull() ?: 1).toFloat()
                    val stepX = size.width / max(1, velocityHistory.size - 1).toFloat()
                    val path = Path()
                    velocityHistory.forEachIndexed { idx, value ->
                        val x = idx * stepX
                        val y = size.height - (value / maxVal) * (size.height - 8f) - 4f
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = accentColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }
            }

            Text(
                text = "${(ratio * 100).toInt()}% hype • $countIn30s comments in last 30s (threshold $threshold)",
                style = MaterialTheme.typography.labelMedium,
                color = MutedSlate
            )
        }
    }
}

fun formatTime(seconds: Double): String {
    val total = max(0, seconds.toInt())
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
