package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.model.CaptionSegment
import com.example.model.CaptionStyle
import com.example.model.TimeRangeSec
import com.example.model.TransitionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "VerticalVideoRenderer"
private const val OUT_WIDTH = 1080
private const val OUT_HEIGHT = 1920

data class MontageSegmentSpec(
    val uri: Uri,
    val startSec: Double,
    val lengthSec: Double,
    val hasAudio: Boolean = true
)

object VerticalVideoRenderer {

    @OptIn(UnstableApi::class)
    suspend fun renderVerticalClip(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startSec: Double,
        durationSec: Int,
        verticalBlur: Boolean,
        enableCaptions: Boolean,
        captionStyle: CaptionStyle,
        captions: List<CaptionSegment>,
        enableHook: Boolean,
        hookText: String,
        hookDurationSec: Double? = null,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val sourceDims = extractSourceVideoInfo(context, inputUri, startSec)
        val blurredBg = if (verticalBlur) {
            sourceDims.sampleFrame?.let { createVerticalBlurredBackground(it) }
        } else null

        val fgRect = computeCenteredVideoRect(sourceDims.width, sourceDims.height)
        val typeface = loadHeadlineTypeface(context)

        val overlay = ViralClipCanvasOverlay(
            blurredBackground = blurredBg,
            foregroundVideoRect = fgRect,
            verticalBlur = verticalBlur,
            enableCaptions = enableCaptions,
            captionStyle = captionStyle,
            captions = captions,
            enableHook = enableHook,
            hookText = hookText.trim(),
            hookDurationSec = hookDurationSec,
            transitionType = TransitionType.NONE,
            cutTimestampsSec = emptyList(),
            headlineTypeface = typeface
        )

        val startMs = (startSec * 1000.0).toLong().coerceAtLeast(0L)
        val endMs = startMs + durationSec * 1000L

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val presentation = createVerticalPresentation(verticalBlur)
        val videoEffects = mutableListOf<Effect>(presentation)
        if (verticalBlur || (enableHook && hookText.isNotBlank()) || (enableCaptions && captions.isNotEmpty())) {
            videoEffects.add(OverlayEffect(listOf(overlay)))
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        runTransformerOnMainThread(context, editedMediaItem, null, outputFile, onProgress)
        blurredBg?.recycle()
        outputFile
    }

    /**
     * Joins multiple trimmed video segments with optional speed, vertical 9:16 formatting,
     * visual transitions, captions/karaoke/hook overlays, and looped background music.
     */
    @OptIn(UnstableApi::class)
    suspend fun renderMontageJoin(
        context: Context,
        segments: List<MontageSegmentSpec>,
        outputFile: File,
        verticalBlur: Boolean,
        speed: Float,
        transitionType: TransitionType,
        enableCaptions: Boolean,
        captionStyle: CaptionStyle,
        captions: List<CaptionSegment>,
        enableHook: Boolean,
        hookText: String,
        hookDurationSec: Double?,
        musicUri: Uri?,
        musicVolume: Float,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(segments.isNotEmpty()) { "At least one segment is required." }
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val firstSeg = segments.first()
        val sourceDims = extractSourceVideoInfo(context, firstSeg.uri, firstSeg.startSec)
        val blurredBg = if (verticalBlur) {
            sourceDims.sampleFrame?.let { createVerticalBlurredBackground(it) }
        } else null

        val fgRect = computeCenteredVideoRect(sourceDims.width, sourceDims.height)
        val typeface = loadHeadlineTypeface(context)

        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        val cutTimestamps = mutableListOf<Double>()
        var runningSec = 0.0
        for (i in 0 until segments.lastIndex) {
            runningSec += segments[i].lengthSec / safeSpeed
            cutTimestamps.add(runningSec)
        }

        val overlay = ViralClipCanvasOverlay(
            blurredBackground = blurredBg,
            foregroundVideoRect = fgRect,
            verticalBlur = verticalBlur,
            enableCaptions = enableCaptions,
            captionStyle = captionStyle,
            captions = captions,
            enableHook = enableHook,
            hookText = hookText.trim(),
            hookDurationSec = hookDurationSec,
            transitionType = transitionType,
            cutTimestampsSec = cutTimestamps,
            headlineTypeface = typeface
        )

        val presentation = createVerticalPresentation(verticalBlur)
        val videoEffects = mutableListOf<Effect>(presentation)
        if (verticalBlur ||
            (enableHook && hookText.isNotBlank()) ||
            (enableCaptions && captions.isNotEmpty()) ||
            (transitionType != TransitionType.NONE && cutTimestamps.isNotEmpty())
        ) {
            videoEffects.add(OverlayEffect(listOf(overlay)))
        }

        val editedItems = segments.map { seg ->
            val startMs = (seg.startSec * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = startMs + (seg.lengthSec * 1000.0).toLong().coerceAtLeast(300L)
            val mediaItem = MediaItem.Builder()
                .setUri(seg.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val itemAudioProcessors = mutableListOf<AudioProcessor>()
            val itemVideoEffects = mutableListOf<Effect>(presentation)

            if (abs(safeSpeed - 1.0f) > 0.02f) {
                val speedPair = Effects.createExperimentalSpeedChangingEffect(
                    object : SpeedProvider {
                        override fun getSpeed(timeUs: Long): Float = safeSpeed
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                    }
                )
                itemAudioProcessors.add(speedPair.first)
                itemVideoEffects.add(speedPair.second)
            }

            EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(itemAudioProcessors, itemVideoEffects))
                .build()
        }

        val mainSequence = EditedMediaItemSequence.Builder(editedItems).build()

        val sequences = mutableListOf<EditedMediaItemSequence>(mainSequence)
        if (musicUri != null && musicVolume > 0.01f) {
            val vol = musicVolume.coerceIn(0f, 1f)
            val volProcessor = ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(
                    ChannelMixingMatrix(1, 2, floatArrayOf(vol, vol))
                )
                putChannelMixingMatrix(
                    ChannelMixingMatrix(2, 2, floatArrayOf(vol, 0f, 0f, vol))
                )
            }
            val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(musicUri))
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(volProcessor), emptyList()))
                .build()
            val musicSequence = EditedMediaItemSequence.Builder(listOf(musicItem))
                .setIsLooping(true)
                .build()
            sequences.add(musicSequence)
        }

        val compositionBuilder = Composition.Builder(sequences)
        if (videoEffects.size > 1) {
            compositionBuilder.setEffects(Effects(emptyList(), listOf(videoEffects.last())))
        }

        runTransformerOnMainThread(context, null, compositionBuilder.build(), outputFile, onProgress)
        blurredBg?.recycle()
        outputFile
    }

    /**
     * Renders jump-cut speech segments by trimming and concatenating non-silent intervals in Media3.
     */
    @OptIn(UnstableApi::class)
    suspend fun renderJumpCutVideo(
        context: Context,
        inputUri: Uri,
        keepIntervals: List<TimeRangeSec>,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(keepIntervals.isNotEmpty()) { "No non-silent intervals to export." }
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val editedItems = keepIntervals.map { range ->
            val startMs = (range.startSec * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (range.endSec * 1000.0).toLong().coerceAtLeast(startMs + 100L)
            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem).build()
        }

        val sequence = EditedMediaItemSequence.Builder(editedItems).build()
        val composition = Composition.Builder(listOf(sequence)).build()
        runTransformerOnMainThread(context, null, composition, outputFile, onProgress)
        outputFile
    }

    @OptIn(UnstableApi::class)
    private fun createVerticalPresentation(verticalBlur: Boolean): Presentation {
        return if (verticalBlur) {
            Presentation.createForWidthAndHeight(
                OUT_WIDTH,
                OUT_HEIGHT,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        } else {
            Presentation.createForWidthAndHeight(
                OUT_WIDTH,
                OUT_HEIGHT,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        }
    }

    private fun loadHeadlineTypeface(context: Context): Typeface {
        return runCatching {
            ResourcesCompat.getFont(context, R.font.space_grotesk)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMainThread(
        context: Context,
        singleItem: EditedMediaItem?,
        composition: Composition?,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var transformer: Transformer? = null
            var polling = true

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!polling) return
                    val t = transformer ?: return
                    val state = t.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && polling) {
                        handler.postDelayed(this, 250L)
                    }
                }
            }

            val listener = object : Transformer.Listener {
                override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                    polling = false
                    handler.removeCallbacks(pollRunnable)
                    onProgress(1f)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    comp: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    polling = false
                    handler.removeCallbacks(pollRunnable)
                    Log.e(TAG, "Media3 Transformer export failed", exportException)
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }

            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            continuation.invokeOnCancellation {
                polling = false
                handler.removeCallbacks(pollRunnable)
                runCatching { transformer.cancel() }
            }

            if (composition != null) {
                transformer.start(composition, outputFile.absolutePath)
            } else if (singleItem != null) {
                transformer.start(singleItem, outputFile.absolutePath)
            } else {
                continuation.resumeWithException(IllegalArgumentException("No media source specified."))
                return@suspendCancellableCoroutine
            }
            handler.postDelayed(pollRunnable, 250L)
        }
    }

    private data class SourceVideoFrameInfo(
        val width: Int,
        val height: Int,
        val sampleFrame: Bitmap?
    )

    private fun extractSourceVideoInfo(
        context: Context,
        uri: Uri,
        startSec: Double
    ): SourceVideoFrameInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
            val frame = retriever.getFrameAtTime(
                ((startSec + 1.0) * 1_000_000.0).toLong().coerceAtLeast(0L),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(0L)
            SourceVideoFrameInfo(w, h, frame)
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting source frame info", e)
            SourceVideoFrameInfo(1920, 1080, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun computeCenteredVideoRect(srcW: Int, srcH: Int): Rect {
        val srcAspect = srcW.toFloat() / max(1, srcH).toFloat()
        val dstAspect = OUT_WIDTH.toFloat() / OUT_HEIGHT.toFloat()
        return if (srcAspect > dstAspect) {
            val scaledH = (OUT_WIDTH / srcAspect).roundToInt().coerceIn(1, OUT_HEIGHT)
            val top = (OUT_HEIGHT - scaledH) / 2
            Rect(0, top, OUT_WIDTH, top + scaledH)
        } else {
            val scaledW = (OUT_HEIGHT * srcAspect).roundToInt().coerceIn(1, OUT_WIDTH)
            val left = (OUT_WIDTH - scaledW) / 2
            Rect(left, 0, left + scaledW, OUT_HEIGHT)
        }
    }

    private fun createVerticalBlurredBackground(sourceFrame: Bitmap): Bitmap {
        val smallW = 108
        val smallH = 192
        val srcRatio = sourceFrame.width.toFloat() / max(1, sourceFrame.height)
        val targetRatio = smallW.toFloat() / smallH.toFloat()

        val cropRect = if (srcRatio > targetRatio) {
            val cropW = (sourceFrame.height * targetRatio).roundToInt().coerceIn(1, sourceFrame.width)
            val left = (sourceFrame.width - cropW) / 2
            Rect(left, 0, left + cropW, sourceFrame.height)
        } else {
            val cropH = (sourceFrame.width / targetRatio).roundToInt().coerceIn(1, sourceFrame.height)
            val top = (sourceFrame.height - cropH) / 2
            Rect(0, top, sourceFrame.width, top + cropH)
        }

        val tiny = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        val tinyCanvas = Canvas(tiny)
        tinyCanvas.drawBitmap(sourceFrame, cropRect, Rect(0, 0, smallW, smallH), Paint(Paint.FILTER_BITMAP_FLAG))

        val blurredTiny = fastBoxBlur(tiny, radius = 8)
        tiny.recycle()

        val full = Bitmap.createScaledBitmap(blurredTiny, OUT_WIDTH, OUT_HEIGHT, true)
        blurredTiny.recycle()

        val dimCanvas = Canvas(full)
        dimCanvas.drawColor(Color.argb(105, 10, 8, 20))
        return full
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val div = radius * 2 + 1

        repeat(2) {
            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (dx in -radius..radius) {
                        val sx = (x + dx).coerceIn(0, w - 1)
                        val c = pix[rowOff + sx]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                    }
                    tmp[rowOff + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (dy in -radius..radius) {
                        val sy = (y + dy).coerceIn(0, h - 1)
                        val c = tmp[sy * w + x]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                    }
                    pix[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                }
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    @OptIn(UnstableApi::class)
    private class ViralClipCanvasOverlay(
        private val blurredBackground: Bitmap?,
        private val foregroundVideoRect: Rect,
        private val verticalBlur: Boolean,
        private val enableCaptions: Boolean,
        private val captionStyle: CaptionStyle,
        private val captions: List<CaptionSegment>,
        private val enableHook: Boolean,
        private val hookText: String,
        private val hookDurationSec: Double?,
        private val transitionType: TransitionType,
        private val cutTimestampsSec: List<Double>,
        headlineTypeface: Typeface
    ) : CanvasOverlay(/* useInputFrameSize = */ true) {

        private val hookFillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 60f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
        }

        private val hookBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 12, 10, 24)
            style = Paint.Style.FILL
        }

        private val hookBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 139, 92, 246)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val captionStrokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 16f
            strokeJoin = Paint.Join.ROUND
        }

        private val captionFillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = captionStyle.primaryColorInt
            textSize = 72f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 6f, Color.argb(190, 0, 0, 0))
        }

        private val karaokeWhitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 6f, Color.argb(190, 0, 0, 0))
        }

        private val karaokeYellowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(250, 204, 21)
            textSize = 76f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            style = Paint.Style.FILL
            setShadowLayer(10f, 0f, 6f, Color.argb(210, 0, 0, 0))
        }

        private val transitionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val w = canvas.width
            val h = canvas.height
            val currentSec = presentationTimeUs / 1_000_000.0

            // 1. Blurred letterbox/pillarbox regions
            if (verticalBlur && blurredBackground != null) {
                if (foregroundVideoRect.top > 0) {
                    val topSrc = Rect(0, 0, OUT_WIDTH, foregroundVideoRect.top)
                    val topDst = Rect(0, 0, w, (foregroundVideoRect.top * h / OUT_HEIGHT.toFloat()).roundToInt())
                    canvas.drawBitmap(blurredBackground, topSrc, topDst, null)
                }
                if (foregroundVideoRect.bottom < OUT_HEIGHT) {
                    val botSrc = Rect(0, foregroundVideoRect.bottom, OUT_WIDTH, OUT_HEIGHT)
                    val botDst = Rect(0, (foregroundVideoRect.bottom * h / OUT_HEIGHT.toFloat()).roundToInt(), w, h)
                    canvas.drawBitmap(blurredBackground, botSrc, botDst, null)
                }
            }

            // 2. Cut transition effects
            if (transitionType != TransitionType.NONE && cutTimestampsSec.isNotEmpty()) {
                drawTransitionAtCuts(canvas, w, h, currentSec)
            }

            // 3. Top Hook Header Pill (optionally timed to hookDurationSec)
            val hookVisible = hookDurationSec == null || currentSec <= hookDurationSec
            if (enableHook && hookText.isNotBlank() && hookVisible) {
                val maxTextWidth = (w * 0.82f).roundToInt()
                val layout = StaticLayout.Builder.obtain(
                    hookText.uppercase(),
                    0,
                    hookText.length,
                    hookFillPaint,
                    maxTextWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(6f, 1f)
                    .build()

                val padX = 44f
                val padY = 26f
                val textWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: maxTextWidth.toFloat()
                val boxLeft = (w - textWidth) / 2f - padX
                val boxRight = (w + textWidth) / 2f + padX
                val boxTop = h * 0.105f
                val boxBottom = boxTop + layout.height + padY * 2f

                val pillRect = RectF(boxLeft, boxTop, boxRight, boxBottom)
                canvas.drawRoundRect(pillRect, 28f, 28f, hookBoxPaint)
                canvas.drawRoundRect(pillRect, 28f, 28f, hookBorderPaint)

                canvas.save()
                canvas.translate((w - maxTextWidth) / 2f, boxTop + padY)
                layout.draw(canvas)
                canvas.restore()
            }

            // 4. Dynamic Synced Auto-Captions / Karaoke
            if (enableCaptions && captions.isNotEmpty()) {
                val active = captions.firstOrNull { currentSec in it.startSec..it.endSec }
                if (active != null) {
                    if (captionStyle == CaptionStyle.KARAOKE && active.words.isNotEmpty()) {
                        drawKaraokeLine(canvas, w, h, active, currentSec)
                    } else {
                        drawStandardCaption(canvas, w, h, active.text)
                    }
                }
            }
        }

        private fun drawStandardCaption(canvas: Canvas, w: Int, h: Int, text: String) {
            val maxWidth = (w * 0.86f).roundToInt()
            val strokeLayout = StaticLayout.Builder.obtain(
                text, 0, text.length, captionStrokePaint, maxWidth
            ).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(6f, 1f).build()

            val fillLayout = StaticLayout.Builder.obtain(
                text, 0, text.length, captionFillPaint, maxWidth
            ).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(6f, 1f).build()

            val yPos = h * 0.75f - strokeLayout.height / 2f
            canvas.save()
            canvas.translate((w - maxWidth) / 2f, yPos)
            strokeLayout.draw(canvas)
            fillLayout.draw(canvas)
            canvas.restore()
        }

        private fun drawKaraokeLine(
            canvas: Canvas,
            w: Int,
            h: Int,
            segment: CaptionSegment,
            currentSec: Double
        ) {
            val words = segment.words.take(4)
            if (words.isEmpty()) {
                drawStandardCaption(canvas, w, h, segment.text)
                return
            }
            val spaceW = karaokeWhitePaint.measureText(" ")
            val totalW = words.sumOf { karaokeWhitePaint.measureText(it.word).toDouble() }.toFloat() +
                spaceW * (words.size - 1)
            var cursorX = (w - totalW) / 2f
            val yPos = h * 0.75f

            words.forEachIndexed { idx, word ->
                val isCurrent = currentSec >= word.startSec &&
                    (currentSec <= word.endSec || idx == words.lastIndex)
                val fillPaint = if (isCurrent) karaokeYellowPaint else karaokeWhitePaint
                canvas.drawText(word.word, cursorX, yPos, captionStrokePaint)
                canvas.drawText(word.word, cursorX, yPos, fillPaint)
                cursorX += karaokeWhitePaint.measureText(word.word) + spaceW
            }
        }

        private fun drawTransitionAtCuts(canvas: Canvas, w: Int, h: Int, currentSec: Double) {
            val halfWindow = 0.25
            val cut = cutTimestampsSec.firstOrNull { abs(currentSec - it) <= halfWindow } ?: return
            val dist = abs(currentSec - cut) / halfWindow // 0 at exact cut, 1 at edge
            val intensity = (1.0 - dist).toFloat().coerceIn(0f, 1f)

            when (transitionType) {
                TransitionType.FADE, TransitionType.DISSOLVE -> {
                    val alpha = (intensity * 235).roundToInt().coerceIn(0, 255)
                    transitionPaint.color = Color.argb(alpha, 10, 8, 20)
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), transitionPaint)
                }
                TransitionType.SLIDE -> {
                    val progress = ((currentSec - (cut - halfWindow)) / (halfWindow * 2.0)).toFloat().coerceIn(0f, 1f)
                    val barW = w * 0.45f
                    val left = -barW + progress * (w + barW)
                    transitionPaint.color = Color.argb(230, 139, 92, 246)
                    canvas.drawRect(left, 0f, left + barW, h.toFloat(), transitionPaint)
                }
                TransitionType.WIPE -> {
                    val wipeW = w * intensity
                    transitionPaint.color = Color.argb(220, 10, 8, 20)
                    canvas.drawRect(0f, 0f, wipeW, h.toFloat(), transitionPaint)
                }
                TransitionType.CIRCLE -> {
                    val maxR = hypot(w / 2f, h / 2f)
                    val holeR = maxR * (1f - intensity)
                    val path = Path().apply {
                        addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
                        addCircle(w / 2f, h / 2f, holeR, Path.Direction.CCW)
                    }
                    transitionPaint.color = Color.argb(240, 10, 8, 20)
                    canvas.drawPath(path, transitionPaint)
                }
                TransitionType.NONE -> Unit
            }
        }
    }
}
