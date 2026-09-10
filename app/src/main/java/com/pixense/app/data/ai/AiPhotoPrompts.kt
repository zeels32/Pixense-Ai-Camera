package com.pixense.app.data.ai

import com.pixense.app.data.model.AiPhotoOperation
import java.util.Locale

/**
 * Dedicated Gemini AI prompts for Pixense photo operations:
 * - FIX: Intelligent overall quality improvement
 * - CLEAN: Removal of unwanted objects, distractions, photobombers, wires
 * - UNBLUR: Recovery of optical clarity, focus, and edge detail
 * - RESTORE: Repair of vintage, old, faded, or damaged photographs
 *
 * Prompts are kept strictly separate from UI code and ensure 4K output with structured JSON analysis.
 */
object AiPhotoPrompts {

    private const val COMMON_OUTPUT_SCHEMA = """
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
    """

    fun getPromptForOperation(operation: AiPhotoOperation, width: Int, height: Int): String {
        val aspect = String.format(Locale.US, "%.2f", width.toFloat() / height.toFloat().coerceAtLeast(1f))
        return when (operation) {
            AiPhotoOperation.FIX -> buildFixPrompt(width, height, aspect)
            AiPhotoOperation.CLEAN -> buildCleanPrompt(width, height, aspect)
            AiPhotoOperation.UNBLUR -> buildUnblurPrompt(width, height, aspect)
            AiPhotoOperation.RESTORE -> buildRestorePrompt(width, height, aspect)
        }
    }

    /**
     * FIX PROMPT:
     * Intelligently improves overall technical quality: exposure, lighting, dynamic range,
     * contrast, noise, mild blur, weak detail, color balance, washed-out tones.
     * Preserves subject identity, skin texture, composition.
     * Goal: Better version of the exact same photograph.
     */
    fun buildFixPrompt(width: Int, height: Int, aspect: String): String {
        return """
            You are a World-Class Computational Photography AI Master and Optical Photo Restorer.

            PRIMARY MISSION: AUTO FIX & MASTER ENHANCE
            Input dimensions: ${width}x${height} (Aspect Ratio: $aspect).
            Produce an ultra-high 4K resolution remastering that intelligently diagnoses and corrects technical photo flaws.

            CORE TECHNICAL ENHANCEMENTS:
            - LIGHTING & EXPOSURE: Balance exposure dynamically. Gently lift crushed shadows without washing out contrast. Recover clipped highlight details in skies, lamps, and reflective surfaces.
            - CONTRAST & DYNAMIC RANGE: Deliver natural HDR depth. Eliminate dull, flat haze while preserving authentic midtone transitions.
            - COLOR ACCURACY & TONE: Fix washed-out, undersaturated, or tinted colors. Deliver rich, lifelike color depth and accurate white balance.
            - SENSOR NOISE & GRAIN: Cleanly eliminate ISO sensor noise, compression banding, and digital grain while retaining genuine micro-textures.
            - DETAIL & SUBTLE ACUITY: Bring out subtle texture acuity in clothing, foliage, architecture, and background depth.

            FIDELITY & IDENTITY MANDATE:
            - PRESERVE IDENTITY & NATURAL SKIN: Maintain 100% fidelity to human facial identity, eye characteristics, natural skin pores, and authentic skin tones. Do NOT apply plastic/wax airbrushing or cosmetic modifications.
            - PRESERVE COMPOSITION & SUBJECT: Do NOT alter the framing, perspective, original subjects, or geometry of the scene.
            - NO HALLUCINATIONS: Do not invent fantasy elements or alter objects. The image must look like a pristine, masterfully captured version of the exact same photograph.

            $COMMON_OUTPUT_SCHEMA
        """.trimIndent()
    }

    /**
     * CLEAN PROMPT:
     * Focuses specifically on removing unwanted visual distractions:
     * Background people, photobombers, wires, cables, trash, poles, signs, minor clutter.
     * Inpaints and reconstructs cleared areas naturally.
     */
    fun buildCleanPrompt(width: Int, height: Int, aspect: String): String {
        return """
            You are a World-Class Computational Photography AI Master specializing in Object Inpainting & Visual Cleanup.

            PRIMARY MISSION: OBJECT & DISTRACTION CLEANUP
            Input dimensions: ${width}x${height} (Aspect Ratio: $aspect).
            Produce an ultra-high 4K resolution photograph where unwanted visual distractions and clutter are seamlessly removed.

            CLEANUP DIRECTIVES:
            - DISTRACTIONS TO REMOVE:
              * Background photobombers and incidental pedestrians in the background.
              * Overhead wires, utility cables, and power cords.
              * Litter, trash, debris, and ground blemishes.
              * Visual intrusions such as unwanted poles, barricades, distracting signs, and stickers.
            - SEAMLESS NATURAL RECONSTRUCTION:
              * Inpaint and reconstruct the background behind removed distractions so it looks 100% natural.
              * Match surrounding textures (walls, sky, grass, pavement, bokeh foliage) with optical realism.
              * Preserve exact lighting direction, shadow gradients, atmospheric perspective, and focal depth.

            PRESERVATION MANDATE:
            - PRIMARY SUBJECT INTEGRITY: Strictly protect the primary subject(s), main persons, central figures, and intentional objects. Do NOT modify them.
            - FACIAL IDENTITY: Strictly preserve the identity, expressions, and features of the main subjects.
            - MINIMAL INTRUSION: Do not aggressively alter or erase background elements that form the legitimate context or architecture of the photograph unless they are clear distractions.

            $COMMON_OUTPUT_SCHEMA
        """.trimIndent()
    }

    /**
     * UNBLUR PROMPT:
     * Focuses specifically on recovering optical clarity and detail:
     * Reducing perceived blur, camera shake, motion blur, and lens softness.
     * Recovers edge definition and facial clarity without harsh artifacts.
     */
    fun buildUnblurPrompt(width: Int, height: Int, aspect: String): String {
        return """
            You are a World-Class Computational Photography AI Master specializing in Optical Deconvolution & Focus Recovery.

            PRIMARY MISSION: UNBLUR & OPTICAL CLARITY
            Input dimensions: ${width}x${height} (Aspect Ratio: $aspect).
            Produce an ultra-high 4K resolution photograph recovering crisp optical acuity and fine detail from motion or focus softness.

            FOCUS & DEBLUR DIRECTIVES:
            - CAMERA SHAKE & MOTION BLUR: Deconvolve rotational, horizontal, or vertical camera shake blur, rendering edges and silhouettes crisp and steady.
            - OPTICAL SOFTNESS RECOVERY: Correct out-of-focus softness across the primary focal plane, bringing the intended subject into razor-sharp focus.
            - FINE DETAIL RESTORATION: Re-establish crisp definition in eyelashes, irises, hair strands, fabric weaves, text lettering, and tactile surface edges.
            - COMPRESSION ARTIFACT REMOVAL: Smooth JPEG compression blocking and ringing artifacts around high-frequency edges.

            ARTIFACT PREVENTION & IDENTITY MANDATE:
            - NO ARTIFICIAL SHARPENING HALOS: Avoid excessive sharpening, stark white halo outlines, or cartoonish ringing around silhouettes.
            - AVOID PLASTIC TEXTURES: Do not replace natural soft areas with artificial plastic surfaces or waxy skin. Preserve realistic skin pores.
            - STRICT IDENTITY LOYALTY: Do not alter facial structure, identity, or expressions when restoring facial focus.
            - PRESERVE AUTHENTIC BOKEH: If the original background has legitimate shallow depth-of-field bokeh blur, maintain that natural photographic separation.

            $COMMON_OUTPUT_SCHEMA
        """.trimIndent()
    }

    /**
     * RESTORE PROMPT:
     * Targets old, faded, damaged, or degraded photographs:
     * Scratches, dust, fading, noise, low contrast, film artifacts, paper tears/creases.
     * Preserves historical appearance, character, and identity without unnecessary modernization.
     */
    fun buildRestorePrompt(width: Int, height: Int, aspect: String): String {
        return """
            You are a World-Class Archival Conservator and AI Optical Photo Restorer.

            PRIMARY MISSION: PHOTO RESTORATION & ARCHIVAL REPAIR
            Input dimensions: ${width}x${height} (Aspect Ratio: $aspect).
            Produce an ultra-high 4K resolution restoration of this degraded, vintage, or damaged photograph.

            PHYSICAL & CHEMICAL DAMAGE RESTORATION:
            - PHYSICAL BLEMISHES: Inpaint and repair physical scratches, dust specks, emulsion cracks, paper tears, water spots, and fold creases.
            - FADING & AGING CORRECTION: Re-establish lost tonal depth, dynamic range, and rich shadow contrast from chemical fading or sun exposure.
            - FILM & SCAN ARTIFACTS: Clean harsh scanner noise, half-tone printing patterns, and chemical grain clumping while honoring authentic analog texture.
            - CLARITY & CONTRAST: Restore clarity to blurred, soft, or aged vintage captures.

            HISTORICAL PRESERVATION MANDATE:
            - PRESERVE HISTORICAL CHARACTER: Maintain authentic period character, vintage clothing, hairstyle, background architecture, and historical atmosphere.
            - PRESERVE IDENTITY: Faithfully preserve the facial features, ancestry, identity, and demeanor of the subjects.
            - COLOR INTEGRITY: Do NOT automatically colorize a black-and-white or sepia image unless color information is genuinely present. Respect original monochrome or sepia tonal values.
            - AVOID MODERNIZATION: Do not make historical photos look like modern smartphone digital selfies. Preserve the soul and dignity of the original photograph.

            $COMMON_OUTPUT_SCHEMA
        """.trimIndent()
    }
}
