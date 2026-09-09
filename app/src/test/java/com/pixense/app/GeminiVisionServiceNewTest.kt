package com.pixense.app

import com.pixense.app.data.ai.GeminiVisionServiceNew
import com.pixense.app.data.model.DetectedSceneCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiVisionServiceNewTest {

    @Test
    fun `determineClosestAspectRatio maps standard camera aspect ratios correctly`() {
        // Landscape 4:3
        assertEquals("4:3", GeminiVisionServiceNew.determineClosestAspectRatio(1200, 900))

        // Landscape 16:9
        assertEquals("16:9", GeminiVisionServiceNew.determineClosestAspectRatio(1920, 1080))

        // Square 1:1
        assertEquals("1:1", GeminiVisionServiceNew.determineClosestAspectRatio(1000, 1000))

        // Portrait 3:4
        assertEquals("3:4", GeminiVisionServiceNew.determineClosestAspectRatio(900, 1200))

        // Portrait 9:16
        assertEquals("9:16", GeminiVisionServiceNew.determineClosestAspectRatio(1080, 1920))

        // Edge case: 0 dimensions fallback to 1:1
        assertEquals("1:1", GeminiVisionServiceNew.determineClosestAspectRatio(0, 0))
    }

    @Test
    fun `buildUnifiedEnhancementPrompt contains all critical photo restoration requirements`() {
        val prompt = GeminiVisionServiceNew.buildUnifiedEnhancementPrompt(1920, 1080)

        // Verifies 4K resolution targeting
        assertTrue("Prompt should target 4K resolution", prompt.contains("4K"))

        // Verifies lighting, colors, focus, blur, noise, low light
        assertTrue("Prompt should address lighting/exposure", prompt.contains("LIGHTING"))
        assertTrue("Prompt should address colors/vibrancy", prompt.contains("COLORS"))
        assertTrue("Prompt should address focus/sharpness", prompt.contains("FOCUS"))
        assertTrue("Prompt should address blur/camera shake", prompt.contains("BLUR"))
        assertTrue("Prompt should address noise/grain", prompt.contains("NOISE"))
        assertTrue("Prompt should address low light", prompt.contains("LOW LIGHT"))

        // Verifies scene-specific enhancements
        assertTrue("Prompt should cover landscape & nature", prompt.contains("Landscape & Nature"))
        assertTrue("Prompt should cover food & cuisine", prompt.contains("Food & Cuisine"))
        assertTrue("Prompt should cover portrait & people", prompt.contains("Portrait & People"))

        // Verifies scene and text detection
        assertTrue("Prompt should require scene detection", prompt.contains("SCENE"))
        assertTrue("Prompt should require text detection", prompt.contains("TEXT DETECTION") || prompt.contains("detectedText"))

        // Verifies strict fidelity mandate (no alteration of original things)
        assertTrue("Prompt must mandate zero content alteration", prompt.contains("ZERO CONTENT ALTERATION"))
        assertTrue("Prompt must mandate visual loyalty to original", prompt.contains("VISUAL LOYALTY") || prompt.contains("visually identical"))
        assertTrue("Prompt must forbid hallucinations", prompt.contains("NO AI HALLUCINATIONS"))
    }

    @Test
    fun `parseUnifiedAiResponse correctly parses scene, detected text, and metrics`() {
        val jsonSample = """
            {
              "category": "LANDSCAPE_NATURE",
              "confidence": 98,
              "detectedText": "Yosemite National Park",
              "detectedElements": ["Mountain", "Pine Trees", "Clear Sky"],
              "lightingCondition": "Golden Hour Sun",
              "noiseAndBlurAssessment": "Clean optical focus",
              "lightingScore": 94,
              "sharpnessScore": 98,
              "noiseReductionScore": 97,
              "blurReductionScore": 96,
              "dynamicRange": "Rich HDR 4K",
              "colorTone": "Natural Vibrant Foliage",
              "aiInsight": "Enhanced mountain dynamic range and vibrant forest greens while preserving scenery.",
              "tailoredCorrectionPlan": "Lush foliage boost and cloud recovery"
            }
        """.trimIndent()

        val (analysis, detection) = GeminiVisionServiceNew.parseUnifiedAiResponse(jsonSample)

        assertEquals(DetectedSceneCategory.LANDSCAPE_NATURE, detection.category)
        assertEquals(98, detection.confidence)
        assertEquals("Yosemite National Park", detection.detectedText)
        assertEquals("Yosemite National Park", analysis.detectedText)
        assertEquals(3, detection.detectedElements.size)
        assertEquals(94, analysis.lightingScore)
        assertEquals(98, analysis.sharpnessScore)
        assertEquals("4K Photo-Quality (Native Aspect)", analysis.resolutionUpscale)
    }

    @Test
    fun `parseUnifiedAiResponse handles fallback for empty or non-json input`() {
        val (analysis, detection) = GeminiVisionServiceNew.parseUnifiedAiResponse("Not a json response")

        assertNotNull(analysis)
        assertNotNull(detection)
        assertEquals(DetectedSceneCategory.GENERAL_AUTO, detection.category)
        assertTrue(analysis.confidenceScore > 0)
        assertNotNull(analysis.aiInsight)
    }

    @Test
    fun `parseResponseParts parses candidates and parts correctly`() {
        val responseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "{\"category\": \"FOOD\", \"confidence\": 95}" },
                      { "inlineData": { "mimeType": "image/jpeg", "data": "dGVzdA==" } }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val (bitmap, text) = GeminiVisionServiceNew.parseResponseParts(responseJson)

        assertTrue(text.contains("FOOD"))
        // Bitmap creation in Robolectric from valid base64
        // text is extracted
    }
}
