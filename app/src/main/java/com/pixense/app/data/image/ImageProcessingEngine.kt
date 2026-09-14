package com.pixense.app.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageProcessingEngine {

    /**
     * Post-processes an AI-enhanced image from Gemini to guarantee:
     * 1. Exact same aspect ratio and native resolution as the original camera photo.
     * 2. High-fidelity optical detail preservation: extracts native high-frequency
     *    sensor details (micro-textures, fine lines, sharp edges, pores, text) from
     *    the original full-resolution camera capture and fuses them with the AI's
     *    improved exposure, HDR dynamic range, and scene-specific color grading.
     * 3. Adaptive edge & micro-contrast enhancement so that when zooming in, the enhanced image
     *    retains or exceeds the quality, sharpness, and clarity of the original photo.
     */
    suspend fun postProcessNanoBananaImage(
        aiBitmap: Bitmap,
        originalBitmap: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val origW = originalBitmap.width
        val origH = originalBitmap.height
        if (origW <= 0 || origH <= 0) return@withContext aiBitmap

        val targetAspect = origW.toFloat() / origH.toFloat()
        val aiAspect = aiBitmap.width.toFloat() / aiBitmap.height.toFloat()

        // 1. Center-crop aiBitmap if aspect ratio differs
        val croppedAiBitmap: Bitmap = if (abs(targetAspect - aiAspect) > 0.005f) {
            val cropWidth: Int
            val cropHeight: Int
            if (aiAspect > targetAspect) {
                cropHeight = aiBitmap.height
                cropWidth = (aiBitmap.height * targetAspect).toInt().coerceIn(1, aiBitmap.width)
            } else {
                cropWidth = aiBitmap.width
                cropHeight = (aiBitmap.width / targetAspect).toInt().coerceIn(1, aiBitmap.height)
            }
            val startX = ((aiBitmap.width - cropWidth) / 2).coerceIn(0, aiBitmap.width - cropWidth)
            val startY = ((aiBitmap.height - cropHeight) / 2).coerceIn(0, aiBitmap.height - cropHeight)
            Bitmap.createBitmap(aiBitmap, startX, startY, cropWidth, cropHeight)
        } else {
            aiBitmap
        }

        // 2. High-quality scaling to exact original dimensions with anti-aliasing & filtering
        val scaledAiBitmap: Bitmap = if (croppedAiBitmap.width != origW || croppedAiBitmap.height != origH) {
            val dest = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
            val srcRect = Rect(0, 0, croppedAiBitmap.width, croppedAiBitmap.height)
            val dstRect = Rect(0, 0, origW, origH)
            canvas.drawBitmap(croppedAiBitmap, srcRect, dstRect, paint)
            dest
        } else {
            if (croppedAiBitmap.config != Bitmap.Config.ARGB_8888 || !croppedAiBitmap.isMutable) {
                croppedAiBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                croppedAiBitmap
            }
        }

        // 3. High-Fidelity Optical Detail Preservation & Fusion
        // Safely access original bitmap pixels (handle HARDWARE bitmap config gracefully)
        val safeOriginal = if (originalBitmap.config == Bitmap.Config.HARDWARE) {
            originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            originalBitmap
        }

        try {
            fuseOpticalDetails(
                original = safeOriginal,
                aiImage = scaledAiBitmap,
                width = origW,
                height = origH
            )
        } catch (_: Exception) {
            // If fusion encounters an issue, fallback gracefully to high-quality scaled AI image
        } finally {
            if (safeOriginal !== originalBitmap && !safeOriginal.isRecycled) {
                safeOriginal.recycle()
            }
        }

        scaledAiBitmap
    }

    /**
     * Extracts optical high-frequency details from the original camera photo and fuses
     * them into the AI image. This preserves all native sensor resolution, sharp optical edges,
     * fine textures, and micro-contrast so the image remains razor sharp when zooming.
     */
    private suspend fun fuseOpticalDetails(
        original: Bitmap,
        aiImage: Bitmap,
        width: Int,
        height: Int
    ) = coroutineScope {
        val chunkSize = 256
        val numChunks = (height + chunkSize - 1) / chunkSize

        val tasks = (0 until numChunks).map { chunkIndex ->
            async(Dispatchers.Default) {
                val yStart = chunkIndex * chunkSize
                val yEnd = min(yStart + chunkSize, height)
                val chunkH = yEnd - yStart

                // Read original rows including 1-row padding above and below for 3x3 filter
                val expStart = max(0, yStart - 1)
                val expEnd = min(height, yEnd + 1)
                val expRows = expEnd - expStart

                val origPixels = IntArray(width * expRows)
                val aiPixels = IntArray(width * chunkH)
                val outPixels = IntArray(width * chunkH)

                original.getPixels(origPixels, 0, width, 0, expStart, width, expRows)
                aiImage.getPixels(aiPixels, 0, width, 0, yStart, width, chunkH)

                for (y in yStart until yEnd) {
                    val r = y - expStart
                    val rPrev = max(0, r - 1)
                    val rNext = min(expRows - 1, r + 1)
                    val outRowOffset = (y - yStart) * width

                    val rowOffsetPrev = rPrev * width
                    val rowOffsetCurr = r * width
                    val rowOffsetNext = rNext * width

                    for (x in 0 until width) {
                        val xPrev = if (x > 0) x - 1 else 0
                        val xNext = if (x < width - 1) x + 1 else width - 1

                        val origPix = origPixels[rowOffsetCurr + x]
                        val oR = (origPix shr 16) and 0xFF
                        val oG = (origPix shr 8) and 0xFF
                        val oB = origPix and 0xFF
                        val origY = (77 * oR + 150 * oG + 29 * oB) shr 8

                        // 3x3 Gaussian-weighted spatial low-pass filter around (x, y)
                        val topPix = origPixels[rowOffsetPrev + x]
                        val botPix = origPixels[rowOffsetNext + x]
                        val leftPix = origPixels[rowOffsetCurr + xPrev]
                        val rightPix = origPixels[rowOffsetCurr + xNext]
                        val tlPix = origPixels[rowOffsetPrev + xPrev]
                        val trPix = origPixels[rowOffsetPrev + xNext]
                        val blPix = origPixels[rowOffsetNext + xPrev]
                        val brPix = origPixels[rowOffsetNext + xNext]

                        val topY = (77 * ((topPix shr 16) and 0xFF) + 150 * ((topPix shr 8) and 0xFF) + 29 * (topPix and 0xFF)) shr 8
                        val botY = (77 * ((botPix shr 16) and 0xFF) + 150 * ((botPix shr 8) and 0xFF) + 29 * (botPix and 0xFF)) shr 8
                        val leftY = (77 * ((leftPix shr 16) and 0xFF) + 150 * ((leftPix shr 8) and 0xFF) + 29 * (leftPix and 0xFF)) shr 8
                        val rightY = (77 * ((rightPix shr 16) and 0xFF) + 150 * ((rightPix shr 8) and 0xFF) + 29 * (rightPix and 0xFF)) shr 8
                        val tlY = (77 * ((tlPix shr 16) and 0xFF) + 150 * ((tlPix shr 8) and 0xFF) + 29 * (tlPix and 0xFF)) shr 8
                        val trY = (77 * ((trPix shr 16) and 0xFF) + 150 * ((trPix shr 8) and 0xFF) + 29 * (trPix and 0xFF)) shr 8
                        val blY = (77 * ((blPix shr 16) and 0xFF) + 150 * ((blPix shr 8) and 0xFF) + 29 * (blPix and 0xFF)) shr 8
                        val brY = (77 * ((brPix shr 16) and 0xFF) + 150 * ((brPix shr 8) and 0xFF) + 29 * (brPix and 0xFF)) shr 8

                        val yLow = (tlY + 2 * topY + trY + 2 * leftY + 4 * origY + 2 * rightY + blY + 2 * botY + brY) shr 4

                        // High-frequency optical detail from original sensor
                        val detail = origY - yLow
                        val absDetail = abs(detail)

                        // Noise-gate & detail gain:
                        // Below threshold (noise/flat sky): gain = 0 (keep AI's smooth clean field)
                        // Above threshold (real textures/edges): boost optical micro-contrast by 1.25x
                        val effectiveDetail = when {
                            absDetail < 2 -> 0f
                            absDetail <= 5 -> detail * ((absDetail - 2f) / 3f) * 1.0f
                            else -> detail * 1.25f
                        }

                        val aiPix = aiPixels[outRowOffset + x]
                        val aiR = (aiPix shr 16) and 0xFF
                        val aiG = (aiPix shr 8) and 0xFF
                        val aiB = aiPix and 0xFF
                        val aiY = (77 * aiR + 150 * aiG + 29 * aiB) shr 8

                        // Shadow noise attenuation: slightly reduce detail injection in pitch black shadows
                        val shadowDamping = if (aiY < 25) (aiY / 25f) else 1.0f
                        val deltaY = (effectiveDetail * shadowDamping).toInt()

                        val outR = (aiR + deltaY).coerceIn(0, 255)
                        val outG = (aiG + deltaY).coerceIn(0, 255)
                        val outB = (aiB + deltaY).coerceIn(0, 255)

                        outPixels[outRowOffset + x] = -0x1000000 or (outR shl 16) or (outG shl 8) or outB
                    }
                }

                // Write fused pixels back into scaledAiBitmap
                aiImage.setPixels(outPixels, 0, width, 0, yStart, width, chunkH)
            }
        }

        tasks.awaitAll()
    }
}
