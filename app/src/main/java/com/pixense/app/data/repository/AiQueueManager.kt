package com.pixense.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import com.pixense.app.data.ai.GeminiApiException
import com.pixense.app.data.ai.GeminiVisionServiceNew
import com.pixense.app.data.analytics.PixenseAnalytics
import com.pixense.app.service.CameraCaptureService
import com.pixense.app.data.db.AppDatabase
import com.pixense.app.data.db.EnhancedPhotoEntity
import com.pixense.app.data.image.ImageProcessingEngine
import com.pixense.app.data.model.AiPhotoAnalysis
import com.pixense.app.data.model.CameraPhoto
import com.pixense.app.data.model.EnhancementPreset
import com.pixense.app.data.model.EnhancementQueueItem
import com.pixense.app.data.model.QueueItemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AiQueueManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = CameraCaptureRepository.getInstance(context)
    private val database = AppDatabase.getDatabase(context)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("camera_ai_prefs", Context.MODE_PRIVATE)

    private val _queue = MutableStateFlow<List<EnhancementQueueItem>>(emptyList())
    val queue: StateFlow<List<EnhancementQueueItem>> = _queue.asStateFlow()

    private val _isAutoProcessEnabled = MutableStateFlow(
        sharedPrefs.getBoolean(KEY_AUTO_PROCESS, false)
    )
    val isAutoProcessEnabled: StateFlow<Boolean> = _isAutoProcessEnabled.asStateFlow()

    private val _selectedDefaultPreset = MutableStateFlow(EnhancementPreset.AUTO)
    val selectedDefaultPreset: StateFlow<EnhancementPreset> = _selectedDefaultPreset.asStateFlow()

    // Persistent set of processed photo signatures & URIs to prevent re-processing loops
    private val processedSignatures = java.util.Collections.synchronizedSet(
        sharedPrefs.getStringSet(KEY_PROCESSED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
    )

    private val processingMutex = Mutex()
    private var isWorkerRunning = false
    private var activeJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var activeItemId: String? = null

    fun setAutoProcessEnabled(enabled: Boolean) {
        _isAutoProcessEnabled.value = enabled
        sharedPrefs.edit().putBoolean(KEY_AUTO_PROCESS, enabled).apply()
    }

    fun setDefaultPreset(preset: EnhancementPreset) {
        _selectedDefaultPreset.value = EnhancementPreset.AUTO
        sharedPrefs.edit().putString(KEY_DEFAULT_PRESET, EnhancementPreset.AUTO.name).apply()
    }

    /**
     * Records a photo as processed so it will never be auto-enhanced again.
     */
    fun markPhotoAsProcessed(photo: CameraPhoto) {
        val sig = photo.signature
        val uriStr = photo.uri.toString()
        val name = photo.displayName
        processedSignatures.add(sig)
        processedSignatures.add(uriStr)
        processedSignatures.add(name)
        repository.markUriAsEnhanced(photo.uri, photo.displayName)

        // Save asynchronously to SharedPreferences
        sharedPrefs.edit().putStringSet(KEY_PROCESSED_SIGNATURES, HashSet(processedSignatures)).apply()
    }

    /**
     * Checks whether this photo is already enhanced, currently in queue, or was previously processed.
     */
    fun hasPhotoBeenProcessedOrQueued(photo: CameraPhoto): Boolean {
        if (photo.isEnhancedImage) return true
        if (processedSignatures.contains(photo.signature) ||
            processedSignatures.contains(photo.uri.toString()) ||
            processedSignatures.contains(photo.displayName)
        ) {
            return true
        }

        // Check active queue items
        val inQueue = _queue.value.any { item ->
            item.photo.id == photo.id ||
            item.photo.uri == photo.uri ||
            item.photo.displayName == photo.displayName
        }
        return inQueue
    }

    /**
     * Checks database and in-memory cache to see if photo has been processed.
     */
    suspend fun isPhotoAlreadyProcessedOrEnhanced(photo: CameraPhoto): Boolean {
        if (hasPhotoBeenProcessedOrQueued(photo)) return true

        try {
            val dbCount = database.enhancedPhotoDao().isUriAlreadyEnhanced(photo.uri.toString(), photo.uri.toString())
            if (dbCount > 0) {
                markPhotoAsProcessed(photo)
                return true
            }
            val nameCount = database.enhancedPhotoDao().isNameAlreadyEnhanced(photo.displayName)
            if (nameCount > 0) {
                markPhotoAsProcessed(photo)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying database for photo enhancement status", e)
        }

        return false
    }

    /**
     * Adds a camera photo into the AI Enhancement Queue.
     * Guaranteed: never enqueues an already enhanced image or a duplicate.
     */
    fun enqueue(photo: CameraPhoto, preset: EnhancementPreset = EnhancementPreset.AUTO, force: Boolean = false): String {
        if (photo.isEnhancedImage) {
            Log.w(TAG, "Skipping enqueue: Photo is an enhanced image (${photo.displayName})")
            return ""
        }

        if (!force && hasPhotoBeenProcessedOrQueued(photo)) {
            Log.d(TAG, "Photo is already in queue or processed (${photo.displayName}), skipping duplicate.")
            return ""
        }

        val id = UUID.randomUUID().toString()
        val item = EnhancementQueueItem(
            id = id,
            photo = photo,
            preset = EnhancementPreset.AUTO,
            status = QueueItemStatus.Pending,
            progress = 0f
        )
        _queue.value = _queue.value + item
        PixenseAnalytics.logEvent("queue_item_enqueued", mapOf("photo" to photo.displayName, "queue_size" to _queue.value.size))
        triggerQueueProcessing()
        return id
    }

    fun retry(itemId: String) {
        PixenseAnalytics.logEvent("queue_item_retried", mapOf("item_id" to itemId))
        _queue.value = _queue.value.map { item ->
            if (item.id == itemId) {
                item.copy(status = QueueItemStatus.Pending, progress = 0f)
            } else {
                item
            }
        }
        triggerQueueProcessing()
    }

    fun stop(itemId: String) {
        PixenseAnalytics.logEvent("queue_item_stopped", mapOf("item_id" to itemId))
        if (activeItemId == itemId) {
            activeJob?.cancel()
            activeJob = null
            activeItemId = null
        }
        updateItemStatus(itemId, QueueItemStatus.Stopped("Processing stopped by user"), 0f)
        triggerQueueProcessing()
    }

    fun cancel(itemId: String) {
        PixenseAnalytics.logEvent("queue_item_cancelled", mapOf("item_id" to itemId))
        if (activeItemId == itemId) {
            activeJob?.cancel()
            activeJob = null
            activeItemId = null
        }
        _queue.value = _queue.value.filterNot { it.id == itemId }
        triggerQueueProcessing()
    }

    fun clearCompleted() {
        _queue.value = _queue.value.filter { it.status !is QueueItemStatus.Completed }
    }

    fun clearAll() {
        _queue.value = _queue.value.filter { it.status is QueueItemStatus.InProgress }
    }

    private fun triggerQueueProcessing() {
        scope.launch {
            processingMutex.withLock {
                if (isWorkerRunning) return@withLock
                isWorkerRunning = true
            }

            try {
                processNextInQueue()
            } finally {
                processingMutex.withLock {
                    isWorkerRunning = false
                }
            }
        }
    }

    private suspend fun processNextInQueue() {
        while (true) {
            val pendingItem = _queue.value.firstOrNull { it.status is QueueItemStatus.Pending } ?: break

            try {
                kotlinx.coroutines.coroutineScope {
                    val job = launch {
                        activeItemId = pendingItem.id
                        processSingleItem(pendingItem)
                    }
                    activeJob = job
                    job.join()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Queue item ${pendingItem.id} was stopped or cancelled by user.")
                updateItemStatus(pendingItem.id, QueueItemStatus.Stopped("Processing stopped by user"), 0f)
            } finally {
                if (activeItemId == pendingItem.id) {
                    activeItemId = null
                    activeJob = null
                }
            }

            // Polite throttle delay between consecutive queue items to prevent burst rate-limits
            delay(1200L)
        }
    }

    private fun updateItemEntitlement(itemId: String, entitlement: EntitlementType) {
        _queue.value = _queue.value.map { item ->
            if (item.id == itemId) {
                item.copy(entitlementType = entitlement)
            } else {
                item
            }
        }
    }

    private suspend fun processSingleItem(pendingItem: EnhancementQueueItem) {
        updateItemStatus(pendingItem.id, QueueItemStatus.InProgress("Connecting to Gemini AI…"), 0.1f)

        val quotaManager = EnhancementQuotaManager.getInstance(context)
        val consumed = quotaManager.consumeEntitlement()
        if (consumed == null) {
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Stopped("Open Pixense to watch an ad and enhance."),
                0f
            )
            CameraCaptureService.showQuotaLimitNotification(context, pendingItem.photo.displayName)
            return
        }

        updateItemEntitlement(pendingItem.id, consumed)
        when (consumed) {
            EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_started")
            EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_started")
        }

        try {
            // 1. Load original bitmap
            updateItemStatus(pendingItem.id, QueueItemStatus.InProgress("Loading photo from storage…"), 0.2f)
            val originalBitmap = repository.loadBitmap(pendingItem.photo.uri)
                ?: throw GeminiApiException.GeneralError("Failed to decode camera photo file.")

            // 2. Gemini 4K Vision Remastering & Scene/Text Detection
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.InProgress("Gemini 4K AI analyzing scene & remastering photo…"),
                0.4f
            )
            val enhancementResult = GeminiVisionServiceNew.enhanceAndAnalyze(
                context = context,
                bitmap = originalBitmap,
                preset = pendingItem.preset,
                cacheKey = "${pendingItem.photo.uri}_${pendingItem.photo.sizeBytes}",
                onStageProgress = { progressText ->
                    updateItemStatus(pendingItem.id, QueueItemStatus.InProgress(progressText), 0.65f)
                }
            )
            val restoredBitmap: Bitmap = enhancementResult.enhancedBitmap
            val analysis: AiPhotoAnalysis = enhancementResult.analysis

            // 3. Aspect ratio & native resolution guarantee
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.InProgress("Finalizing and saving to Gallery…"),
                0.9f
            )
            val finalBitmap = ImageProcessingEngine.postProcessNanoBananaImage(
                aiBitmap = restoredBitmap,
                originalBitmap = originalBitmap
            )

            // 5. Save to MediaStore (Pictures/Camera_AI)
            val savedUri = repository.saveEnhancedBitmap(finalBitmap, pendingItem.photo.displayName)
                ?: throw GeminiApiException.GeneralError("Failed to save enhanced image to device Gallery.")

            // 6. Save to Room database for AI Gallery screen
            val entity = EnhancedPhotoEntity(
                originalUri = pendingItem.photo.uri,
                enhancedUri = savedUri,
                originalDisplayName = pendingItem.photo.displayName,
                enhancedDisplayName = "AI_Enhanced_${pendingItem.photo.displayName}",
                preset = pendingItem.preset,
                timestamp = System.currentTimeMillis(),
                sceneType = analysis.sceneType,
                lightingScore = analysis.lightingScore,
                sharpnessScore = analysis.sharpnessScore,
                dynamicRange = analysis.dynamicRange,
                aiInsight = analysis.aiInsight,
                resolution = analysis.resolutionUpscale,
                width = finalBitmap.width,
                height = finalBitmap.height
            )
            val entityId = database.enhancedPhotoDao().insert(entity)

            // Mark original photo and saved enhanced photo as processed
            markPhotoAsProcessed(pendingItem.photo)
            repository.markUriAsEnhanced(savedUri, entity.enhancedDisplayName)

            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Completed(enhancedUri = savedUri, entityId = entityId),
                1.0f
            )
            Log.d(TAG, "Queue item ${pendingItem.id} completed successfully and saved to Gallery: $savedUri")

            // Log completion event
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_completed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_completed")
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            if (consumed == EntitlementType.REWARDED) {
                quotaManager.refundRewardedEnhancement()
            }
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
            }
            throw e
        } catch (e: GeminiApiException.NoInternet) {
            Log.e(TAG, "Queue item failed: No internet", e)
            PixenseAnalytics.recordException(e, "Queue processing failed: No Internet")
            if (consumed == EntitlementType.REWARDED) {
                quotaManager.refundRewardedEnhancement()
            }
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
            }
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Failed("No internet connection. Tap to retry once connected."),
                0f
            )
        } catch (e: GeminiApiException.MissingApiKey) {
            Log.e(TAG, "Queue item failed: Missing API Key", e)
            PixenseAnalytics.recordException(e, "Queue processing failed: Missing API Key")
            if (consumed == EntitlementType.REWARDED) {
                quotaManager.refundRewardedEnhancement()
            }
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
            }
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Failed("Gemini API key is not configured in application Secrets."),
                0f
            )
        } catch (e: GeminiApiException.QuotaExceeded) {
            Log.e(TAG, "Queue item failed: Quota exceeded", e)
            PixenseAnalytics.recordException(e, "Queue processing failed: Quota Exceeded")
            if (consumed == EntitlementType.REWARDED) {
                quotaManager.refundRewardedEnhancement()
            }
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
            }
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Failed("API rate limit reached. Tap Retry to process."),
                0f
            )
            delay(2000L)
        } catch (e: GeminiApiException) {
            Log.e(TAG, "Queue item failed with GeminiApiException", e)
            PixenseAnalytics.recordException(e, "Queue processing failed: GeminiApiException")
            if (consumed == EntitlementType.REWARDED) {
                quotaManager.refundRewardedEnhancement()
            }
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
            }
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Failed(e.message ?: "Gemini AI enhancement failed."),
                0f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Queue item failed with unexpected exception", e)
            PixenseAnalytics.recordException(e, "Queue processing failed: Unexpected Exception")
            if (consumed == EntitlementType.REWARDED) {
                quotaManager.refundRewardedEnhancement()
            }
            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
            }
            updateItemStatus(
                pendingItem.id,
                QueueItemStatus.Failed("Failed: ${e.localizedMessage ?: "Unknown error"}"),
                0f
            )
        }
    }

    private fun updateItemStatus(itemId: String, status: QueueItemStatus, progress: Float) {
        _queue.value = _queue.value.map { item ->
            if (item.id == itemId) {
                item.copy(
                    status = status,
                    progress = progress,
                    completedAt = if (status is QueueItemStatus.Completed) System.currentTimeMillis() else item.completedAt
                )
            } else {
                item
            }
        }
    }

    companion object {
        private const val TAG = "AiQueueManager"
        private const val KEY_AUTO_PROCESS = "key_auto_process_enabled"
        private const val KEY_DEFAULT_PRESET = "key_default_enhancement_preset"
        private const val KEY_PROCESSED_SIGNATURES = "key_processed_photo_signatures"

        @Volatile
        private var instance: AiQueueManager? = null

        fun getInstance(context: Context): AiQueueManager {
            return instance ?: synchronized(this) {
                instance ?: AiQueueManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
