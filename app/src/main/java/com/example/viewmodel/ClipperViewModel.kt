package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.provider.OpenableColumns
import com.example.data.AiViralRepository
import com.example.data.ApiSettingsRepository
import com.example.data.AudioPeakAnalyzer
import com.example.data.ClipStorageRepository
import com.example.data.GeminiSettings
import com.example.data.MontageSegmentSpec
import com.example.data.StudioPipelines
import com.example.data.VerticalVideoRenderer
import com.example.data.YouTubeService
import com.example.model.AudioEnergyProfile
import com.example.model.CaptionSegment
import com.example.model.CaptionStyle
import com.example.model.ClipDurationOption
import com.example.model.ClipPipelineState
import com.example.model.HypeLevel
import com.example.model.LiveChatMessage
import com.example.model.LiveMomentCapture
import com.example.model.MontageClipItem
import com.example.model.SavedClip
import com.example.model.SilenceAnalysisResult
import com.example.model.StudioToolMode
import com.example.model.TransitionType
import com.example.model.YouTubeVideoInfo
import com.example.model.ViralAnalysisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ClipperViewModel"

data class SettingsUiState(
    val apiKey: String = "",
    val modelName: String = GeminiSettings.DEFAULT_GEMINI_MODEL,
    val isVerifying: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
}

data class ClipStudioUiState(
    val selectedVideoUri: Uri? = null,
    val videoTitle: String = "",
    val videoDurationSec: Double = 0.0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoThumbnail: Bitmap? = null,
    val youtubeUrlInput: String = "",
    val isAnalyzingYoutubeUrl: Boolean = false,
    val youtubeVideoInfo: YouTubeVideoInfo? = null,
    val youtubeStatusMessage: String? = null,
    val energyProfile: AudioEnergyProfile? = null,
    val isAnalyzingEnergy: Boolean = false,
    val selectedStartSec: Double = 0.0,
    val durationOption: ClipDurationOption = ClipDurationOption.SEC_30,
    val verticalBlur: Boolean = true,
    val enableCaptions: Boolean = true,
    val captionStyle: CaptionStyle = CaptionStyle.VIRAL_YELLOW,
    val enableHook: Boolean = true,
    val hookText: String = "WAIT FOR IT!",
    val isSuggestingHook: Boolean = false,
    val generatedCaptions: List<CaptionSegment> = emptyList(),
    val lastViralScore: Int? = null,
    val lastPeakReason: String? = null,
    val pipelineState: ClipPipelineState = ClipPipelineState.Idle
)

data class LiveMonitorUiState(
    val liveUrlInput: String = "",
    val durationOption: ClipDurationOption = ClipDurationOption.SEC_30,
    val hypeThreshold: Int = 30,
    val maxWaitSeconds: Int = 180,
    val verticalBlur: Boolean = true,
    val enableCaptions: Boolean = true,
    val captionStyle: CaptionStyle = CaptionStyle.NEON_CYAN,
    val enableHook: Boolean = true,
    val hookText: String = "INSANE MOMENT!",
    val isMonitoring: Boolean = false,
    val isConnecting: Boolean = false,
    val isAnalyzingBurst: Boolean = false,
    val elapsedSeconds: Int = 0,
    val messagesInLast30s: Int = 0,
    val hypeLevel: HypeLevel = HypeLevel.CALM,
    val velocityHistory: List<Int> = emptyList(),
    val recentMessages: List<LiveChatMessage> = emptyList(),
    val streamInfo: YouTubeVideoInfo? = null,
    val statusMessage: String = "Paste a live YouTube stream URL to track real-time chat velocity.",
    val errorMessage: String? = null,
    val capturedMoments: List<LiveMomentCapture> = emptyList()
)

data class MontageUiState(
    val clips: List<MontageClipItem> = emptyList(),
    val isLoadingClips: Boolean = false,
    val segLenSec: Int = 5,
    val autoBestMoment: Boolean = true,
    val speed: Float = 1.0f,
    val transition: TransitionType = TransitionType.FADE,
    val verticalBlur: Boolean = true,
    val enableCaptions: Boolean = false,
    val captionStyle: CaptionStyle = CaptionStyle.VIRAL_YELLOW,
    val enableHook: Boolean = false,
    val hookText: String = "",
    val musicUri: Uri? = null,
    val musicTitle: String? = null,
    val musicVolume: Float = 0.25f,
    val lastPeakReason: String? = null,
    val pipelineState: ClipPipelineState = ClipPipelineState.Idle
)

data class AiAutoMontageUiState(
    val selectedVideoUri: Uri? = null,
    val videoTitle: String = "",
    val videoDurationSec: Double = 0.0,
    val videoThumbnail: Bitmap? = null,
    val youtubeUrlInput: String = "",
    val isAnalyzingYoutubeUrl: Boolean = false,
    val youtubeVideoInfo: YouTubeVideoInfo? = null,
    val youtubeStatusMessage: String? = null,
    val nClips: Int = 5,
    val segLenSec: Int = 8,
    val speed: Float = 1.0f,
    val transition: TransitionType = TransitionType.FADE,
    val verticalBlur: Boolean = true,
    val enableCaptions: Boolean = true,
    val captionStyle: CaptionStyle = CaptionStyle.KARAOKE,
    val autoHook: Boolean = true,
    val hookText: String = "",
    val musicUri: Uri? = null,
    val musicTitle: String? = null,
    val musicVolume: Float = 0.25f,
    val pickedHighlightsSec: List<Double> = emptyList(),
    val lastPeakReason: String? = null,
    val pipelineState: ClipPipelineState = ClipPipelineState.Idle
)

data class SilenceRemoverUiState(
    val selectedVideoUri: Uri? = null,
    val videoTitle: String = "",
    val videoDurationSec: Double = 0.0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoThumbnail: Bitmap? = null,
    val noiseDb: Int = -35,
    val minSilenceSec: Float = 0.5f,
    val isAnalyzing: Boolean = false,
    val analysisResult: SilenceAnalysisResult? = null,
    val pipelineState: ClipPipelineState = ClipPipelineState.Idle
)

data class VaultUiState(
    val clips: List<SavedClip> = emptyList(),
    val activePlayerClip: SavedClip? = null,
    val feedbackMessage: String? = null
)

class ClipperViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AiViralRepository()
    private val youTubeService = YouTubeService()
    private val settingsRepository = ApiSettingsRepository(application)

    private val _selectedToolMode = MutableStateFlow(StudioToolMode.VIRAL_CLIP)
    val selectedToolMode: StateFlow<StudioToolMode> = _selectedToolMode.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _studioState = MutableStateFlow(ClipStudioUiState())
    val studioState: StateFlow<ClipStudioUiState> = _studioState.asStateFlow()

    private val _montageState = MutableStateFlow(MontageUiState())
    val montageState: StateFlow<MontageUiState> = _montageState.asStateFlow()

    private val _aiMontageState = MutableStateFlow(AiAutoMontageUiState())
    val aiMontageState: StateFlow<AiAutoMontageUiState> = _aiMontageState.asStateFlow()

    private val _silenceState = MutableStateFlow(SilenceRemoverUiState())
    val silenceState: StateFlow<SilenceRemoverUiState> = _silenceState.asStateFlow()

    private val _liveState = MutableStateFlow(LiveMonitorUiState())
    val liveState: StateFlow<LiveMonitorUiState> = _liveState.asStateFlow()

    private val _vaultState = MutableStateFlow(VaultUiState())
    val vaultState: StateFlow<VaultUiState> = _vaultState.asStateFlow()

    private var liveMonitorJob: Job? = null
    private var clipGenerationJob: Job? = null
    private var montageJob: Job? = null
    private var aiMontageJob: Job? = null
    private var silenceJob: Job? = null

    fun selectToolMode(mode: StudioToolMode) {
        _selectedToolMode.value = mode
    }

    init {
        refreshVault()
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { saved ->
                _settingsState.update {
                    it.copy(
                        apiKey = saved.apiKey,
                        modelName = saved.modelName
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Settings Actions
    // -------------------------------------------------------------------------

    fun saveApiSettings(apiKey: String, modelName: String) {
        viewModelScope.launch {
            settingsRepository.saveSettings(apiKey, modelName)
            _settingsState.update {
                it.copy(
                    statusMessage = if (apiKey.isBlank()) "Settings saved (no API key set)." else "API key saved locally.",
                    isError = false
                )
            }
        }
    }

    fun saveAndVerifyApiSettings(apiKey: String, modelName: String) {
        viewModelScope.launch {
            settingsRepository.saveSettings(apiKey, modelName)
            _settingsState.update {
                it.copy(isVerifying = true, statusMessage = null, isError = false)
            }
            try {
                val result = aiRepository.verifyApiKey(apiKey, modelName)
                _settingsState.update {
                    it.copy(
                        isVerifying = false,
                        statusMessage = result,
                        isError = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "API key verification failed", e)
                _settingsState.update {
                    it.copy(
                        isVerifying = false,
                        statusMessage = e.localizedMessage ?: "Failed to verify Gemini API key.",
                        isError = true
                    )
                }
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settingsRepository.clearApiKey()
            _settingsState.update {
                it.copy(
                    statusMessage = "API key cleared.",
                    isError = false
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Video Clipper Studio Actions
    // -------------------------------------------------------------------------

    fun onVideoPicked(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    selectedVideoUri = uri,
                    isAnalyzingEnergy = true,
                    pipelineState = ClipPipelineState.Idle,
                    youtubeStatusMessage = null,
                    generatedCaptions = emptyList(),
                    lastViralScore = null,
                    lastPeakReason = null
                )
            }
            try {
                val meta = AudioPeakAnalyzer.getVideoMetadata(context, uri)
                _studioState.update {
                    it.copy(
                        videoTitle = meta.displayName.substringBeforeLast("."),
                        videoDurationSec = meta.durationSec,
                        videoWidth = meta.width,
                        videoHeight = meta.height,
                        videoThumbnail = meta.thumbnail
                    )
                }
                val clipSec = _studioState.value.durationOption.seconds
                val profile = AudioPeakAnalyzer.analyzeAudioEnergy(context, uri, clipSec)
                _studioState.update {
                    it.copy(
                        energyProfile = profile,
                        selectedStartSec = profile.peakStartSec,
                        isAnalyzingEnergy = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading picked video", e)
                _studioState.update {
                    it.copy(
                        isAnalyzingEnergy = false,
                        pipelineState = ClipPipelineState.Error(
                            e.localizedMessage ?: "Failed to read selected video."
                        )
                    )
                }
            }
        }
    }

    fun updateYoutubeUrlInput(url: String) {
        _studioState.update { it.copy(youtubeUrlInput = url) }
    }

    fun analyzeYouTubeUrl() {
        val url = _studioState.value.youtubeUrlInput.trim()
        if (url.isEmpty()) {
            _studioState.update {
                it.copy(youtubeStatusMessage = "Enter a YouTube URL to inspect.")
            }
            return
        }
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    isAnalyzingYoutubeUrl = true,
                    youtubeStatusMessage = "Fetching YouTube metadata..."
                )
            }
            try {
                val info = youTubeService.fetchVideoInfo(url)
                val settings = _settingsState.value
                val suggestedHook = if (settings.hasApiKey) {
                    runCatching {
                        aiRepository.suggestViralHook(
                            apiKey = settings.apiKey,
                            modelName = settings.modelName,
                            videoTitle = info.title,
                            extraContext = "Channel: ${info.authorName}"
                        )
                    }.getOrNull()
                } else null

                val status = if (info.hlsManifestUrl != null) {
                    "Stream detected! Ready to clip or monitor live hype."
                } else {
                    "Loaded \"${info.title}\". Pick a downloaded video file from your device below to render an offline 9:16 MP4."
                }
                _studioState.update {
                    it.copy(
                        isAnalyzingYoutubeUrl = false,
                        youtubeVideoInfo = info,
                        videoTitle = if (it.selectedVideoUri == null) info.title else it.videoTitle,
                        hookText = suggestedHook ?: it.hookText,
                        youtubeStatusMessage = status
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "YouTube URL lookup failed", e)
                _studioState.update {
                    it.copy(
                        isAnalyzingYoutubeUrl = false,
                        youtubeStatusMessage = e.localizedMessage ?: "Could not inspect YouTube link."
                    )
                }
            }
        }
    }

    fun selectDuration(option: ClipDurationOption) {
        val state = _studioState.value
        val profile = state.energyProfile
        if (profile == null) {
            _studioState.update { it.copy(durationOption = option) }
            return
        }
        val energies = profile.secondEnergies
        val dur = option.seconds
        var bestStart = 0
        var bestMean = -1f
        val limit = max(1, energies.size - dur)
        for (s in 0 until limit) {
            val end = min(energies.size, s + dur)
            var sum = 0f
            for (k in s until end) sum += energies[k]
            val mean = sum / max(1, end - s)
            if (mean > bestMean) {
                bestMean = mean
                bestStart = s
            }
        }
        val peakStart = max(0.0, (bestStart - 5).toDouble())
            .coerceAtMost(max(0.0, profile.durationSec - dur))

        _studioState.update {
            it.copy(
                durationOption = option,
                selectedStartSec = peakStart,
                energyProfile = profile.copy(peakStartSec = peakStart)
            )
        }
    }

    fun updateStartSec(startSec: Double) {
        val maxStart = max(0.0, _studioState.value.videoDurationSec - _studioState.value.durationOption.seconds)
        _studioState.update {
            it.copy(selectedStartSec = startSec.coerceIn(0.0, maxStart))
        }
    }

    fun toggleVerticalBlur(enabled: Boolean) {
        _studioState.update { it.copy(verticalBlur = enabled) }
    }

    fun toggleCaptions(enabled: Boolean) {
        _studioState.update { it.copy(enableCaptions = enabled) }
    }

    fun selectCaptionStyle(style: CaptionStyle) {
        _studioState.update { it.copy(captionStyle = style) }
    }

    fun toggleHook(enabled: Boolean) {
        _studioState.update { it.copy(enableHook = enabled) }
    }

    fun updateHookText(text: String) {
        _studioState.update { it.copy(hookText = text) }
    }

    fun suggestHookWithAi() {
        val settings = _settingsState.value
        if (!settings.hasApiKey) {
            _studioState.update {
                it.copy(
                    pipelineState = ClipPipelineState.Error(
                        "Gemini API key is not set. Open Settings to enter your API key."
                    )
                )
            }
            return
        }
        val state = _studioState.value
        val title = state.videoTitle.ifBlank { state.youtubeVideoInfo?.title ?: "Viral Clip" }
        viewModelScope.launch {
            _studioState.update { it.copy(isSuggestingHook = true) }
            try {
                val hook = aiRepository.suggestViralHook(
                    apiKey = settings.apiKey,
                    modelName = settings.modelName,
                    videoTitle = title,
                    extraContext = "Duration ${state.durationOption.seconds}s short clip"
                )
                _studioState.update { it.copy(hookText = hook, isSuggestingHook = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI hook suggestion failed", e)
                _studioState.update {
                    it.copy(
                        isSuggestingHook = false,
                        pipelineState = ClipPipelineState.Error(
                            e.localizedMessage ?: "AI hook suggestion failed."
                        )
                    )
                }
            }
        }
    }

    fun generateViralClip() {
        val state = _studioState.value
        val settings = _settingsState.value
        val sourceUri = state.selectedVideoUri ?: state.youtubeVideoInfo?.hlsManifestUrl?.let { Uri.parse(it) }
        if (sourceUri == null) {
            _studioState.update {
                it.copy(
                    pipelineState = ClipPipelineState.Error(
                        "Pick a video from your device first to render a 9:16 MP4 short."
                    )
                )
            }
            return
        }

        if (state.enableCaptions && !settings.hasApiKey) {
            _studioState.update {
                it.copy(
                    pipelineState = ClipPipelineState.Error(
                        "Gemini API key is required for AI Auto-Captions. Enter your key in Settings, or turn off Auto-Captions to render offline."
                    )
                )
            }
            return
        }

        clipGenerationJob?.cancel()
        clipGenerationJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val actualDuration = if (state.videoDurationSec > 0.5) {
                    min(state.durationOption.seconds.toDouble(), state.videoDurationSec).toInt()
                        .coerceAtLeast(1)
                } else {
                    state.durationOption.seconds
                }

                val aiResult = if (settings.hasApiKey) {
                    _studioState.update {
                        it.copy(
                            pipelineState = ClipPipelineState.AnalyzingAudio(
                                "Extracting peak window (${state.selectedStartSec.toInt()}s)..."
                            )
                        )
                    }
                    val audioBytes = if (state.selectedVideoUri != null) {
                        AudioPeakAnalyzer.extractAudioClipBytes(
                            context = context,
                            uri = sourceUri,
                            startSec = state.selectedStartSec,
                            durationSec = actualDuration
                        )
                    } else null

                    val keyframes = if (audioBytes == null && state.selectedVideoUri != null) {
                        AudioPeakAnalyzer.extractKeyFrames(
                            context = context,
                            uri = sourceUri,
                            startSec = state.selectedStartSec,
                            durationSec = actualDuration
                        )
                    } else emptyList()

                    _studioState.update {
                        it.copy(
                            pipelineState = ClipPipelineState.GeneratingCaptions(
                                if (state.enableCaptions) "Generating AI auto-captions via Gemini REST API..."
                                else "Analyzing viral moment via Gemini REST API..."
                            )
                        )
                    }

                    try {
                        aiRepository.analyzeClipAndGenerateCaptions(
                            apiKey = settings.apiKey,
                            modelName = settings.modelName,
                            audioBytes = audioBytes,
                            keyframes = keyframes,
                            videoTitle = state.videoTitle.ifBlank { "Viral Clip" },
                            durationSec = actualDuration,
                            generateCaptions = state.enableCaptions,
                            karaokeMode = state.captionStyle == CaptionStyle.KARAOKE
                        )
                    } finally {
                        keyframes.forEach { if (!it.isRecycled) it.recycle() }
                    }
                } else {
                    ViralAnalysisResult(
                        captions = emptyList(),
                        suggestedHook = state.hookText.ifBlank { "WAIT FOR IT!" },
                        viralScore = 80,
                        peakReason = "Clipped at audio RMS peak window (offline mode)."
                    )
                }

                val finalHook = if (state.enableHook && state.hookText.isBlank()) {
                    aiResult.suggestedHook
                } else {
                    state.hookText
                }

                _studioState.update {
                    it.copy(
                        generatedCaptions = aiResult.captions,
                        lastViralScore = aiResult.viralScore,
                        lastPeakReason = aiResult.peakReason,
                        hookText = finalHook
                    )
                }

                val (clipId, outFile) = ClipStorageRepository.newClipOutputFile(context)
                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.RenderingClip(
                            progress = 0.05f,
                            stepMessage = "Rendering 1080x1920 9:16 short..."
                        )
                    )
                }

                VerticalVideoRenderer.renderVerticalClip(
                    context = context,
                    inputUri = sourceUri,
                    outputFile = outFile,
                    startSec = state.selectedStartSec,
                    durationSec = actualDuration,
                    verticalBlur = state.verticalBlur,
                    enableCaptions = state.enableCaptions,
                    captionStyle = state.captionStyle,
                    captions = aiResult.captions,
                    enableHook = state.enableHook,
                    hookText = finalHook,
                    onProgress = { progress ->
                        _studioState.update { current ->
                            if (current.pipelineState is ClipPipelineState.RenderingClip) {
                                current.copy(
                                    pipelineState = ClipPipelineState.RenderingClip(
                                        progress = progress,
                                        stepMessage = "Encoding 9:16 video (${(progress * 100).toInt()}%)..."
                                    )
                                )
                            } else current
                        }
                    }
                )

                val savedClip = SavedClip(
                    id = clipId,
                    filePath = outFile.absolutePath,
                    title = state.videoTitle.ifBlank { "Viral Short" },
                    hookText = if (state.enableHook) finalHook else "",
                    durationSec = actualDuration,
                    captionStyle = state.captionStyle,
                    verticalBlur = state.verticalBlur,
                    viralScore = aiResult.viralScore,
                    createdAtMs = System.currentTimeMillis(),
                    captions = aiResult.captions
                )
                ClipStorageRepository.saveClipMetadata(context, savedClip)
                refreshVault()

                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.Success(
                            clip = savedClip,
                            message = "Ready! Clipped from ${state.selectedStartSec.toInt()}s • Viral Score ${aiResult.viralScore}/100"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clip generation pipeline failed", e)
                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.Error(
                            e.localizedMessage ?: "Failed to generate clip."
                        )
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Multi-Clip Montage Actions
    // -------------------------------------------------------------------------

    fun onMontageVideosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _montageState.update { it.copy(isLoadingClips = true, pipelineState = ClipPipelineState.Idle) }
            try {
                val segLen = _montageState.value.segLenSec
                val added = uris.map { uri ->
                    val meta = AudioPeakAnalyzer.getVideoMetadata(context, uri)
                    val peak = if (meta.durationSec > segLen) {
                        AudioPeakAnalyzer.analyzeAudioEnergy(context, uri, segLen).peakStartSec
                    } else 0.0
                    MontageClipItem(
                        id = UUID.randomUUID().toString().take(8),
                        uri = uri,
                        displayName = meta.displayName.substringBeforeLast("."),
                        durationSec = meta.durationSec,
                        thumbnail = meta.thumbnail,
                        peakStartSec = peak
                    )
                }
                _montageState.update {
                    it.copy(
                        clips = it.clips + added,
                        isLoadingClips = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed adding montage videos", e)
                _montageState.update {
                    it.copy(
                        isLoadingClips = false,
                        pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "Failed loading selected clips.")
                    )
                }
            }
        }
    }

    fun removeMontageClip(clipId: String) {
        _montageState.update { s -> s.copy(clips = s.clips.filterNot { it.id == clipId }) }
    }

    fun moveMontageClip(clipId: String, delta: Int) {
        _montageState.update { s ->
            val list = s.clips.toMutableList()
            val idx = list.indexOfFirst { it.id == clipId }
            val target = idx + delta
            if (idx >= 0 && target in list.indices) {
                val item = list.removeAt(idx)
                list.add(target, item)
            }
            s.copy(clips = list)
        }
    }

    fun updateMontageSegLen(sec: Int) {
        _montageState.update { it.copy(segLenSec = sec.coerceIn(3, 30)) }
    }

    fun toggleMontageAutoBest(enabled: Boolean) {
        _montageState.update { it.copy(autoBestMoment = enabled) }
    }

    fun updateMontageSpeed(speed: Float) {
        _montageState.update { it.copy(speed = speed.coerceIn(0.5f, 2.0f)) }
    }

    fun selectMontageTransition(transition: TransitionType) {
        _montageState.update { it.copy(transition = transition) }
    }

    fun toggleMontageBlur(enabled: Boolean) {
        _montageState.update { it.copy(verticalBlur = enabled) }
    }

    fun toggleMontageCaptions(enabled: Boolean) {
        _montageState.update { it.copy(enableCaptions = enabled) }
    }

    fun selectMontageCaptionStyle(style: CaptionStyle) {
        _montageState.update { it.copy(captionStyle = style) }
    }

    fun toggleMontageHook(enabled: Boolean) {
        _montageState.update { it.copy(enableHook = enabled) }
    }

    fun updateMontageHookText(text: String) {
        _montageState.update { it.copy(hookText = text) }
    }

    fun onMontageMusicPicked(uri: Uri) {
        val title = resolveUriDisplayName(uri) ?: "Background Audio"
        _montageState.update { it.copy(musicUri = uri, musicTitle = title) }
    }

    fun clearMontageMusic() {
        _montageState.update { it.copy(musicUri = null, musicTitle = null) }
    }

    fun updateMontageMusicVol(vol: Float) {
        _montageState.update { it.copy(musicVolume = vol.coerceIn(0f, 1f)) }
    }

    fun generateMontage() {
        val state = _montageState.value
        val settings = _settingsState.value
        if (state.clips.isEmpty()) {
            _montageState.update {
                it.copy(pipelineState = ClipPipelineState.Error("Add at least 1 video clip to build a montage."))
            }
            return
        }
        if (state.enableCaptions && !settings.hasApiKey) {
            _montageState.update {
                it.copy(
                    pipelineState = ClipPipelineState.Error(
                        "Gemini API key is required for AI Auto-Captions. Enter your key in Settings or disable captions."
                    )
                )
            }
            return
        }

        montageJob?.cancel()
        montageJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                _montageState.update {
                    it.copy(pipelineState = ClipPipelineState.AnalyzingAudio("Inspecting clip audio peaks..."))
                }
                val specs = state.clips.map { clip ->
                    val len = min(state.segLenSec.toDouble(), max(0.5, clip.durationSec))
                    val start = if (state.autoBestMoment && clip.durationSec > len) {
                        clip.peakStartSec.coerceIn(0.0, max(0.0, clip.durationSec - len))
                    } else 0.0
                    MontageSegmentSpec(
                        uri = clip.uri,
                        startSec = start,
                        lengthSec = len,
                        hasAudio = AudioPeakAnalyzer.hasAudioTrack(context, clip.uri)
                    )
                }

                val title = if (state.clips.size == 1) state.clips.first().displayName
                else "Montage (${state.clips.size} clips)"

                val (saved, aiResult) = StudioPipelines.runMontageRenderPipeline(
                    context = context,
                    segments = specs,
                    title = title,
                    speed = state.speed,
                    transition = state.transition,
                    verticalBlur = state.verticalBlur,
                    enableCaptions = state.enableCaptions,
                    captionStyle = state.captionStyle,
                    enableHook = state.enableHook,
                    hookText = state.hookText,
                    hookDurationSec = null,
                    musicUri = state.musicUri,
                    musicVolume = state.musicVolume,
                    apiKey = settings.apiKey,
                    modelName = settings.modelName,
                    aiRepository = aiRepository,
                    onStatus = { status ->
                        _montageState.update { it.copy(pipelineState = status) }
                    }
                )
                refreshVault()
                _montageState.update {
                    it.copy(
                        lastPeakReason = aiResult.peakReason,
                        pipelineState = ClipPipelineState.Success(
                            clip = saved,
                            message = "Montage Ready! ${specs.size} clips • ${saved.durationSec}s"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Montage render failed", e)
                _montageState.update {
                    it.copy(pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "Failed to render montage."))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // AI Auto-Montage Actions (1 Long Video -> Highlight Mashup)
    // -------------------------------------------------------------------------

    fun onAiMontageVideoPicked(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _aiMontageState.update {
                it.copy(
                    selectedVideoUri = uri,
                    pickedHighlightsSec = emptyList(),
                    pipelineState = ClipPipelineState.Idle
                )
            }
            try {
                val meta = AudioPeakAnalyzer.getVideoMetadata(context, uri)
                _aiMontageState.update {
                    it.copy(
                        videoTitle = meta.displayName.substringBeforeLast("."),
                        videoDurationSec = meta.durationSec,
                        videoThumbnail = meta.thumbnail
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading video for AI Auto-Montage", e)
                _aiMontageState.update {
                    it.copy(pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "Failed to read video."))
                }
            }
        }
    }

    fun updateAiMontageYoutubeUrl(url: String) {
        _aiMontageState.update { it.copy(youtubeUrlInput = url) }
    }

    fun analyzeAiMontageYoutubeUrl() {
        val url = _aiMontageState.value.youtubeUrlInput.trim()
        if (url.isEmpty()) {
            _aiMontageState.update { it.copy(youtubeStatusMessage = "Enter a YouTube URL to inspect.") }
            return
        }
        viewModelScope.launch {
            _aiMontageState.update {
                it.copy(isAnalyzingYoutubeUrl = true, youtubeStatusMessage = "Fetching YouTube info...")
            }
            try {
                val info = youTubeService.fetchVideoInfo(url)
                val settings = _settingsState.value
                val hook = if (settings.hasApiKey) {
                    runCatching {
                        aiRepository.suggestViralHook(
                            apiKey = settings.apiKey,
                            modelName = settings.modelName,
                            videoTitle = info.title,
                            extraContext = "AI Auto-Montage Highlight Mashup"
                        )
                    }.getOrNull()
                } else null
                _aiMontageState.update {
                    it.copy(
                        isAnalyzingYoutubeUrl = false,
                        youtubeVideoInfo = info,
                        videoTitle = if (it.selectedVideoUri == null) info.title else it.videoTitle,
                        hookText = hook ?: it.hookText,
                        youtubeStatusMessage = "Loaded \"${info.title}\". Pick the downloaded video below to extract highlights."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiMontageState.update {
                    it.copy(
                        isAnalyzingYoutubeUrl = false,
                        youtubeStatusMessage = e.localizedMessage ?: "Failed inspecting YouTube URL."
                    )
                }
            }
        }
    }

    fun updateAiMontageNClips(n: Int) {
        _aiMontageState.update { it.copy(nClips = n.coerceIn(2, 10)) }
    }

    fun updateAiMontageSegLen(sec: Int) {
        _aiMontageState.update { it.copy(segLenSec = sec.coerceIn(4, 20)) }
    }

    fun updateAiMontageSpeed(speed: Float) {
        _aiMontageState.update { it.copy(speed = speed.coerceIn(0.5f, 2.0f)) }
    }

    fun selectAiMontageTransition(transition: TransitionType) {
        _aiMontageState.update { it.copy(transition = transition) }
    }

    fun toggleAiMontageBlur(enabled: Boolean) {
        _aiMontageState.update { it.copy(verticalBlur = enabled) }
    }

    fun toggleAiMontageCaptions(enabled: Boolean) {
        _aiMontageState.update { it.copy(enableCaptions = enabled) }
    }

    fun selectAiMontageCaptionStyle(style: CaptionStyle) {
        _aiMontageState.update { it.copy(captionStyle = style) }
    }

    fun toggleAiMontageAutoHook(enabled: Boolean) {
        _aiMontageState.update { it.copy(autoHook = enabled) }
    }

    fun updateAiMontageHookText(text: String) {
        _aiMontageState.update { it.copy(hookText = text) }
    }

    fun onAiMontageMusicPicked(uri: Uri) {
        val title = resolveUriDisplayName(uri) ?: "Background Audio"
        _aiMontageState.update { it.copy(musicUri = uri, musicTitle = title) }
    }

    fun clearAiMontageMusic() {
        _aiMontageState.update { it.copy(musicUri = null, musicTitle = null) }
    }

    fun updateAiMontageMusicVol(vol: Float) {
        _aiMontageState.update { it.copy(musicVolume = vol.coerceIn(0f, 1f)) }
    }

    fun generateAiAutoMontage() {
        val state = _aiMontageState.value
        val settings = _settingsState.value
        val uri = state.selectedVideoUri
        if (uri == null) {
            _aiMontageState.update {
                it.copy(pipelineState = ClipPipelineState.Error("Pick a long video from your device first."))
            }
            return
        }
        if (state.enableCaptions && !settings.hasApiKey) {
            _aiMontageState.update {
                it.copy(
                    pipelineState = ClipPipelineState.Error(
                        "Gemini API key is required for AI Auto-Captions. Enter your key in Settings or disable captions."
                    )
                )
            }
            return
        }

        aiMontageJob?.cancel()
        aiMontageJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                _aiMontageState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.AnalyzingAudio(
                            "Scoring 70% audio RMS + 30% scene action for top ${state.nClips} highlights..."
                        )
                    )
                }

                val starts = AudioPeakAnalyzer.analyzeHighlights(
                    context = context,
                    uri = uri,
                    nClips = state.nClips,
                    segLenSec = state.segLenSec
                )
                val hasAudio = AudioPeakAnalyzer.hasAudioTrack(context, uri)
                val segLen = min(state.segLenSec.toDouble(), max(1.0, state.videoDurationSec))
                val specs = starts.map { s ->
                    MontageSegmentSpec(
                        uri = uri,
                        startSec = s,
                        lengthSec = min(segLen, max(0.5, state.videoDurationSec - s)),
                        hasAudio = hasAudio
                    )
                }

                _aiMontageState.update { it.copy(pickedHighlightsSec = starts) }

                val (saved, aiResult) = StudioPipelines.runMontageRenderPipeline(
                    context = context,
                    segments = specs,
                    title = "${state.videoTitle.ifBlank { "Video" }} Mashup",
                    speed = state.speed,
                    transition = state.transition,
                    verticalBlur = state.verticalBlur,
                    enableCaptions = state.enableCaptions,
                    captionStyle = state.captionStyle,
                    enableHook = state.autoHook,
                    hookText = state.hookText,
                    hookDurationSec = 4.0,
                    musicUri = state.musicUri,
                    musicVolume = state.musicVolume,
                    apiKey = settings.apiKey,
                    modelName = settings.modelName,
                    aiRepository = aiRepository,
                    onStatus = { status ->
                        _aiMontageState.update { it.copy(pipelineState = status) }
                    }
                )

                refreshVault()
                val spots = starts.joinToString(", ") { "${it.toInt()}s" }
                _aiMontageState.update {
                    it.copy(
                        hookText = saved.hookText.ifBlank { it.hookText },
                        lastPeakReason = "${aiResult.peakReason} • Highlights: $spots",
                        pipelineState = ClipPipelineState.Success(
                            clip = saved,
                            message = "AI Auto-Montage Ready! ${starts.size} highlights (${saved.durationSec}s)"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Auto-Montage failed", e)
                _aiMontageState.update {
                    it.copy(pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "AI Auto-Montage failed."))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Silence Remover Actions
    // -------------------------------------------------------------------------

    fun onSilenceVideoPicked(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _silenceState.update {
                it.copy(
                    selectedVideoUri = uri,
                    isAnalyzing = true,
                    analysisResult = null,
                    pipelineState = ClipPipelineState.Idle
                )
            }
            try {
                val meta = AudioPeakAnalyzer.getVideoMetadata(context, uri)
                _silenceState.update {
                    it.copy(
                        videoTitle = meta.displayName.substringBeforeLast("."),
                        videoDurationSec = meta.durationSec,
                        videoWidth = meta.width,
                        videoHeight = meta.height,
                        videoThumbnail = meta.thumbnail
                    )
                }
                val result = AudioPeakAnalyzer.detectSpeechKeepIntervals(
                    context = context,
                    uri = uri,
                    noiseDb = _silenceState.value.noiseDb,
                    minSilenceSec = _silenceState.value.minSilenceSec.toDouble()
                )
                _silenceState.update { it.copy(isAnalyzing = false, analysisResult = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Silence scan failed", e)
                _silenceState.update {
                    it.copy(
                        isAnalyzing = false,
                        pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "Failed scanning video audio.")
                    )
                }
            }
        }
    }

    fun updateSilenceNoiseDb(db: Int) {
        _silenceState.update { it.copy(noiseDb = db.coerceIn(-60, -20)) }
    }

    fun updateSilenceMinSec(sec: Float) {
        _silenceState.update { it.copy(minSilenceSec = sec.coerceIn(0.3f, 2.0f)) }
    }

    fun analyzeSilencePreview() {
        val state = _silenceState.value
        val uri = state.selectedVideoUri ?: return
        viewModelScope.launch {
            _silenceState.update { it.copy(isAnalyzing = true, pipelineState = ClipPipelineState.Idle) }
            try {
                val result = AudioPeakAnalyzer.detectSpeechKeepIntervals(
                    context = getApplication(),
                    uri = uri,
                    noiseDb = state.noiseDb,
                    minSilenceSec = state.minSilenceSec.toDouble()
                )
                _silenceState.update { it.copy(isAnalyzing = false, analysisResult = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _silenceState.update {
                    it.copy(
                        isAnalyzing = false,
                        pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "Silence scan failed.")
                    )
                }
            }
        }
    }

    fun removeSilence() {
        val state = _silenceState.value
        val uri = state.selectedVideoUri
        if (uri == null) {
            _silenceState.update {
                it.copy(pipelineState = ClipPipelineState.Error("Pick a video from your device first."))
            }
            return
        }

        silenceJob?.cancel()
        silenceJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                _silenceState.update {
                    it.copy(pipelineState = ClipPipelineState.AnalyzingAudio("Detecting silence below ${state.noiseDb} dBFS..."))
                }
                val analysis = AudioPeakAnalyzer.detectSpeechKeepIntervals(
                    context = context,
                    uri = uri,
                    noiseDb = state.noiseDb,
                    minSilenceSec = state.minSilenceSec.toDouble()
                )
                _silenceState.update { it.copy(analysisResult = analysis) }

                val saved = StudioPipelines.runSilenceCutPipeline(
                    context = context,
                    inputUri = uri,
                    title = state.videoTitle.ifBlank { "Video" },
                    keepIntervals = analysis.keepIntervals,
                    originalDurationSec = analysis.originalDurationSec,
                    newDurationSec = analysis.newDurationSec,
                    onStatus = { status ->
                        _silenceState.update { it.copy(pipelineState = status) }
                    }
                )
                refreshVault()
                val savedPct = if (analysis.originalDurationSec > 0.0) {
                    (((analysis.originalDurationSec - analysis.newDurationSec) / analysis.originalDurationSec) * 100.0).toInt()
                } else 0
                _silenceState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.Success(
                            clip = saved,
                            message = "Silence Removed! ${analysis.originalDurationSec.toInt()}s → ${analysis.newDurationSec.toInt()}s (-$savedPct%)"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Silence removal failed", e)
                _silenceState.update {
                    it.copy(pipelineState = ClipPipelineState.Error(e.localizedMessage ?: "Failed removing silence."))
                }
            }
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String? {
        val context = getApplication<Application>()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // Live Stream Hype Monitor Actions
    // -------------------------------------------------------------------------

    fun updateLiveUrl(url: String) {
        _liveState.update { it.copy(liveUrlInput = url, errorMessage = null) }
    }

    fun updateLiveDuration(option: ClipDurationOption) {
        _liveState.update { it.copy(durationOption = option) }
    }

    fun updateLiveThreshold(threshold: Int) {
        _liveState.update { it.copy(hypeThreshold = threshold) }
    }

    fun updateLiveMaxWait(seconds: Int) {
        _liveState.update { it.copy(maxWaitSeconds = seconds) }
    }

    fun selectLiveCaptionStyle(style: CaptionStyle) {
        _liveState.update { it.copy(captionStyle = style) }
    }

    fun updateLiveHookText(text: String) {
        _liveState.update { it.copy(hookText = text) }
    }

    fun startLiveMonitoring() {
        val current = _liveState.value
        val url = current.liveUrlInput.trim()
        if (url.isEmpty()) {
            _liveState.update { it.copy(errorMessage = "Enter a YouTube Live stream link first.") }
            return
        }

        liveMonitorJob?.cancel()
        liveMonitorJob = viewModelScope.launch {
            _liveState.update {
                it.copy(
                    isConnecting = true,
                    isMonitoring = false,
                    errorMessage = null,
                    elapsedSeconds = 0,
                    messagesInLast30s = 0,
                    hypeLevel = HypeLevel.CALM,
                    velocityHistory = emptyList(),
                    recentMessages = emptyList(),
                    statusMessage = "Connecting to YouTube live stream..."
                )
            }

            try {
                val info = youTubeService.fetchVideoInfo(url)
                val apiKey = info.innertubeApiKey
                var continuation = info.liveChatContinuation

                if (apiKey.isNullOrBlank() || continuation.isNullOrBlank()) {
                    _liveState.update {
                        it.copy(
                            isConnecting = false,
                            streamInfo = info,
                            errorMessage = "Live chat is unavailable on this link. Make sure the YouTube video is currently live with chat enabled."
                        )
                    }
                    return@launch
                }

                _liveState.update {
                    it.copy(
                        isConnecting = false,
                        isMonitoring = true,
                        streamInfo = info,
                        statusMessage = "Monitoring live chat on \"${info.title}\"..."
                    )
                }

                val timestamps = ArrayDeque<Long>()
                val seenIds = LinkedHashSet<String>()
                val startTimeMs = System.currentTimeMillis()
                var hyped = false

                while (isActive) {
                    val nowMs = System.currentTimeMillis()
                    val elapsed = ((nowMs - startTimeMs) / 1000L).toInt()
                    if (elapsed >= _liveState.value.maxWaitSeconds) break

                    val poll = youTubeService.pollLiveChat(apiKey, continuation ?: break)
                    continuation = poll.nextContinuation ?: continuation

                    val fresh = poll.messages.filter { msg -> seenIds.add(msg.id) }
                    if (seenIds.size > 1000) {
                        val iter = seenIds.iterator()
                        repeat(300) { if (iter.hasNext()) { iter.next(); iter.remove() } }
                    }

                    val pollNowMs = System.currentTimeMillis()
                    fresh.forEach { timestamps.addLast(pollNowMs) }
                    while (timestamps.isNotEmpty() && pollNowMs - timestamps.first() > 30_000L) {
                        timestamps.removeFirst()
                    }

                    val count = timestamps.size
                    val threshold = _liveState.value.hypeThreshold.coerceAtLeast(1)
                    val level = computeHypeLevel(count, threshold)

                    _liveState.update { s ->
                        s.copy(
                            elapsedSeconds = ((pollNowMs - startTimeMs) / 1000L).toInt(),
                            messagesInLast30s = count,
                            hypeLevel = level,
                            velocityHistory = (s.velocityHistory + count).takeLast(30),
                            recentMessages = (s.recentMessages + fresh).takeLast(40),
                            statusMessage = "Watching live chat (${((pollNowMs - startTimeMs) / 1000L)}s) • $count msgs/30s"
                        )
                    }

                    if (count >= threshold) {
                        hyped = true
                        _liveState.update {
                            it.copy(
                                isMonitoring = false,
                                isAnalyzingBurst = true,
                                statusMessage = "VIRAL MOMENT DETECTED! Analyzing burst..."
                            )
                        }
                        captureBurstWithAi(
                            info = info,
                            messages = _liveState.value.recentMessages,
                            velocity = count
                        )
                        break
                    }

                    delay(poll.timeoutMs)
                }

                if (!hyped && isActive) {
                    _liveState.update {
                        it.copy(
                            isMonitoring = false,
                            statusMessage = "Monitoring window (${_liveState.value.maxWaitSeconds}s) ended without crossing threshold."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live chat monitoring error", e)
                _liveState.update {
                    it.copy(
                        isConnecting = false,
                        isMonitoring = false,
                        isAnalyzingBurst = false,
                        errorMessage = e.localizedMessage ?: "Live monitoring failed."
                    )
                }
            }
        }
    }

    fun stopLiveMonitoring() {
        liveMonitorJob?.cancel()
        liveMonitorJob = null
        _liveState.update {
            it.copy(
                isConnecting = false,
                isMonitoring = false,
                statusMessage = "Live monitoring stopped."
            )
        }
    }

    fun captureCurrentLiveMoment() {
        val state = _liveState.value
        val info = state.streamInfo ?: return
        if (state.recentMessages.isEmpty()) {
            _liveState.update { it.copy(errorMessage = "No chat messages captured yet to analyze.") }
            return
        }
        viewModelScope.launch {
            _liveState.update { it.copy(isAnalyzingBurst = true, errorMessage = null) }
            try {
                captureBurstWithAi(info, state.recentMessages, state.messagesInLast30s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual live burst capture failed", e)
                _liveState.update {
                    it.copy(
                        isAnalyzingBurst = false,
                        errorMessage = e.localizedMessage ?: "Failed to analyze live chat burst."
                    )
                }
            }
        }
    }

    private suspend fun captureBurstWithAi(
        info: YouTubeVideoInfo,
        messages: List<LiveChatMessage>,
        velocity: Int
    ) {
        val settings = _settingsState.value
        val (aiHook, score, summary) = if (settings.hasApiKey) {
            val burst = aiRepository.analyzeLiveChatBurst(
                apiKey = settings.apiKey,
                modelName = settings.modelName,
                streamTitle = info.title,
                messages = messages,
                msgCount30s = velocity
            )
            Triple(burst.hookTitle, burst.viralScore, burst.summary)
        } else {
            Triple(
                _liveState.value.hookText.ifBlank { "INSANE MOMENT!" },
                min(100, 65 + velocity),
                "Peak live chat velocity of $velocity msgs/30s captured. Add a Gemini API key in Settings for AI reaction summaries."
            )
        }

        val hook = if (_liveState.value.enableHook && _liveState.value.hookText.isNotBlank() &&
            _liveState.value.hookText != "INSANE MOMENT!"
        ) {
            _liveState.value.hookText
        } else {
            aiHook
        }

        val capture = LiveMomentCapture(
            id = UUID.randomUUID().toString().take(8),
            videoId = info.videoId,
            streamTitle = info.title,
            capturedAtMs = System.currentTimeMillis(),
            chatVelocityPer30s = velocity,
            viralScore = score,
            hookTitle = hook,
            summary = summary,
            sampleMessages = messages.takeLast(5).map { "${it.author}: ${it.text}" },
            hlsManifestUrl = info.hlsManifestUrl
        )
        _liveState.update {
            it.copy(
                isAnalyzingBurst = false,
                hookText = hook,
                capturedMoments = listOf(capture) + it.capturedMoments,
                statusMessage = "Captured viral moment ($velocity msgs/30s) • Score $score/100"
            )
        }
    }

    private fun computeHypeLevel(count: Int, threshold: Int): HypeLevel {
        val ratio = count.toFloat() / max(1, threshold).toFloat()
        return when {
            ratio >= 1.0f -> HypeLevel.PEAK_VIRAL
            ratio >= 0.7f -> HypeLevel.HIGH_VELOCITY
            ratio >= 0.35f -> HypeLevel.BUILDING
            else -> HypeLevel.CALM
        }
    }

    // -------------------------------------------------------------------------
    // Clips Vault Actions
    // -------------------------------------------------------------------------

    fun refreshVault() {
        viewModelScope.launch {
            val clips = ClipStorageRepository.loadSavedClips(getApplication())
            _vaultState.update { it.copy(clips = clips) }
        }
    }

    fun openClipPlayer(clip: SavedClip) {
        _vaultState.update { it.copy(activePlayerClip = clip) }
    }

    fun closeClipPlayer() {
        _vaultState.update { it.copy(activePlayerClip = null) }
    }

    fun saveClipToGallery(clip: SavedClip) {
        viewModelScope.launch {
            try {
                ClipStorageRepository.saveClipToGallery(getApplication(), clip)
                _vaultState.update {
                    it.copy(feedbackMessage = "Saved \"${clip.hookText.ifBlank { clip.title }}\" to Movies/ViralClipper.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gallery save failed", e)
                _vaultState.update {
                    it.copy(feedbackMessage = e.localizedMessage ?: "Failed to save to gallery.")
                }
            }
        }
    }

    fun deleteClip(clip: SavedClip) {
        viewModelScope.launch {
            ClipStorageRepository.deleteClip(getApplication(), clip)
            refreshVault()
            _vaultState.update {
                it.copy(
                    activePlayerClip = if (it.activePlayerClip?.id == clip.id) null else it.activePlayerClip,
                    feedbackMessage = "Clip deleted."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveMonitorJob?.cancel()
        clipGenerationJob?.cancel()
        montageJob?.cancel()
        aiMontageJob?.cancel()
        silenceJob?.cancel()
    }
}
