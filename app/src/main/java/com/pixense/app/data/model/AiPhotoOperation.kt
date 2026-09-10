package com.pixense.app.data.model

/**
 * Domain model representing the four primary AI photo operations in Pixense:
 * - FIX: Intelligent overall quality improvement
 * - CLEAN: Removal of unwanted objects, distractions, photobombers, wires
 * - UNBLUR: Recovery of optical clarity, focus, and edge detail
 * - RESTORE: Repair of vintage, old, faded, or damaged photographs
 */
enum class AiPhotoOperation(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val statusMessage: String,
    val analyticsTag: String
) {
    FIX(
        id = "FIX",
        title = "Fix",
        description = "Automatically improve your photo with AI",
        iconEmoji = "✨",
        statusMessage = "Pixense is fixing your photo…",
        analyticsTag = "FIX"
    ),
    CLEAN(
        id = "CLEAN",
        title = "Clean",
        description = "Remove unwanted objects and distractions",
        iconEmoji = "🧹",
        statusMessage = "Pixense is cleaning your photo…",
        analyticsTag = "CLEAN"
    ),
    UNBLUR(
        id = "UNBLUR",
        title = "Unblur",
        description = "Recover clarity and detail",
        iconEmoji = "🔍",
        statusMessage = "Pixense is restoring clarity…",
        analyticsTag = "UNBLUR"
    ),
    RESTORE(
        id = "RESTORE",
        title = "Restore",
        description = "Restore old, faded and damaged photos",
        iconEmoji = "🕰️",
        statusMessage = "Pixense is restoring your photo…",
        analyticsTag = "RESTORE"
    );

    companion object {
        val DEFAULT = FIX

        fun fromString(value: String?): AiPhotoOperation {
            if (value == null) return DEFAULT
            val upper = value.uppercase().trim()
            return entries.firstOrNull { it.id == upper || it.name == upper } ?: DEFAULT
        }
    }
}
