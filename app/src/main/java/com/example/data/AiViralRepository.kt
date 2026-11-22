package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.model.CaptionSegment
import com.example.model.CaptionWord
import com.example.model.LiveChatMessage
import com.example.model.ViralAnalysisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "AiViralRepository"
private const val GEMINI_REST_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

data class LiveBurstAnalysis(
    val hookTitle: String,
    val viralScore: Int,
    val summary: String
)

class AiViralRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun verifyApiKey(apiKey: String, modelName: String): String {
        val key = apiKey.trim()
        require(key.isNotEmpty()) { "Enter your Gemini API key first." }
        val model = modelName.trim().ifBlank { GeminiSettings.DEFAULT_GEMINI_MODEL }

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Reply with OK"))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 16)
            })
        }

        val text = callGeminiRest(key, model, payload, timeoutMs = 25_000L)
        return "Connected to $model (${text.trim().take(24)})"
    }

    suspend fun analyzeClipAndGenerateCaptions(
        apiKey: String,
        modelName: String,
        audioBytes: ByteArray?,
        keyframes: List<Bitmap>,
        videoTitle: String,
        durationSec: Int,
        generateCaptions: Boolean,
        karaokeMode: Boolean = false
    ): ViralAnalysisResult {
        val key = requireApiKey(apiKey)
        val model = modelName.trim().ifBlank { GeminiSettings.DEFAULT_GEMINI_MODEL }

        val parts = JSONArray().apply {
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "audio/mp4")
                        put("data", Base64.encodeToString(audioBytes, Base64.NO_WRAP))
                    })
                })
            }
            keyframes.forEach { frame ->
                val jpegBytes = bitmapToJpegBytes(frame)
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
                    })
                })
            }
            put(JSONObject().put("text", buildString {
                append("You are an expert short-form viral video editor (TikTok/Reels/Shorts). ")
                append("Analyze the attached ")
                if (audioBytes != null) append("audio track ") else append("video keyframes ")
                append("for a $durationSec-second clip titled \"$videoTitle\". ")
                append("1) Create a bold, scroll-stopping uppercase hook headline (3-7 words, e.g. 'WAIT FOR THIS INSANE MOMENT!'). Prefer punchy spoken questions or exclamations if speech exists. ")
                append("2) Rate its viral energy from 1 to 100 and briefly explain why. ")
                if (generateCaptions) {
                    val wordsPerChunk = if (karaokeMode) "exactly 3 words" else "2 to 4 words"
                    if (audioBytes != null) {
                        append("3) Transcribe the spoken audio accurately into dynamic short-form caption chunks of $wordsPerChunk each with precise startSec, endSec, and per-word timestamps in 'words' between 0.0 and $durationSec.0.")
                    } else {
                        append("3) Generate 4 to 6 punchy visual caption beats of $wordsPerChunk timed evenly across 0.0 to $durationSec.0 seconds with per-word timestamps.")
                    }
                } else {
                    append("3) Return an empty captions array [].")
                }
            }))
        }

        val responseSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("suggestedHook", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Short punchy uppercase viral hook title")
                })
                put("viralScore", JSONObject().apply {
                    put("type", "INTEGER")
                    put("description", "Viral potential score from 1 to 100")
                })
                put("peakReason", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "One concise sentence explaining why this moment engages viewers")
                })
                put("captions", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("startSec", JSONObject().put("type", "NUMBER"))
                            put("endSec", JSONObject().put("type", "NUMBER"))
                            put("text", JSONObject().put("type", "STRING"))
                            put("words", JSONObject().apply {
                                put("type", "ARRAY")
                                put("items", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("startSec", JSONObject().put("type", "NUMBER"))
                                        put("endSec", JSONObject().put("type", "NUMBER"))
                                        put("word", JSONObject().put("type", "STRING"))
                                    })
                                    put("required", JSONArray(listOf("startSec", "endSec", "word")))
                                })
                            })
                        })
                        put("required", JSONArray(listOf("startSec", "endSec", "text")))
                    })
                })
            })
            put("required", JSONArray(listOf("suggestedHook", "viralScore", "peakReason", "captions")))
        }

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema)
            })
        }

        val jsonString = callGeminiRest(key, model, payload, timeoutMs = 60_000L)
        val json = JSONObject(jsonString)

        val suggestedHook = json.optString("suggestedHook", "WAIT FOR IT!").trim().uppercase()
        val viralScore = json.optInt("viralScore", 82).coerceIn(1, 100)
        val peakReason = json.optString("peakReason", "High-energy moment ideal for 9:16 retention.")
        val captionsJson = json.optJSONArray("captions")
        val captions = buildList {
            if (generateCaptions && captionsJson != null) {
                for (i in 0 until captionsJson.length()) {
                    val obj = captionsJson.optJSONObject(i) ?: continue
                    val start = obj.optDouble("startSec", 0.0).coerceIn(0.0, durationSec.toDouble())
                    val end = obj.optDouble("endSec", start + 1.5)
                        .coerceIn(start + 0.25, durationSec.toDouble())
                    val text = obj.optString("text", "").trim().uppercase()
                    if (text.isEmpty()) continue

                    val wordsJson = obj.optJSONArray("words")
                    val parsedWords = buildList {
                        if (wordsJson != null && wordsJson.length() > 0) {
                            for (w in 0 until wordsJson.length()) {
                                val wo = wordsJson.optJSONObject(w) ?: continue
                                val wText = wo.optString("word", "").trim().uppercase()
                                if (wText.isEmpty()) continue
                                val wStart = wo.optDouble("startSec", start).coerceIn(start, end)
                                val wEnd = wo.optDouble("endSec", wStart + 0.25).coerceIn(wStart + 0.05, end)
                                add(CaptionWord(wStart, wEnd, wText))
                            }
                        }
                    }

                    // Ensure Karaoke always has word timings even if model omitted 'words'
                    val finalWords = if (parsedWords.isNotEmpty()) {
                        parsedWords
                    } else {
                        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
                        if (tokens.isEmpty()) emptyList()
                        else {
                            val step = (end - start) / tokens.size
                            tokens.mapIndexed { idx, tok ->
                                CaptionWord(
                                    startSec = start + idx * step,
                                    endSec = start + (idx + 1) * step,
                                    word = tok
                                )
                            }
                        }
                    }

                    add(
                        CaptionSegment(
                            startSec = start,
                            endSec = end,
                            text = text,
                            words = finalWords
                        )
                    )
                }
            }
        }

        return ViralAnalysisResult(
            captions = captions,
            suggestedHook = suggestedHook.ifEmpty { "WAIT FOR IT!" },
            viralScore = viralScore,
            peakReason = peakReason
        )
    }

    suspend fun suggestViralHook(
        apiKey: String,
        modelName: String,
        videoTitle: String,
        extraContext: String = ""
    ): String {
        val key = requireApiKey(apiKey)
        val model = modelName.trim().ifBlank { GeminiSettings.DEFAULT_GEMINI_MODEL }

        val prompt = "Generate ONE ultra-punchy uppercase viral short-form video hook header " +
            "(2 to 6 words max, no quotes, no hashtags) for a video titled \"$videoTitle\". " +
            if (extraContext.isNotBlank()) "Context: $extraContext" else ""

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
        }

        val text = callGeminiRest(key, model, payload, timeoutMs = 30_000L)
        return text.trim().replace("\"", "").uppercase().take(52)
            .ifBlank { "WAIT FOR IT!" }
    }

    suspend fun analyzeLiveChatBurst(
        apiKey: String,
        modelName: String,
        streamTitle: String,
        messages: List<LiveChatMessage>,
        msgCount30s: Int
    ): LiveBurstAnalysis {
        val key = requireApiKey(apiKey)
        val model = modelName.trim().ifBlank { GeminiSettings.DEFAULT_GEMINI_MODEL }

        val sampleText = messages.takeLast(25).joinToString("\n") { "${it.author}: ${it.text}" }
        val prompt = "A YouTube live stream titled \"$streamTitle\" just hit $msgCount30s chat messages in 30 seconds. " +
            "Analyze these recent chat messages and generate a viral short-form hook headline, viral score (1-100), and summary:\n$sampleText"

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("hookTitle", JSONObject().put("type", "STRING"))
                put("viralScore", JSONObject().put("type", "INTEGER"))
                put("summary", JSONObject().put("type", "STRING"))
            })
            put("required", JSONArray(listOf("hookTitle", "viralScore", "summary")))
        }

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
            })
        }

        val jsonString = callGeminiRest(key, model, payload, timeoutMs = 45_000L)
        val json = JSONObject(jsonString)
        return LiveBurstAnalysis(
            hookTitle = json.optString("hookTitle", "INSANE MOMENT!").trim().uppercase(),
            viralScore = json.optInt("viralScore", 90).coerceIn(1, 100),
            summary = json.optString("summary", "Chat exploded during a peak live moment.")
        )
    }

    private suspend fun callGeminiRest(
        apiKey: String,
        modelName: String,
        payload: JSONObject,
        timeoutMs: Long
    ): String {
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    val endpoint = "$GEMINI_REST_BASE/$modelName:generateContent"
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-goog-api-key", apiKey)
                        .post(
                            payload.toString()
                                .toRequestBody("application/json; charset=utf-8".toMediaType())
                        )
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val errMsg = runCatching {
                                JSONObject(bodyString)
                                    .optJSONObject("error")
                                    ?.optString("message")
                            }.getOrNull()
                            val detail = errMsg?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                            throw IllegalStateException("Gemini API error (${response.code}): $detail")
                        }

                        val root = JSONObject(bodyString)
                        val candidates = root.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                            ?: throw IllegalStateException("Gemini returned no candidates.")
                        val parts = firstCandidate
                            .optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?: throw IllegalStateException("Gemini returned empty content parts.")

                        val combined = buildString {
                            for (i in 0 until parts.length()) {
                                val partText = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                                append(partText)
                            }
                        }
                        if (combined.isBlank()) {
                            throw IllegalStateException("Gemini returned an empty text response.")
                        }
                        combined
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini REST request timed out", e)
            throw IllegalStateException("Gemini API request timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini REST request failed", e)
            throw e
        }
    }

    private fun requireApiKey(apiKey: String): String {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) {
            "Gemini API key is not set. Open Settings to enter your API key."
        }
        return trimmed
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        return stream.toByteArray()
    }
}
