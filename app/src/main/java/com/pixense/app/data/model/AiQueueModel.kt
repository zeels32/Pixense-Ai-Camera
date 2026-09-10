package com.pixense.app.data.model

import android.net.Uri

sealed interface QueueItemStatus {
    data object Pending : QueueItemStatus
    data class InProgress(val stage: String) : QueueItemStatus
    data class Completed(val enhancedUri: Uri, val entityId: Long) : QueueItemStatus
    data class Failed(val error: String) : QueueItemStatus
    data class Stopped(val reason: String = "Processing stopped") : QueueItemStatus
}

data class EnhancementQueueItem(
    val id: String,
    val photo: CameraPhoto,
    val preset: EnhancementPreset,
    val operation: AiPhotoOperation = AiPhotoOperation.FIX,
    val status: QueueItemStatus = QueueItemStatus.Pending,
    val progress: Float = 0f,
    val queuedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val entitlementType: com.pixense.app.data.repository.EntitlementType? = null
)
