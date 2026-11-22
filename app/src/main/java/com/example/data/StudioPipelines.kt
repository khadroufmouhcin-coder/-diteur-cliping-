package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.CaptionSegment
import com.example.model.CaptionStyle
import com.example.model.ClipPipelineState
import com.example.model.SavedClip
import com.example.model.TimeRangeSec
import com.example.model.TransitionType
import com.example.model.ViralAnalysisResult
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object StudioPipelines {

    suspend fun runMontageRenderPipeline(
        context: Context,
        segments: List<MontageSegmentSpec>,
        title: String,
        speed: Float,
        transition: TransitionType,
        verticalBlur: Boolean,
        enableCaptions: Boolean,
        captionStyle: CaptionStyle,
        enableHook: Boolean,
        hookText: String,
        hookDurationSec: Double?,
        musicUri: Uri?,
        musicVolume: Float,
        apiKey: String,
        modelName: String,
        aiRepository: AiViralRepository,
        onStatus: (ClipPipelineState) -> Unit
    ): Pair<SavedClip, ViralAnalysisResult> {
        require(segments.isNotEmpty()) { "Add at least one video clip first." }
        val totalOutputSec = max(1, (segments.sumOf { it.lengthSec } / speed.coerceIn(0.5f, 2.0f)).roundToInt())
        val needsAi = (enableCaptions || (enableHook && hookText.isBlank())) && apiKey.isNotBlank()

        var tempRawFile: File? = null
        try {
            val aiResult = if (needsAi) {
                tempRawFile = File(context.cacheDir, "montage_raw_${System.currentTimeMillis()}.mp4")
                onStatus(ClipPipelineState.RenderingClip(0.05f, "Joining segments for AI transcription..."))
                VerticalVideoRenderer.renderMontageJoin(
                    context = context,
                    segments = segments,
                    outputFile = tempRawFile,
                    verticalBlur = verticalBlur,
                    speed = speed,
                    transitionType = TransitionType.NONE,
                    enableCaptions = false,
                    captionStyle = captionStyle,
                    captions = emptyList(),
                    enableHook = false,
                    hookText = "",
                    hookDurationSec = null,
                    musicUri = null,
                    musicVolume = 0f,
                    onProgress = { p ->
                        onStatus(
                            ClipPipelineState.RenderingClip(
                                progress = p * 0.35f,
                                stepMessage = "Preparing joined track (${(p * 100).toInt()}%)..."
                            )
                        )
                    }
                )

                val joinedUri = Uri.fromFile(tempRawFile)
                onStatus(ClipPipelineState.AnalyzingAudio("Extracting montage audio track..."))
                val audioBytes = AudioPeakAnalyzer.extractAudioClipBytes(
                    context = context,
                    uri = joinedUri,
                    startSec = 0.0,
                    durationSec = totalOutputSec
                )
                val keyframes = if (audioBytes == null) {
                    AudioPeakAnalyzer.extractKeyFrames(
                        context = context,
                        uri = joinedUri,
                        startSec = 0.0,
                        durationSec = totalOutputSec
                    )
                } else emptyList()

                onStatus(ClipPipelineState.GeneratingCaptions("Generating synced captions & hook via Gemini..."))
                try {
                    aiRepository.analyzeClipAndGenerateCaptions(
                        apiKey = apiKey,
                        modelName = modelName,
                        audioBytes = audioBytes,
                        keyframes = keyframes,
                        videoTitle = title,
                        durationSec = totalOutputSec,
                        generateCaptions = enableCaptions,
                        karaokeMode = captionStyle == CaptionStyle.KARAOKE
                    )
                } finally {
                    keyframes.forEach { if (!it.isRecycled) it.recycle() }
                }
            } else {
                ViralAnalysisResult(
                    captions = emptyList(),
                    suggestedHook = hookText.ifBlank { "EPIC MONTAGE!" },
                    viralScore = 84,
                    peakReason = if (apiKey.isBlank() && enableCaptions) {
                        "Rendered offline without captions (no Gemini API key set)."
                    } else {
                        "Rendered ${segments.size} segments at ${"%.2f".format(speed)}x speed."
                    }
                )
            }

            val finalHook = if (enableHook && hookText.isBlank()) {
                aiResult.suggestedHook
            } else {
                hookText.trim()
            }

            val (clipId, outFile) = ClipStorageRepository.newClipOutputFile(context)
            onStatus(ClipPipelineState.RenderingClip(0.4f, "Encoding final 9:16 montage..."))

            val baseProgress = if (needsAi) 0.4f else 0.05f
            val spanProgress = 1f - baseProgress

            VerticalVideoRenderer.renderMontageJoin(
                context = context,
                segments = segments,
                outputFile = outFile,
                verticalBlur = verticalBlur,
                speed = speed,
                transitionType = transition,
                enableCaptions = enableCaptions && aiResult.captions.isNotEmpty(),
                captionStyle = captionStyle,
                captions = aiResult.captions,
                enableHook = enableHook && finalHook.isNotBlank(),
                hookText = finalHook,
                hookDurationSec = hookDurationSec,
                musicUri = musicUri,
                musicVolume = musicVolume,
                onProgress = { p ->
                    val overall = (baseProgress + p * spanProgress).coerceIn(0f, 0.99f)
                    onStatus(
                        ClipPipelineState.RenderingClip(
                            progress = overall,
                            stepMessage = "Rendering 9:16 montage (${(overall * 100).toInt()}%)..."
                        )
                    )
                }
            )

            val savedClip = SavedClip(
                id = clipId,
                filePath = outFile.absolutePath,
                title = title,
                hookText = if (enableHook) finalHook else "",
                durationSec = totalOutputSec,
                captionStyle = captionStyle,
                verticalBlur = verticalBlur,
                viralScore = aiResult.viralScore,
                createdAtMs = System.currentTimeMillis(),
                captions = aiResult.captions
            )
            ClipStorageRepository.saveClipMetadata(context, savedClip)
            return savedClip to aiResult
        } finally {
            runCatching {
                if (tempRawFile?.exists() == true) tempRawFile.delete()
            }
        }
    }

    suspend fun runSilenceCutPipeline(
        context: Context,
        inputUri: Uri,
        title: String,
        keepIntervals: List<TimeRangeSec>,
        originalDurationSec: Double,
        newDurationSec: Double,
        onStatus: (ClipPipelineState) -> Unit
    ): SavedClip {
        require(keepIntervals.isNotEmpty()) { "No active speech segments detected." }
        val (clipId, outFile) = ClipStorageRepository.newClipOutputFile(context)
        onStatus(ClipPipelineState.RenderingClip(0.05f, "Cutting ${keepIntervals.size} active speech segments..."))

        VerticalVideoRenderer.renderJumpCutVideo(
            context = context,
            inputUri = inputUri,
            keepIntervals = keepIntervals,
            outputFile = outFile,
            onProgress = { p ->
                onStatus(
                    ClipPipelineState.RenderingClip(
                        progress = p,
                        stepMessage = "Exporting jump-cut MP4 (${(p * 100).toInt()}%)..."
                    )
                )
            }
        )

        val savedPct = if (originalDurationSec > 0.0) {
            (((originalDurationSec - newDurationSec) / originalDurationSec) * 100.0).roundToInt().coerceIn(0, 100)
        } else 0

        val savedClip = SavedClip(
            id = clipId,
            filePath = outFile.absolutePath,
            title = "$title (No Silence)",
            hookText = "SILENCE REMOVED (-$savedPct%)",
            durationSec = max(1, newDurationSec.roundToInt()),
            captionStyle = CaptionStyle.MODERN_CLEAN,
            verticalBlur = false,
            viralScore = 88,
            createdAtMs = System.currentTimeMillis(),
            captions = emptyList<CaptionSegment>()
        )
        ClipStorageRepository.saveClipMetadata(context, savedClip)
        return savedClip
    }
}
