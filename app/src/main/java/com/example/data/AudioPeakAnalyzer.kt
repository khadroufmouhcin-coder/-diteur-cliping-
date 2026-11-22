package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.AudioEnergyProfile
import com.example.model.SilenceAnalysisResult
import com.example.model.TimeRangeSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "AudioPeakAnalyzer"
private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024

data class VideoMediaMetadata(
    val displayName: String,
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap?,
    val hasAudio: Boolean = true
)

object AudioPeakAnalyzer {

    suspend fun getVideoMetadata(context: Context, uri: Uri): VideoMediaMetadata =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 30_000L
                val width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 1920
                val height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 1080
                val hasAudioStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                )
                val thumb = retriever.getFrameAtTime(
                    min(durationMs * 1000L / 4L, 2_000_000L),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                val name = resolveDisplayName(context, uri) ?: "Selected Video"
                VideoMediaMetadata(
                    displayName = name,
                    durationSec = durationMs / 1000.0,
                    width = width,
                    height = height,
                    thumbnail = thumb,
                    hasAudio = hasAudioStr != "no"
                )
            } finally {
                runCatching { retriever.release() }
            }
        }

    fun hasAudioTrack(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).any { idx ->
                extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
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

    /**
     * Computes per-second RMS loudness and starts the clip 5 seconds before the loudest window.
     */
    suspend fun analyzeAudioEnergy(
        context: Context,
        uri: Uri,
        clipDurationSec: Int
    ): AudioEnergyProfile = withContext(Dispatchers.IO) {
        val meta = getVideoMetadata(context, uri)
        val totalSecInt = max(1, meta.durationSec.toInt())
        if (meta.durationSec <= clipDurationSec) {
            return@withContext AudioEnergyProfile(meta.durationSec, List(totalSecInt) { 0.5f }, 0.0)
        }

        val rawRms = decodeBucketedRms(context, uri, bucketDurationMs = 1000, maxBuckets = totalSecInt)
        if (rawRms.isEmpty()) {
            return@withContext AudioEnergyProfile(
                durationSec = meta.durationSec,
                secondEnergies = List(totalSecInt) { 0.25f },
                peakStartSec = 0.0
            )
        }

        val maxRms = rawRms.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val normalized = rawRms.map { (it / maxRms).toFloat().coerceIn(0f, 1f) }

        var bestStart = 0
        var bestMean = -1.0
        val windowEndLimit = max(1, rawRms.size - clipDurationSec)
        for (s in 0 until windowEndLimit) {
            val end = min(rawRms.size, s + clipDurationSec)
            var sum = 0.0
            for (k in s until end) sum += rawRms[k]
            val mean = sum / max(1, end - s)
            if (mean > bestMean) {
                bestMean = mean
                bestStart = s
            }
        }

        val peakStart = max(0.0, (bestStart - 5).toDouble())
            .coerceAtMost(max(0.0, meta.durationSec - clipDurationSec))

        AudioEnergyProfile(
            durationSec = meta.durationSec,
            secondEnergies = normalized,
            peakStartSec = peakStart
        )
    }

    /**
     * Ports AI Auto-Montage's highlight scorer:
     * 70% audio loudness (RMS) + 30% visual scene/action change intensity, picking N non-overlapping moments.
     */
    suspend fun analyzeHighlights(
        context: Context,
        uri: Uri,
        nClips: Int,
        segLenSec: Int
    ): List<Double> = withContext(Dispatchers.IO) {
        val meta = getVideoMetadata(context, uri)
        val dur = meta.durationSec
        val len = max(1, segLenSec)
        val secs = dur.toInt()
        if (secs <= len) return@withContext listOf(0.0)

        val targetCount = max(1, min(nClips, secs / len))
        val scores = DoubleArray(secs)

        // 70% Audio RMS energy
        val audioRms = decodeBucketedRms(context, uri, bucketDurationMs = 1000, maxBuckets = secs)
        val maxAudio = audioRms.maxOrNull()?.takeIf { it > 0.0 } ?: 0.0
        if (maxAudio > 0.0) {
            for (i in 0 until min(secs, audioRms.size)) {
                scores[i] += 0.7 * (audioRms[i] / maxAudio)
            }
        }

        // 30% Visual scene/motion activity
        val sceneScores = computeSceneActivityScores(context, uri, secs)
        val maxScene = sceneScores.maxOrNull()?.takeIf { it > 0.0 } ?: 0.0
        if (maxScene > 0.0) {
            for (i in 0 until secs) {
                scores[i] += 0.3 * (sceneScores[i] / maxScene)
            }
        }

        // Prefix sum sliding windows
        val nWin = secs - len + 1
        val prefix = DoubleArray(secs + 1)
        for (i in 0 until secs) prefix[i + 1] = prefix[i] + scores[i]
        val winScores = DoubleArray(nWin) { s -> (prefix[s + len] - prefix[s]) / len }

        val picks = mutableListOf<Int>()
        repeat(targetCount) {
            var bestIdx = -1
            var bestVal = -1.0
            for (i in winScores.indices) {
                if (winScores[i] > bestVal) {
                    bestVal = winScores[i]
                    bestIdx = i
                }
            }
            if (bestIdx < 0 || bestVal < 0.0) return@repeat
            picks.add(bestIdx)
            val suppressFrom = max(0, bestIdx - len + 1)
            val suppressTo = min(nWin, bestIdx + len)
            for (k in suppressFrom until suppressTo) {
                winScores[k] = -1.0
            }
        }

        picks.map { s ->
            max(0.0, min(s.toDouble() - 2.0, dur - len))
        }.sorted()
    }

    private fun computeSceneActivityScores(context: Context, uri: Uri, totalSecs: Int): DoubleArray {
        val scores = DoubleArray(totalSecs)
        if (totalSecs < 2) return scores
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val strideSec = max(1, totalSecs / 48)
            var prevLuma: IntArray? = null
            var sec = 0
            while (sec < totalSecs) {
                val frame = retriever.getFrameAtTime(
                    sec * 1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val tiny = Bitmap.createScaledBitmap(frame, 32, 18, false)
                    frame.recycle()
                    val pixels = IntArray(32 * 18)
                    tiny.getPixels(pixels, 0, 32, 0, 0, 32, 18)
                    tiny.recycle()
                    val luma = IntArray(pixels.size) { idx ->
                        val c = pixels[idx]
                        (((c shr 16) and 0xFF) * 30 + ((c shr 8) and 0xFF) * 59 + (c and 0xFF) * 11) / 100
                    }
                    prevLuma?.let { prev ->
                        var diffSum = 0L
                        for (i in luma.indices) diffSum += abs(luma[i] - prev[i])
                        val meanDiff = diffSum.toDouble() / (luma.size * 255.0)
                        val fillEnd = min(totalSecs, sec + strideSec)
                        for (k in sec until fillEnd) scores[k] = meanDiff
                    }
                    prevLuma = luma
                }
                sec += strideSec
            }
            scores
        } catch (e: Exception) {
            Log.w(TAG, "Scene activity scoring skipped: ${e.message}")
            scores
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Ports Silence Remover: computes 50ms RMS energy frames in dBFS and returns non-silent keep intervals.
     */
    suspend fun detectSpeechKeepIntervals(
        context: Context,
        uri: Uri,
        noiseDb: Int = -35,
        minSilenceSec: Double = 0.5,
        padSec: Double = 0.12
    ): SilenceAnalysisResult = withContext(Dispatchers.IO) {
        val meta = getVideoMetadata(context, uri)
        val dur = meta.durationSec
        val frameMs = 50
        val totalFrames = max(1, ((dur * 1000.0) / frameMs).roundToInt())
        val rmsFrames = decodeBucketedRms(context, uri, bucketDurationMs = frameMs, maxBuckets = totalFrames)

        if (rmsFrames.isEmpty()) {
            return@withContext SilenceAnalysisResult(
                originalDurationSec = dur,
                newDurationSec = dur,
                keepIntervals = listOf(TimeRangeSec(0.0, dur))
            )
        }

        val frameSec = frameMs / 1000.0
        val minSilentFrames = max(1, (minSilenceSec / frameSec).roundToInt())
        val silentRuns = mutableListOf<TimeRangeSec>()

        var runStartFrame = -1
        for (i in rmsFrames.indices) {
            val rms = rmsFrames[i]
            val db = if (rms > 1e-7) 20.0 * log10(rms) else -120.0
            if (db < noiseDb) {
                if (runStartFrame < 0) runStartFrame = i
            } else {
                if (runStartFrame >= 0 && (i - runStartFrame) >= minSilentFrames) {
                    silentRuns.add(TimeRangeSec(runStartFrame * frameSec, i * frameSec))
                }
                runStartFrame = -1
            }
        }
        if (runStartFrame >= 0 && (rmsFrames.size - runStartFrame) >= minSilentFrames) {
            silentRuns.add(TimeRangeSec(runStartFrame * frameSec, dur))
        }

        val keep = mutableListOf<TimeRangeSec>()
        var pos = 0.0
        for (silent in silentRuns) {
            val st = max(0.0, silent.startSec)
            val en = silent.endSec
            if (st + padSec > pos + 0.08) {
                keep.add(TimeRangeSec(pos, min(dur, st + padSec)))
            }
            pos = max(pos, en - padSec)
        }
        if (dur - pos > 0.08) {
            keep.add(TimeRangeSec(pos, dur))
        }

        // Merge any adjacent or slightly overlapping keep windows
        val merged = mutableListOf<TimeRangeSec>()
        for (interval in keep) {
            val last = merged.lastOrNull()
            if (last != null && interval.startSec <= last.endSec + 0.05) {
                merged[merged.lastIndex] = TimeRangeSec(last.startSec, max(last.endSec, interval.endSec))
            } else if (interval.lengthSec >= 0.1) {
                merged.add(interval)
            }
        }

        val newDur = merged.sumOf { it.lengthSec }
        SilenceAnalysisResult(
            originalDurationSec = dur,
            newDurationSec = newDur,
            keepIntervals = merged
        )
    }

    private fun decodeBucketedRms(
        context: Context,
        uri: Uri,
        bucketDurationMs: Int,
        maxBuckets: Int
    ): DoubleArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return DoubleArray(0)

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bucketSquares = DoubleArray(maxBuckets)
            val bucketCounts = LongArray(maxBuckets)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 5_000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val startBucket = ((bufferInfo.presentationTimeUs / (bucketDurationMs * 1000L)).toInt())
                            .coerceIn(0, maxBuckets - 1)
                        accumulatePcmEnergy(
                            buffer = outBuf,
                            offset = bufferInfo.offset,
                            sizeBytes = bufferInfo.size,
                            channels = channels,
                            sampleRate = sampleRate,
                            bucketDurationMs = bucketDurationMs,
                            startBucket = startBucket,
                            bucketSquares = bucketSquares,
                            bucketCounts = bucketCounts
                        )
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            DoubleArray(maxBuckets) { i ->
                if (bucketCounts[i] > 0) sqrt(bucketSquares[i] / bucketCounts[i]) else 0.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio RMS decoding failed", e)
            DoubleArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun accumulatePcmEnergy(
        buffer: ByteBuffer,
        offset: Int,
        sizeBytes: Int,
        channels: Int,
        sampleRate: Int,
        bucketDurationMs: Int,
        startBucket: Int,
        bucketSquares: DoubleArray,
        bucketCounts: LongArray
    ) {
        val dup = buffer.duplicate()
        dup.position(offset)
        dup.limit(offset + sizeBytes)
        val stride = max(1, channels)
        val samplesPerBucket = max(1, (sampleRate * bucketDurationMs) / 1000)
        var frameIdx = 0
        var i = 0
        while (i + 1 < sizeBytes) {
            val lo = dup.get().toInt() and 0xFF
            val hi = dup.get().toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
            if ((frameIdx % stride) == 0) {
                val offsetBuckets = (frameIdx / stride) / samplesPerBucket
                val idx = (startBucket + offsetBuckets).coerceIn(0, bucketSquares.lastIndex)
                bucketSquares[idx] += sample * sample
                bucketCounts[idx]++
            }
            frameIdx++
            i += 2
        }
    }

    suspend fun extractAudioClipBytes(
        context: Context,
        uri: Uri,
        startSec: Double,
        durationSec: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "clip_audio_${System.currentTimeMillis()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioIdx = i
                    format = f
                    break
                }
            }
            if (audioIdx == -1 || format == null) return@withContext null

            val startUs = (startSec * 1_000_000.0).toLong().coerceAtLeast(0L)
            val endUs = startUs + durationSec * 1_000_000L

            extractor.selectTrack(audioIdx)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(format)
            muxer.start()

            val buf = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstPts = -1L

            while (true) {
                val sampleSize = extractor.readSampleData(buf, 0)
                if (sampleSize < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                if (pts >= startUs) {
                    if (firstPts < 0L) firstPts = pts
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (pts - firstPts).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buf, info)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            if (!tempFile.exists() || tempFile.length() <= 0L || tempFile.length() > MAX_INLINE_AUDIO_BYTES) {
                return@withContext null
            }
            tempFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Audio clip extraction failed", e)
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    suspend fun extractKeyFrames(
        context: Context,
        uri: Uri,
        startSec: Double,
        durationSec: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val points = listOf(0.15, 0.5, 0.85)
            points.mapNotNull { ratio ->
                val us = ((startSec + durationSec * ratio) * 1_000_000.0).toLong()
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { bmp ->
                    val maxDim = 512
                    val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
                    if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bmp,
                            max(1, (bmp.width * scale).toInt()),
                            max(1, (bmp.height * scale).toInt()),
                            true
                        )
                    } else bmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keyframe extraction failed", e)
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }
}
