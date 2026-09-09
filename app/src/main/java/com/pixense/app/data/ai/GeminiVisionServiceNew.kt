package com.pixense.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.pixense.app.BuildConfig
import com.pixense.app.data.model.AiPhotoAnalysis
import com.pixense.app.data.model.DetectedSceneCategory
import com.pixense.app.data.model.EnhancementPreset
import com.pixense.app.data.model.SceneDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * GeminiVisionServiceNew:
 * Unified, single-model AI Vision service powered by Gemini multimodal image models
 * (primary: gemini-3.1-flash-image supporting 4K resolution output).
 *
 * Performs in a single multimodal pass:
 * 1. Scene category detection and text detection in the photo.
 * 2. High-fidelity 4K image remastering fixing lighting, colors, focus, blurriness,
 *    sensor noise, low-light details, and scene characteristics (landscape, nature, food, portraits, etc.).
 * 3. Strict preservation of original image elements, identity, geometry, and composition.
 *
 * Does not use local pixel-sampling image analysis or multi-stage separate LLM pipelines.
 */
object GeminiVisionServiceNew {
    private const val TAG = "GeminiVisionServiceNew"

    // Primary single model supporting 4K image generation & multimodal reasoning
    private const val PRIMARY_MODEL = "gemini-3.1-flash-image"
    // Fallback model if primary is unavailable
    private const val FALLBACK_MODEL = "gemini-2.5-flash-image"

    private val CANDIDATE_MODELS = listOf(
        PRIMARY_MODEL,
        FALLBACK_MODEL
    )

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // LRU Cache for processed enhancements
    private val resultCache = LruCache<String, GeminiEnhancementResult>(15)

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (e: Exception) {
            true
        }
    }

    private fun getApiKey(): String {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw GeminiApiException.MissingApiKey()
        }
        return apiKey
    }

    /**
     * Finds the closest aspect ratio string supported by the Gemini imageConfig API.
     */
    fun determineClosestAspectRatio(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "1:1"
        val ratio = width.toFloat() / height.toFloat()

        val supportedRatios = listOf(
            "1:1" to 1.0f,
            "4:3" to 4f / 3f,     // 1.333f
            "3:4" to 3f / 4f,     // 0.75f
            "16:9" to 16f / 9f,   // 1.778f
            "9:16" to 9f / 16f,   // 0.5625f
            "3:2" to 3f / 2f,     // 1.5f
            "2:3" to 2f / 3f,     // 0.667f
            "21:9" to 21f / 9f    // 2.333f
        )

        return supportedRatios.minByOrNull { abs(it.second - ratio) }?.first ?: "1:1"
    }

    /**
     * Builds the unified single-pass prompt for the Gemini multimodal model.
     * Instructs the model to simultaneously:
     * 1. Detect the scene category and any text in the photo.
     * 2. Remaster the photo in 4K resolution (fixing lighting, colors, focus, blurriness,
     *    noise, low light, scene elements like landscape, nature, food, etc.).
     * 3. Maintain 100% fidelity to the original image (enhanced version without changing actual things).
     */
    fun buildUnifiedEnhancementPrompt(width: Int, height: Int): String {
        val aspect = String.format(java.util.Locale.US, "%.2f", width.toFloat() / height.toFloat())
        return """
            You are a World-Class Computational Photography AI Master and Optical Photo Restorer.

            OBJECTIVE:
            Analyze this input photograph (${width}x${height}, Aspect Ratio: $aspect) and produce an ultra-high 4K resolution remastering in a single pass.
            
            PART 1: SCENE & TEXT DETECTION
            - Detect the exact scene category: PORTRAIT, LOW_LIGHT, FOOD, TEXTURE_MACRO, LANDSCAPE_NATURE, ARCHITECTURE_URBAN, DOCUMENT_TEXT, or GENERAL_AUTO.
            - Detect and transcribe any visible text, words, signage, or documents found in the image.
            - Assess lighting quality, focus, blur, and noise levels.

            PART 2: 4K OPTICAL REMASTERING & ENHANCEMENT
            Generate an enhanced, crystal-clear 4K version of this photograph:
            - LIGHTING & EXPOSURE: Restore balanced dynamic range (HDR). Lift crushed shadows without washing out contrast, recover blown highlight details (skies, reflections, lamps), and smooth harsh glare.
            - COLORS & VIBRANCY: Correct washed-out or inaccurate colors. Deliver authentic, natural color depth, rich tones, and precise white balance.
            - FOCUS & SHARPNESS: Eliminate optical softness. Maximize focal acuity, subject micro-contrast, and clean edge definition without unnatural halos or ringing.
            - BLURRINESS & CAMERA SHAKE: Correct camera shake blur, motion blur, and lens softness to yield razor-sharp optical clarity.
            - NOISE & GRAIN SUPPRESSION: Cleanly eliminate ISO sensor noise, digital grain, compression artifacts, and digital haze while retaining natural micro-textures.
            - LOW LIGHT & NIGHT RECOVERY: Cleanly illuminate dark and night scenes, revealing hidden shadow details while preserving natural nighttime atmosphere and protecting light sources from blooming.
            - SCENE-SPECIFIC ENHANCEMENT:
              * Landscape & Nature: Recover dramatic sky/clouds, boost lush foliage greens and water clarity, and remove atmospheric haze.
              * Food & Cuisine: Enhance appetizing warmth, sauce glisten, culinary surface textures, garnish sharpness, and depth.
              * Portrait & People: Preserve natural skin pores, realistic skin tones, iris clarity, and hair strands—NO plastic/wax airbrushing.
              * Architecture & Urban: Crisp structural lines, straight geometry, glass/metal reflections, and material textures.
              * Document & Text: Maximize contrast between text and background, eliminate page shadows and glare, rendering typography razor-sharp.
              * Macro & Texture: Reveal fine micro-textures with maximum edge fidelity.

            NON-NEGOTIABLE FIDELITY & IDENTITY MANDATE:
            - ZERO CONTENT ALTERATION: Do NOT change any actual things, objects, subjects, people, identities, facial structures, expressions, clothing, composition, background elements, framing, or scene geometry from the original image.
            - VISUAL LOYALTY: The output image must be visually identical in composition, framing, and content to the input image—it must simply be the pristine, masterfully enhanced, razor-sharp 4K version of the exact same original photo.
            - NO AI HALLUCINATIONS: Do not add fantasy elements, do not remove genuine items, and do not apply stylized filters or cartoon effects. The result must look like an authentic, high-end optical photograph captured by a flagship camera lens.

            OUTPUT FORMAT:
            1. An enhanced 4K photograph delivered in inlineData.
            2. A structured JSON response in the text content (no markdown code fences):
            {
              "category": "PORTRAIT|LOW_LIGHT|FOOD|TEXTURE_MACRO|LANDSCAPE_NATURE|ARCHITECTURE_URBAN|DOCUMENT_TEXT|GENERAL_AUTO",
              "confidence": 96,
              "detectedText": "Any text/words detected in the image or empty string if none",
              "detectedElements": ["element1", "element2", "element3"],
              "lightingCondition": "Detected lighting assessment",
              "noiseAndBlurAssessment": "Focus, blur, and noise status",
              "lightingScore": 92,
              "sharpnessScore": 96,
              "noiseReductionScore": 95,
              "blurReductionScore": 94,
              "dynamicRange": "Naturally Restored 4K HDR",
              "colorTone": "Authentic & Vibrant",
              "aiInsight": "Clear 1-2 sentence photography insight describing the scene, detected text if any, and enhancements applied",
              "tailoredCorrectionPlan": "Summary of tailored 4K enhancements applied"
            }
        """.trimIndent()
    }

    /**
     * Parses the multimodal text response into structured AiPhotoAnalysis and SceneDetectionResult.
     */
    fun parseUnifiedAiResponse(rawText: String): Pair<AiPhotoAnalysis, SceneDetectionResult> {
        val cleanJson = if (rawText.contains("{") && rawText.contains("}")) {
            rawText.substring(rawText.indexOf("{"), rawText.lastIndexOf("}") + 1).trim()
        } else {
            rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        var category = DetectedSceneCategory.GENERAL_AUTO
        var confidence = 95
        var detectedText: String? = null
        val elementsList = mutableListOf<String>()
        var lightingCondition = "Naturally Balanced Exposure"
        var noiseAssessment = "4K Optical Detail Restored"
        var lightingScore = 90
        var sharpnessScore = 95
        var noiseReductionScore = 94
        var blurReductionScore = 95
        var dynamicRange = "Naturally Restored 4K HDR"
        var colorTone = "Authentic & Vibrant"
        var aiInsight = "Photo enhanced to 4K resolution with restored exposure, color vibrancy, and optical sharpness."
        var tailoredPlan = "Applied single-model 4K photo restoration."

        if (cleanJson.isNotBlank()) {
            try {
                val json = JSONObject(cleanJson)
                val catStr = json.optString("category", "")
                if (catStr.isNotBlank()) {
                    category = DetectedSceneCategory.fromString(catStr)
                }
                confidence = json.optInt("confidence", 95)
                val dt = json.optString("detectedText", "")
                if (dt.isNotBlank()) {
                    detectedText = dt
                }
                val arr = json.optJSONArray("detectedElements")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        elementsList.add(arr.getString(i))
                    }
                }
                lightingCondition = json.optString("lightingCondition", lightingCondition)
                noiseAssessment = json.optString("noiseAndBlurAssessment", noiseAssessment)
                lightingScore = json.optInt("lightingScore", lightingScore)
                sharpnessScore = json.optInt("sharpnessScore", sharpnessScore)
                noiseReductionScore = json.optInt("noiseReductionScore", noiseReductionScore)
                blurReductionScore = json.optInt("blurReductionScore", blurReductionScore)
                dynamicRange = json.optString("dynamicRange", dynamicRange)
                colorTone = json.optString("colorTone", colorTone)
                aiInsight = json.optString("aiInsight", aiInsight)
                tailoredPlan = json.optString("tailoredCorrectionPlan", tailoredPlan)
            } catch (e: Exception) {
                Log.d(TAG, "Fallback parsing AI text: ${e.message}")
            }
        }

        if (elementsList.isEmpty()) {
            elementsList.add("${category.title} Scene")
            if (!detectedText.isNullOrBlank()) {
                elementsList.add("Text: \"${detectedText.take(24)}\"")
            } else {
                elementsList.add("4K Optical Remaster")
            }
        }

        val detection = SceneDetectionResult(
            category = category,
            confidence = confidence,
            detectedElements = elementsList,
            lightingCondition = lightingCondition,
            noiseAndBlurAssessment = noiseAssessment,
            tailoredCorrectionPlan = tailoredPlan,
            detectedText = detectedText
        )

        val analysis = AiPhotoAnalysis(
            sceneType = category.title,
            category = category,
            confidenceScore = confidence,
            lightingScore = lightingScore,
            dynamicRange = dynamicRange,
            colorTone = colorTone,
            suggestedPreset = EnhancementPreset.AUTO,
            aiInsight = aiInsight,
            sharpnessScore = sharpnessScore,
            noiseReductionScore = noiseReductionScore,
            blurReductionScore = blurReductionScore,
            resolutionUpscale = "4K Photo-Quality (Native Aspect)",
            detectedElements = elementsList,
            tailoredPlan = tailoredPlan,
            detectedText = detectedText
        )

        return Pair(analysis, detection)
    }

    /**
     * Unified Single-Model 4K Photo Remastering & Scene/Text Detection Pipeline:
     * - Uses a single Gemini model (gemini-3.1-flash-image) to detect scene and text and
     *   generate the remastered 4K photo in one pass.
     * - Does NOT use local pixel-sampling heuristics.
     */
    suspend fun enhanceAndAnalyze(
        context: Context,
        bitmap: Bitmap,
        preset: EnhancementPreset = EnhancementPreset.AUTO,
        cacheKey: String? = null,
        onStageProgress: ((String) -> Unit)? = null
    ): GeminiEnhancementResult = withContext(Dispatchers.IO) {
        if (!cacheKey.isNullOrBlank()) {
            val cached = resultCache.get(cacheKey)
            if (cached != null) {
                Log.d(TAG, "Serving Gemini restoration result from in-memory cache")
                return@withContext cached
            }
        }

        if (!isNetworkAvailable(context)) {
            throw GeminiApiException.NoInternet()
        }

        val apiKey = getApiKey()

        onStageProgress?.invoke("Gemini 4K AI analyzing scene, text & remastering photo…")

        try {
            val base64Image = scaleAndEncodeBitmap(bitmap, maxDimension = 1920, quality = 95)
            val promptText = buildUnifiedEnhancementPrompt(bitmap.width, bitmap.height)
            val closestAspect = determineClosestAspectRatio(bitmap.width, bitmap.height)

            var lastException: Exception? = null
            var returnedBitmap: Bitmap? = null
            var returnedText = ""

            modelLoop@ for (modelName in CANDIDATE_MODELS) {
                var attempt = 0
                val maxAttemptsForModel = 2
                while (attempt < maxAttemptsForModel) {
                    attempt++
                    try {
                        val supports4kConfig = modelName == PRIMARY_MODEL
                        val jsonBody = buildRequestBody(promptText, base64Image, closestAspect, include4kConfig = supports4kConfig)

                        val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"
                        val request = Request.Builder()
                            .url(url)
                            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) {
                            val errBody = response.body?.string() ?: response.message
                            if (response.code == 429) {
                                Log.w(TAG, "Quota limit / 429 on model $modelName (attempt $attempt). Waiting backoff...")
                                if (attempt < maxAttemptsForModel) {
                                    delay(1500L * attempt)
                                    continue
                                } else {
                                    lastException = GeminiApiException.QuotaExceeded()
                                    break
                                }
                            } else if (response.code == 404) {
                                Log.w(TAG, "Model $modelName not found (404), falling back to alternative...")
                                break
                            } else if (response.code == 400 && supports4kConfig) {
                                // If 4K imageConfig was rejected, retry without imageConfig on same model
                                Log.w(TAG, "imageConfig rejected on $modelName, retrying with standard generationConfig")
                                val fallbackBody = buildRequestBody(promptText, base64Image, closestAspect, include4kConfig = false)
                                val fallbackRequest = Request.Builder()
                                    .url(url)
                                    .post(fallbackBody.toString().toRequestBody("application/json".toMediaType()))
                                    .build()
                                val fallbackResponse = client.newCall(fallbackRequest).execute()
                                if (fallbackResponse.isSuccessful) {
                                    val fallbackBodyStr = fallbackResponse.body?.string() ?: ""
                                    val (parsedBm, parsedTxt) = parseResponseParts(fallbackBodyStr)
                                    if (parsedBm != null) {
                                        returnedBitmap = parsedBm
                                        returnedText = parsedTxt
                                        Log.d(TAG, "Successfully restored photo via $modelName (standard config)")
                                        break@modelLoop
                                    }
                                }
                                lastException = GeminiApiException.ServerError(response.code, errBody)
                                break
                            } else {
                                lastException = GeminiApiException.ServerError(response.code, errBody)
                                break
                            }
                        }

                        val responseBody = response.body?.string() ?: ""
                        val (parsedBm, parsedTxt) = parseResponseParts(responseBody)
                        if (parsedBm != null) {
                            returnedBitmap = parsedBm
                            returnedText = parsedTxt
                            Log.d(TAG, "Successfully enhanced photo in 4K with model $modelName")
                            break@modelLoop
                        }
                    } catch (e: GeminiApiException) {
                        lastException = e
                    } catch (e: Exception) {
                        lastException = e
                    }
                }
            }

            if (returnedBitmap == null) {
                if (lastException is GeminiApiException) {
                    throw lastException
                }
                throw GeminiApiException.GenerationFailed("Gemini 4K Vision AI did not return an enhanced image for this photo. Please try again.")
            }

            val (analysis, detection) = parseUnifiedAiResponse(returnedText)
            val result = GeminiEnhancementResult(
                enhancedBitmap = returnedBitmap,
                analysis = analysis,
                detection = detection
            )

            if (!cacheKey.isNullOrBlank()) {
                resultCache.put(cacheKey, result)
            }

            return@withContext result

        } catch (e: GeminiApiException) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection to Gemini server", e)
            throw GeminiApiException.NoInternet()
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Gemini API connection timed out", e)
            throw GeminiApiException.GeneralError("Gemini AI connection timed out during 4K remastering. Please retry.")
        } catch (e: IOException) {
            Log.e(TAG, "Network I/O error calling Gemini", e)
            throw GeminiApiException.NoInternet("Network connection failed during Gemini AI enhancement.")
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini image enhancement API", e)
            throw GeminiApiException.GeneralError("Gemini AI enhancement failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun buildRequestBody(
        promptText: String,
        base64Image: String,
        aspectRatio: String,
        include4kConfig: Boolean
    ): JSONObject {
        return JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", promptText) })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.85)
                put("maxOutputTokens", 2048)
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
                if (include4kConfig) {
                    put("imageConfig", JSONObject().apply {
                        put("imageSize", "4K")
                        put("aspectRatio", aspectRatio)
                    })
                }
            })
        }
    }

    fun parseResponseParts(responseBody: String): Pair<Bitmap?, String> {
        var returnedBitmap: Bitmap? = null
        var returnedText = ""

        try {
            val jsonResponse = JSONObject(responseBody)
            val candidate = jsonResponse.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inlineData")) {
                        val inlineData = part.getJSONObject("inlineData")
                        val base64Data = inlineData.optString("data")
                        if (base64Data.isNotBlank()) {
                            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                            returnedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(ByteBuffer.wrap(decodedBytes))
                                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                    decoder.isMutableRequired = true
                                }
                            } else {
                                val options = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                }
                                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                            }
                        }
                    } else if (part.has("text")) {
                        returnedText += part.getString("text")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response parts", e)
        }

        return Pair(returnedBitmap, returnedText)
    }

    private fun scaleAndEncodeBitmap(bitmap: Bitmap, maxDimension: Int, quality: Int = 95): String {
        val scale = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1.0f
        )
        val scaled = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
