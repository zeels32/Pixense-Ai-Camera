package com.pixense.app

import com.pixense.app.data.ai.AiPhotoPrompts
import com.pixense.app.data.model.AiPhotoOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPhotoOperationTest {

    @Test
    fun `verify all four AI photo operations are defined with correct attributes`() {
        val operations = AiPhotoOperation.entries
        assertEquals(4, operations.size)

        val fix = AiPhotoOperation.FIX
        assertEquals("Fix", fix.title)
        assertEquals("✨", fix.iconEmoji)
        assertEquals("FIX", fix.analyticsTag)
        assertTrue(fix.description.isNotEmpty())
        assertTrue(fix.statusMessage.isNotEmpty())

        val clean = AiPhotoOperation.CLEAN
        assertEquals("Clean", clean.title)
        assertEquals("🧹", clean.iconEmoji)
        assertEquals("CLEAN", clean.analyticsTag)
        assertTrue(clean.description.isNotEmpty())
        assertTrue(clean.statusMessage.isNotEmpty())

        val unblur = AiPhotoOperation.UNBLUR
        assertEquals("Unblur", unblur.title)
        assertEquals("🔍", unblur.iconEmoji)
        assertEquals("UNBLUR", unblur.analyticsTag)
        assertTrue(unblur.description.isNotEmpty())
        assertTrue(unblur.statusMessage.isNotEmpty())

        val restore = AiPhotoOperation.RESTORE
        assertEquals("Restore", restore.title)
        assertEquals("🕰️", restore.iconEmoji)
        assertEquals("RESTORE", restore.analyticsTag)
        assertTrue(restore.description.isNotEmpty())
        assertTrue(restore.statusMessage.isNotEmpty())
    }

    @Test
    fun `fromString maps correctly and falls back to DEFAULT on null or unknown`() {
        assertEquals(AiPhotoOperation.FIX, AiPhotoOperation.fromString("fix"))
        assertEquals(AiPhotoOperation.CLEAN, AiPhotoOperation.fromString("CLEAN"))
        assertEquals(AiPhotoOperation.UNBLUR, AiPhotoOperation.fromString("Unblur"))
        assertEquals(AiPhotoOperation.RESTORE, AiPhotoOperation.fromString("restore"))
        assertEquals(AiPhotoOperation.DEFAULT, AiPhotoOperation.fromString(null))
        assertEquals(AiPhotoOperation.DEFAULT, AiPhotoOperation.fromString("unknown_tool"))
    }

    @Test
    fun `prompt for FIX operation contains auto-enhancement and 4K directives`() {
        val prompt = AiPhotoPrompts.getPromptForOperation(AiPhotoOperation.FIX, 1920, 1080)
        assertNotNull(prompt)
        assertTrue("Prompt should mention 4K", prompt.contains("4K"))
        assertTrue("Prompt should mention aspect ratio", prompt.contains("16:9"))
        assertTrue("Prompt should contain FIX specific auto-enhancement directives", prompt.contains("AUTO-ENHANCE") || prompt.contains("AUTOMATIC"))
        assertTrue("Prompt should address lighting/exposure", prompt.contains("LIGHTING"))
        assertTrue("Prompt should mandate strict fidelity", prompt.contains("ZERO CONTENT ALTERATION") || prompt.contains("VISUAL LOYALTY"))
        assertTrue("Prompt should include structured JSON schema", prompt.contains("\"category\"") && prompt.contains("\"aiInsight\""))
    }

    @Test
    fun `prompt for CLEAN operation contains object removal and inpainting directives`() {
        val prompt = AiPhotoPrompts.getPromptForOperation(AiPhotoOperation.CLEAN, 1000, 1000)
        assertNotNull(prompt)
        assertTrue("Prompt should mention 4K", prompt.contains("4K"))
        assertTrue("Prompt should mention square aspect ratio", prompt.contains("1:1"))
        assertTrue("Prompt should mention object removal or distraction cleaning", 
            prompt.contains("REMOVE") || prompt.contains("CLEAN") || prompt.contains("DISTRACTION"))
        assertTrue("Prompt should mention photobombers or unwanted artifacts",
            prompt.contains("photobombers") || prompt.contains("blemishes") || prompt.contains("distractions"))
        assertTrue("Prompt should preserve original main subject", prompt.contains("subject") || prompt.contains("IDENTITY"))
        assertTrue("Prompt should include structured JSON schema", prompt.contains("\"category\"") && prompt.contains("\"aiInsight\""))
    }

    @Test
    fun `prompt for UNBLUR operation contains deblurring and sharpness recovery directives`() {
        val prompt = AiPhotoPrompts.getPromptForOperation(AiPhotoOperation.UNBLUR, 900, 1200)
        assertNotNull(prompt)
        assertTrue("Prompt should mention 4K", prompt.contains("4K"))
        assertTrue("Prompt should mention portrait aspect ratio", prompt.contains("3:4"))
        assertTrue("Prompt should mention unblurring or deconvolution",
            prompt.contains("UNBLUR") || prompt.contains("DEBLUR") || prompt.contains("SHARPNESS"))
        assertTrue("Prompt should address camera shake or motion blur",
            prompt.contains("motion blur") || prompt.contains("camera shake") || prompt.contains("soft focus"))
        assertTrue("Prompt should include structured JSON schema", prompt.contains("\"category\"") && prompt.contains("\"aiInsight\""))
    }

    @Test
    fun `prompt for RESTORE operation contains archival restoration directives`() {
        val prompt = AiPhotoPrompts.getPromptForOperation(AiPhotoOperation.RESTORE, 1080, 1920)
        assertNotNull(prompt)
        assertTrue("Prompt should mention 4K", prompt.contains("4K"))
        assertTrue("Prompt should mention 9:16 aspect ratio", prompt.contains("9:16"))
        assertTrue("Prompt should mention restoration or vintage or aging",
            prompt.contains("RESTORE") || prompt.contains("RESTORATION") || prompt.contains("vintage") || prompt.contains("scratches"))
        assertTrue("Prompt should address tears, scratches, dust, or color fading",
            prompt.contains("scratches") || prompt.contains("creases") || prompt.contains("faded") || prompt.contains("discoloration"))
        assertTrue("Prompt should include structured JSON schema", prompt.contains("\"category\"") && prompt.contains("\"aiInsight\""))
    }
}
