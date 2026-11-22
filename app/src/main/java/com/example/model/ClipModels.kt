package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class StudioToolMode(val label: String, val testTag: String) {
    VIRAL_CLIP("Viral Clip", "tool_mode_viral_clip"),
    MONTAGE("Montage", "tool_mode_montage"),
    AI_AUTO_MONTAGE("AI Auto-Montage", "tool_mode_ai_montage"),
    SILENCE_REMOVER("Silence Cut", "tool_mode_silence_remover")
}

enum class CaptionStyle(
    val displayName: String,
    val primaryColorInt: Int,
    val composeColorHex: Long
) {
    VIRAL_YELLOW(
        displayName = "Viral Yellow",
        primaryColorInt = 0xFFFACC15.toInt(),
        composeColorHex = 0xFFFACC15
    ),
    NEON_CYAN(
        displayName = "Neon Cyan",
        primaryColorInt = 0xFF22D3EE.toInt(),
        composeColorHex = 0xFF22D3EE
    ),
    MODERN_CLEAN(
        displayName = "Modern Clean",
        primaryColorInt = 0xFFFFFFFF.toInt(),
        composeColorHex = 0xFFF8FAFC
    ),
    KARAOKE(
        displayName = "Karaoke",
        primaryColorInt = 0xFFFACC15.toInt(),
        composeColorHex = 0xFFFACC15
    );

    companion object {
        fun fromName(name: String): CaptionStyle =
            entries.firstOrNull { it.name == name } ?: VIRAL_YELLOW
    }
}

enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    SLIDE("Slide"),
    WIPE("Wipe"),
    CIRCLE("Circle"),
    DISSOLVE("Dissolve")
}

enum class ClipDurationOption(val seconds: Int, val label: String) {
    SEC_10(10, "10s"),
    SEC_20(20, "20s"),
    SEC_30(30, "30s")
}

data class CaptionWord(
    val startSec: Double,
    val endSec: Double,
    val word: String
)

data class CaptionSegment(
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val words: List<CaptionWord> = emptyList()
)

data class ViralAnalysisResult(
    val captions: List<CaptionSegment>,
    val suggestedHook: String,
    val viralScore: Int,
    val peakReason: String
)

data class AudioEnergyProfile(
    val durationSec: Double,
    val secondEnergies: List<Float>,
    val peakStartSec: Double
)

data class MontageClipItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val durationSec: Double,
    val thumbnail: Bitmap?,
    val peakStartSec: Double
)

data class TimeRangeSec(
    val startSec: Double,
    val endSec: Double
) {
    val lengthSec: Double get() = (endSec - startSec).coerceAtLeast(0.0)
}

data class SilenceAnalysisResult(
    val originalDurationSec: Double,
    val newDurationSec: Double,
    val keepIntervals: List<TimeRangeSec>
)

data class YouTubeVideoInfo(
    val videoId: String,
    val originalUrl: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String,
    val isLive: Boolean,
    val hlsManifestUrl: String?,
    val liveChatContinuation: String?,
    val innertubeApiKey: String?
)

data class LiveChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val timestampMs: Long
)

enum class HypeLevel(val label: String) {
    CALM("CALM"),
    BUILDING("BUILDING MOMENTUM"),
    HIGH_VELOCITY("VERY HIGH VELOCITY"),
    PEAK_VIRAL("PEAK VIRAL HYPE")
}

data class LiveMomentCapture(
    val id: String,
    val videoId: String,
    val streamTitle: String,
    val capturedAtMs: Long,
    val chatVelocityPer30s: Int,
    val viralScore: Int,
    val hookTitle: String,
    val summary: String,
    val sampleMessages: List<String>,
    val hlsManifestUrl: String?
)

data class SavedClip(
    val id: String,
    val filePath: String,
    val title: String,
    val hookText: String,
    val durationSec: Int,
    val captionStyle: CaptionStyle,
    val verticalBlur: Boolean,
    val viralScore: Int,
    val createdAtMs: Long,
    val captions: List<CaptionSegment>
)

sealed interface ClipPipelineState {
    data object Idle : ClipPipelineState
    data class AnalyzingAudio(val stepMessage: String) : ClipPipelineState
    data class GeneratingCaptions(val stepMessage: String) : ClipPipelineState
    data class RenderingClip(val progress: Float, val stepMessage: String) : ClipPipelineState
    data class Success(val clip: SavedClip, val message: String) : ClipPipelineState
    data class Error(val message: String) : ClipPipelineState
}
