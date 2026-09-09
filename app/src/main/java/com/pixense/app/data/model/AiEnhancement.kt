package com.pixense.app.data.model

import android.graphics.Bitmap

/**
 * Single Unified Auto-Intelligent Enhancement Preset.
 * Detects the scene type (portrait, low light, food, texture, landscape, etc.) via Gemini LLM
 * and applies targeted restoration using Gemini Image Model.
 */
enum class EnhancementPreset(val title: String, val description: String, val iconName: String) {
    AUTO("Auto Intelligent", "Detects scene type (Portrait, Low Light, Food, Texture, etc.) & custom-remasters with Gemini AI", "auto_awesome"),
    // Legacy entries preserved for Room database enum deserialization compatibility
    AUTO_PRO("Auto Intelligent", "Detects scene type & custom-remasters with Gemini AI", "auto_awesome"),
    HDR_VIVID("Auto Intelligent", "Detects scene type & custom-remasters with Gemini AI", "auto_awesome"),
    PORTRAIT_POLISH("Auto Intelligent", "Detects scene type & custom-remasters with Gemini AI", "auto_awesome"),
    LOW_LIGHT_FIX("Auto Intelligent", "Detects scene type & custom-remasters with Gemini AI", "auto_awesome"),
    SUPER_CLARITY("Auto Intelligent", "Detects scene type & custom-remasters with Gemini AI", "auto_awesome");

    companion object {
        val DEFAULT = AUTO
    }
}

/**
 * Scene and subject categories detected by the Gemini LLM before applying enhancement.
 */
enum class DetectedSceneCategory(
    val title: String,
    val emoji: String,
    val description: String,
    val promptFocus: String
) {
    PORTRAIT(
        "Portrait & Face",
        "👤",
        "Human subject detected. Preserving natural facial features, skin tone warmth, and gentle bokeh background.",
        "Human Portrait Mode: Strictly preserve facial identity, eye sharpness, natural skin pores and realistic skin tones. Soften background distractions with gentle bokeh while maintaining crisp subject edges without artificial airbrushing."
    ),
    LOW_LIGHT(
        "Low Light & Night",
        "🌙",
        "Dark environment or night scene detected. Eliminating ISO digital sensor noise and lifting deep shadow details.",
        "Low Light & Night Mode: Heavily reduce sensor noise and digital grain, illuminate underexposed shadow areas, protect bright light sources from blooming, and balance natural nighttime color temperature."
    ),
    FOOD(
        "Food & Cuisine",
        "🍽️",
        "Culinary/dish subject detected. Enhancing appetizing warmth, saturation depth, and glistening surface details.",
        "Food & Culinary Mode: Enhance appetizing color saturation, highlight garnish textures and sauce glistening, optimize warm color temperature, and bring out delicious dish depth."
    ),
    TEXTURE_MACRO(
        "Texture & Macro",
        "🔍",
        "Close-up micro-details or intricate surfaces detected. Maximizing edge contrast, micro-textures, and razor clarity.",
        "Macro & Texture Mode: Maximize micro-contrast, restore intricate surface grain (fabrics, wood, plants, materials), de-blur fine details, and produce razor-sharp edge clarity."
    ),
    LANDSCAPE_NATURE(
        "Landscape & Nature",
        "🌿",
        "Outdoor scenery, greenery, or sky detected. Maximizing dynamic range, foliage vibrance, and atmospheric clarity.",
        "Landscape & Nature Mode: Maximize dynamic range (HDR), recover cloud and sky textures, boost lush foliage greens and sky blues, and eliminate atmospheric haze."
    ),
    ARCHITECTURE_URBAN(
        "Architecture & City",
        "🏙️",
        "Built structures or geometric lines detected. Enhancing structural lines, contrast, and glass/metal reflections.",
        "Architecture & Urban Mode: Sharpen geometric building edges, increase structural contrast, enhance reflections on glass and metal surfaces, and balance architectural exposure."
    ),
    DOCUMENT_TEXT(
        "Document & Text",
        "📄",
        "Text, document, or printed material detected. Maximizing legibility and contrast.",
        "Document & Text Mode: Maximize contrast between text and background, remove shadow creases and paper glare, and render typography razor-sharp."
    ),
    GENERAL_AUTO(
        "Auto Intelligent Scene",
        "✨",
        "Balanced auto-enhancement optimizing exposure, color fidelity, and edge clarity.",
        "Auto Intelligent Mode: Optimally balance exposure, clean sensor noise, enhance color fidelity, and sharpen optical focus."
    );

    companion object {
        fun fromString(name: String?): DetectedSceneCategory {
            if (name == null) return GENERAL_AUTO
            val upper = name.uppercase()
            return entries.firstOrNull { 
                upper.contains(it.name) || upper.contains(it.title.uppercase())
            } ?: when {
                upper.contains("PORTRAIT") || upper.contains("PERSON") || upper.contains("FACE") || upper.contains("PEOPLE") || upper.contains("SELFIE") -> PORTRAIT
                upper.contains("LOW LIGHT") || upper.contains("NIGHT") || upper.contains("DARK") || upper.contains("SHADOW") -> LOW_LIGHT
                upper.contains("FOOD") || upper.contains("MEAL") || upper.contains("DISH") || upper.contains("DRINK") || upper.contains("RESTAURANT") -> FOOD
                upper.contains("TEXTURE") || upper.contains("MACRO") || upper.contains("CLOSE") || upper.contains("DETAIL") || upper.contains("PATTERN") -> TEXTURE_MACRO
                upper.contains("LANDSCAPE") || upper.contains("NATURE") || upper.contains("OUTDOOR") || upper.contains("SKY") || upper.contains("MOUNTAIN") || upper.contains("PLANT") -> LANDSCAPE_NATURE
                upper.contains("ARCHITECTURE") || upper.contains("BUILDING") || upper.contains("CITY") || upper.contains("URBAN") || upper.contains("STREET") -> ARCHITECTURE_URBAN
                upper.contains("DOC") || upper.contains("TEXT") || upper.contains("PAPER") || upper.contains("RECEIPT") || upper.contains("BOOK") -> DOCUMENT_TEXT
                else -> GENERAL_AUTO
            }
        }
    }
}

/**
 * Result from the Gemini LLM image detection phase before remastering.
 */
data class SceneDetectionResult(
    val category: DetectedSceneCategory,
    val confidence: Int, // 0 to 100%
    val detectedElements: List<String>,
    val lightingCondition: String,
    val noiseAndBlurAssessment: String,
    val tailoredCorrectionPlan: String,
    val detectedText: String? = null
)

data class AiPhotoAnalysis(
    val sceneType: String,
    val category: DetectedSceneCategory = DetectedSceneCategory.GENERAL_AUTO,
    val confidenceScore: Int = 95,
    val lightingScore: Int = 85,
    val dynamicRange: String = "HDR Balanced",
    val colorTone: String = "Natural",
    val suggestedPreset: EnhancementPreset = EnhancementPreset.AUTO,
    val aiInsight: String = "Scene analyzed by Gemini LLM & remastered with Gemini Vision.",
    val sharpnessScore: Int = 90,
    val noiseReductionScore: Int = 92,
    val blurReductionScore: Int = 94,
    val resolutionUpscale: String = "4K Photo-Quality (Native Aspect)",
    val detectedElements: List<String> = emptyList(),
    val tailoredPlan: String = "",
    val brightnessAdjustment: Float = 0f,
    val contrastAdjustment: Float = 1.15f,
    val saturationAdjustment: Float = 1.1f,
    val warmthAdjustment: Float = 0f,
    val sharpnessAdjustment: Float = 1.2f,
    val detectedText: String? = null
)

sealed interface EnhancementUiState {
    data object Idle : EnhancementUiState
    data class Detecting(val message: String = "Gemini LLM analyzing scene & subject characteristics…") : EnhancementUiState
    data class Processing(val stage: String, val detectedScene: DetectedSceneCategory? = null) : EnhancementUiState
    data class Success(
        val originalBitmap: Bitmap,
        val enhancedBitmap: Bitmap,
        val analysis: AiPhotoAnalysis,
        val preset: EnhancementPreset = EnhancementPreset.AUTO
    ) : EnhancementUiState
    data class Error(val message: String) : EnhancementUiState
}
