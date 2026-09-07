package com.pixense.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.pixense.app.data.ai.GeminiApiException
import com.pixense.app.data.ai.GeminiVisionService
import com.pixense.app.data.db.AppDatabase
import com.pixense.app.data.db.EnhancedPhotoEntity
import com.pixense.app.data.image.ImageProcessingEngine
import com.pixense.app.data.model.AiPhotoAnalysis
import com.pixense.app.data.analytics.PixenseAnalytics
import com.pixense.app.data.model.CameraPhoto
import com.pixense.app.data.model.EnhancementPreset
import com.pixense.app.data.model.EnhancementQueueItem
import com.pixense.app.data.model.EnhancementUiState
import com.pixense.app.data.model.ThemeMode
import com.pixense.app.data.paging.DcimPagingSource
import com.pixense.app.data.repository.AiQueueManager
import com.pixense.app.data.repository.CameraCaptureRepository
import com.pixense.app.data.repository.EnhancementQuotaManager
import com.pixense.app.data.repository.EntitlementType
import com.pixense.app.data.repository.QuotaState
import com.pixense.app.data.repository.RewardedAdManager
import com.pixense.app.service.CameraCaptureService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StudioTab {
    STUDIO,
    GALLERY,
    QUEUE,
    SETTINGS
}

class CameraAiViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pixense_prefs", Context.MODE_PRIVATE)

    private val repository = CameraCaptureRepository.getInstance(application)
    private val queueManager = AiQueueManager.getInstance(application)
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.enhancedPhotoDao()

    private val quotaManager = EnhancementQuotaManager.getInstance(application)
    private val rewardedAdManager = RewardedAdManager.getInstance(application)

    // Quota State
    val quotaState: StateFlow<QuotaState> = quotaManager.quotaState

    // State flow for pending ad prompt target
    private val _pendingAdEnhancementPhoto = MutableStateFlow<CameraPhoto?>(null)
    val pendingAdEnhancementPhoto: StateFlow<CameraPhoto?> = _pendingAdEnhancementPhoto.asStateFlow()

    // State flow for whether rewarded ad is loaded & loading
    val isAdLoaded: StateFlow<Boolean> = rewardedAdManager.isAdLoaded
    val isAdLoading: StateFlow<Boolean> = rewardedAdManager.isLoading

    fun refreshAdStatus() {
        rewardedAdManager.loadAd()
    }

    fun dismissAdPrompt() {
        _pendingAdEnhancementPhoto.value = null
    }

    fun showRewardedAd(activity: android.app.Activity) {
        val photo = _pendingAdEnhancementPhoto.value ?: return
        PixenseAnalytics.logEvent("rewarded_ad_requested")
        rewardedAdManager.showAd(
            activity = activity,
            onRewardEarned = {
                quotaManager.addPendingRewardedEnhancement()
                queueManager.enqueue(photo, EnhancementPreset.AUTO, force = true)
                _saveStatusMessage.value = "Added \"${photo.displayName}\" to Gemini AI Queue!"
                _pendingAdEnhancementPhoto.value = null
            },
            onFailure = { errorMsg ->
                _saveStatusMessage.value = errorMsg
            }
        )
    }

    // Theme Mode Selection (Default: SYSTEM)
    private val _themeMode = MutableStateFlow(
        ThemeMode.fromString(prefs.getString("app_theme_mode", ThemeMode.SYSTEM.name))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("app_theme_mode", mode.name).apply()
        PixenseAnalytics.logEvent("theme_mode_changed", mapOf("mode" to mode.name))
        PixenseAnalytics.setUserProperty("theme_mode", mode.name)
        PixenseAnalytics.setCustomKey("theme_mode", mode.name)
    }

    val latestPhoto: StateFlow<CameraPhoto?> = repository.latestPhoto
    val isServiceActive: StateFlow<Boolean> = repository.isServiceActive

    // Auto-Process Toggle & Queue Management
    val isAutoProcessEnabled: StateFlow<Boolean> = queueManager.isAutoProcessEnabled
    val queueItems: StateFlow<List<EnhancementQueueItem>> = queueManager.queue

    // Enhanced Photos Gallery
    val enhancedPhotos: StateFlow<List<EnhancedPhotoEntity>> = dao.getAllEnhancedPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Navigation Tab
    private val _currentTab = MutableStateFlow(StudioTab.STUDIO)
    val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

    // Preset Selection (Unified Auto Intelligent Enhancement)
    private val _selectedPreset = MutableStateFlow(EnhancementPreset.AUTO)
    val selectedPreset: StateFlow<EnhancementPreset> = _selectedPreset.asStateFlow()

    // Studio Enhancement UI State
    private val _enhancementState = MutableStateFlow<EnhancementUiState>(EnhancementUiState.Idle)
    val enhancementState: StateFlow<EnhancementUiState> = _enhancementState.asStateFlow()

    private val _isShowingOriginal = MutableStateFlow(false)
    val isShowingOriginal: StateFlow<Boolean> = _isShowingOriginal.asStateFlow()

    private val _saveStatusMessage = MutableStateFlow<String?>(null)
    val saveStatusMessage: StateFlow<String?> = _saveStatusMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // In-App CameraX Screen Overlay State (Launcher screen on app start)
    private val _isCameraOpen = MutableStateFlow(true)
    val isCameraOpen: StateFlow<Boolean> = _isCameraOpen.asStateFlow()

    // Selected Gallery Photo for Detail & Re-Enhance Inspection
    private val _selectedGalleryPhoto = MutableStateFlow<EnhancedPhotoEntity?>(null)
    val selectedGalleryPhoto: StateFlow<EnhancedPhotoEntity?> = _selectedGalleryPhoto.asStateFlow()

    // Paging 3 Flow for DCIM Gallery
    private val _refreshPagingTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dcimPagingFlow: Flow<PagingData<CameraPhoto>> = _refreshPagingTrigger
        .flatMapLatest {
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = true,
                    prefetchDistance = 10,
                    initialLoadSize = 20
                ),
                pagingSourceFactory = { DcimPagingSource(repository) }
            ).flow
        }
        .cachedIn(viewModelScope)

    // DCIM Photos for Studio Grid
    private val _dcimPhotos = MutableStateFlow<List<CameraPhoto>>(emptyList())
    val dcimPhotos: StateFlow<List<CameraPhoto>> = _dcimPhotos.asStateFlow()

    // Infinite Scroll Pagination for DCIM Photos
    val pageSize: Int = 20
    private val _visibleDcimCount = MutableStateFlow(pageSize)
    val visibleDcimCount: StateFlow<Int> = _visibleDcimCount.asStateFlow()

    // Currently Selected Photo for Fullscreen Preview & Enhance Modal
    private val _previewPhoto = MutableStateFlow<CameraPhoto?>(null)
    val previewPhoto: StateFlow<CameraPhoto?> = _previewPhoto.asStateFlow()

    init {
        refreshLatestPhoto()
        loadDcimPhotos()
    }

    fun refreshDcimPaging() {
        _refreshPagingTrigger.value += 1
        loadDcimPhotos()
    }

    fun openCamera() {
        _isCameraOpen.value = true
        PixenseAnalytics.logEvent("camera_opened")
        PixenseAnalytics.logScreenView("CameraScreen")
        PixenseAnalytics.setCustomKey("is_camera_open", true)
    }

    fun closeCamera() {
        _isCameraOpen.value = false
        PixenseAnalytics.logEvent("camera_closed")
        PixenseAnalytics.logScreenView("StudioScreen")
        PixenseAnalytics.setCustomKey("is_camera_open", false)
    }

    fun onPhotoCaptured(uri: Uri) {
        // Do not close camera on captured photo, keep user in camera session
        PixenseAnalytics.logEvent("camera_photo_captured", mapOf("auto_process" to isAutoProcessEnabled.value))
        viewModelScope.launch {
            val photo = repository.queryPhotoByUri(uri)
            if (photo != null) {
                repository.setLatestPhoto(photo)
                refreshDcimPaging()

                if (isAutoProcessEnabled.value) {
                    queueManager.enqueue(photo, _selectedPreset.value, force = true)
                    _saveStatusMessage.value = "Photo captured! Gemini AI Remastering enqueued."
                } else {
                    _saveStatusMessage.value = "Photo captured & saved to DCIM!"
                }
            }
        }
    }

    fun openPhotoPreview(photo: CameraPhoto) {
        _previewPhoto.value = photo
        PixenseAnalytics.logEvent("photo_preview_opened", mapOf("display_name" to photo.displayName))
    }

    fun closePhotoPreview() {
        _previewPhoto.value = null
        PixenseAnalytics.logEvent("photo_preview_closed")
    }

    fun loadMoreDcimPhotos() {
        val total = _dcimPhotos.value.size
        if (_visibleDcimCount.value < total) {
            _visibleDcimCount.value = (_visibleDcimCount.value + pageSize).coerceAtMost(total)
        }
    }

    fun loadDcimPhotos() {
        viewModelScope.launch {
            val photos = repository.queryDcimPhotosPaged(offset = 0, limit = pageSize)
            _dcimPhotos.value = photos
            if (_visibleDcimCount.value < pageSize) {
                _visibleDcimCount.value = pageSize
            }
        }
    }

    fun enhanceSpecificPhoto(photo: CameraPhoto) {
        repository.setLatestPhoto(photo)
        PixenseAnalytics.logEvent("enhance_specific_photo_clicked", mapOf("display_name" to photo.displayName))
        if (quotaManager.hasAvailableEntitlement()) {
            queueManager.enqueue(photo, EnhancementPreset.AUTO, force = true)
            _saveStatusMessage.value = "Added \"${photo.displayName}\" to Gemini AI Queue!"
        } else {
            _pendingAdEnhancementPhoto.value = photo
        }
    }

    fun selectTab(tab: StudioTab) {
        _currentTab.value = tab
        PixenseAnalytics.logEvent("studio_tab_selected", mapOf("tab" to tab.name))
        PixenseAnalytics.logScreenView("StudioTab_${tab.name}")
        PixenseAnalytics.setCustomKey("current_studio_tab", tab.name)
    }

    fun setAutoProcessEnabled(enabled: Boolean) {
        queueManager.setAutoProcessEnabled(enabled)
        PixenseAnalytics.logEvent("auto_process_toggled", mapOf("enabled" to enabled))
        PixenseAnalytics.setUserProperty("auto_process_enabled", enabled.toString())
        PixenseAnalytics.setCustomKey("auto_process_enabled", enabled)
        if (enabled) {
            CameraCaptureService.start(getApplication())
            repository.setServiceActive(true)
        } else {
            CameraCaptureService.stop(getApplication())
            repository.setServiceActive(false)
        }
    }

    fun enqueueCurrentPhoto(preset: EnhancementPreset = EnhancementPreset.AUTO) {
        val photo = latestPhoto.value ?: return
        if (quotaManager.hasAvailableEntitlement()) {
            queueManager.enqueue(photo, EnhancementPreset.AUTO, force = true)
            _saveStatusMessage.value = "Added \"${photo.displayName}\" to AI processing queue."
        } else {
            _pendingAdEnhancementPhoto.value = photo
        }
    }

    fun retryQueueItem(itemId: String) {
        queueManager.retry(itemId)
        _saveStatusMessage.value = "Resuming AI enhancement queue."
    }

    fun stopQueueItem(itemId: String) {
        queueManager.stop(itemId)
        _saveStatusMessage.value = "Stopped AI enhancement for selected photo."
    }

    fun cancelQueueItem(itemId: String) {
        queueManager.cancel(itemId)
    }

    fun clearCompletedQueue() {
        queueManager.clearCompleted()
    }

    fun refreshLatestPhoto() {
        viewModelScope.launch {
            val photo = repository.queryLatestCameraPhoto()
            if (photo != null && photo.isNativeCameraPath && !photo.isEnhancedImage) {
                repository.setLatestPhoto(photo)
            }
        }
    }

    fun selectPreset(preset: EnhancementPreset = EnhancementPreset.AUTO) {
        _selectedPreset.value = EnhancementPreset.AUTO
        queueManager.setDefaultPreset(EnhancementPreset.AUTO)
    }

    fun enhancePhoto(preset: EnhancementPreset = EnhancementPreset.AUTO) {
        val photo = latestPhoto.value ?: return
        val context = getApplication<Application>().applicationContext

        // Check if already processing to prevent double tap
        if (_enhancementState.value is EnhancementUiState.Processing) return

        if (!quotaManager.hasAvailableEntitlement()) {
            _pendingAdEnhancementPhoto.value = photo
            return
        }

        viewModelScope.launch {
            _enhancementState.value = EnhancementUiState.Processing("Gemini LLM analyzing scene (portrait, low light, food, texture)…")

            val consumed = quotaManager.consumeEntitlement()
            if (consumed == null) {
                _enhancementState.value = EnhancementUiState.Idle
                _saveStatusMessage.value = "Please watch an ad to enhance."
                return@launch
            }

            when (consumed) {
                EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_started")
                EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_started")
            }

            val originalBitmap = repository.loadBitmap(photo.uri)
            if (originalBitmap == null) {
                _enhancementState.value = EnhancementUiState.Error("Failed to decode camera photo file from storage.")
                if (consumed == EntitlementType.REWARDED) {
                    quotaManager.refundRewardedEnhancement()
                }
                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
                }
                return@launch
            }

            try {
                // 2-Stage Gemini Pipeline:
                // Stage 1: Gemini LLM detects scene & characteristics
                // Stage 2: Gemini Image model remasters tailored specifically to the detected scene
                val enhancementResult = GeminiVisionService.enhanceAndAnalyze(
                    context = context,
                    bitmap = originalBitmap,
                    preset = EnhancementPreset.AUTO,
                    cacheKey = "${photo.uri}_${photo.sizeBytes}",
                    onStageProgress = { progressText ->
                        _enhancementState.value = EnhancementUiState.Processing(progressText)
                    }
                )

                val nanoBananaBitmap: Bitmap = enhancementResult.enhancedBitmap
                val analysis: AiPhotoAnalysis = enhancementResult.analysis

                // Ensure aspect ratio and native dimensions match original exactly
                val finalEnhancedBitmap = ImageProcessingEngine.postProcessNanoBananaImage(
                    aiBitmap = nanoBananaBitmap,
                    originalBitmap = originalBitmap
                )

                _enhancementState.value = EnhancementUiState.Success(
                    originalBitmap = originalBitmap,
                    enhancedBitmap = finalEnhancedBitmap,
                    analysis = analysis,
                    preset = EnhancementPreset.AUTO
                )

                // Auto save to Gallery & Database
                val savedUri = repository.saveEnhancedBitmap(finalEnhancedBitmap, photo.displayName)
                if (savedUri != null) {
                    val entity = EnhancedPhotoEntity(
                        originalUri = photo.uri,
                        enhancedUri = savedUri,
                        originalDisplayName = photo.displayName,
                        enhancedDisplayName = "AI_Enhanced_${photo.displayName}",
                        preset = EnhancementPreset.AUTO,
                        timestamp = System.currentTimeMillis(),
                        sceneType = analysis.sceneType,
                        lightingScore = analysis.lightingScore,
                        sharpnessScore = analysis.sharpnessScore,
                        dynamicRange = analysis.dynamicRange,
                        aiInsight = analysis.aiInsight,
                        resolution = analysis.resolutionUpscale,
                        width = finalEnhancedBitmap.width,
                        height = finalEnhancedBitmap.height
                    )
                    dao.insert(entity)
                    queueManager.markPhotoAsProcessed(photo)
                    repository.markUriAsEnhanced(savedUri, entity.enhancedDisplayName)
                    _saveStatusMessage.value = "Enhanced & saved to AI Gallery (${analysis.sceneType})!"
                }

                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_completed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_completed")
                }

            } catch (e: GeminiApiException.NoInternet) {
                PixenseAnalytics.recordException(e, "Gemini enhancement failed: No Internet")
                if (consumed == EntitlementType.REWARDED) {
                    quotaManager.refundRewardedEnhancement()
                }
                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
                }
                _enhancementState.value = EnhancementUiState.Error(
                    e.message ?: "No internet connection. Please check your network to connect to Gemini AI."
                )
            } catch (e: GeminiApiException.MissingApiKey) {
                PixenseAnalytics.recordException(e, "Gemini enhancement failed: Missing API Key")
                if (consumed == EntitlementType.REWARDED) {
                    quotaManager.refundRewardedEnhancement()
                }
                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
                }
                _enhancementState.value = EnhancementUiState.Error(
                    "Gemini API key is not configured. Please add GEMINI_API_KEY to your application secrets."
                )
            } catch (e: GeminiApiException.QuotaExceeded) {
                PixenseAnalytics.recordException(e, "Gemini enhancement failed: Quota Exceeded")
                if (consumed == EntitlementType.REWARDED) {
                    quotaManager.refundRewardedEnhancement()
                }
                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
                }
                _enhancementState.value = EnhancementUiState.Error(
                    "Gemini API quota exceeded or rate limited. Please wait a few seconds and try again."
                )
            } catch (e: GeminiApiException) {
                PixenseAnalytics.recordException(e, "Gemini enhancement failed: GeminiApiException")
                if (consumed == EntitlementType.REWARDED) {
                    quotaManager.refundRewardedEnhancement()
                }
                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
                }
                _enhancementState.value = EnhancementUiState.Error(
                    e.message ?: "Gemini AI enhancement failed. Please retry."
                )
            } catch (e: Exception) {
                PixenseAnalytics.recordException(e, "Gemini enhancement failed: Unexpected Exception")
                if (consumed == EntitlementType.REWARDED) {
                    quotaManager.refundRewardedEnhancement()
                }
                when (consumed) {
                    EntitlementType.FREE -> PixenseAnalytics.logEvent("free_enhancement_failed")
                    EntitlementType.REWARDED -> PixenseAnalytics.logEvent("rewarded_enhancement_failed")
                }
                _enhancementState.value = EnhancementUiState.Error(
                    "Enhancement failed: ${e.localizedMessage ?: "Unexpected error"}"
                )
            }
        }
    }

    fun toggleShowOriginal(showOriginal: Boolean) {
        _isShowingOriginal.value = showOriginal
        PixenseAnalytics.logEvent("photo_compare_toggled", mapOf("show_original" to showOriginal))
    }

    fun resetEnhancement() {
        _enhancementState.value = EnhancementUiState.Idle
        _isShowingOriginal.value = false
    }

    fun clearError() {
        _enhancementState.value = EnhancementUiState.Idle
    }

    fun saveEnhancedPhoto() {
        val currentState = _enhancementState.value
        if (currentState !is EnhancementUiState.Success) return
        val currentPhoto = latestPhoto.value ?: return

        viewModelScope.launch {
            _isSaving.value = true
            val savedUri = repository.saveEnhancedBitmap(currentState.enhancedBitmap, currentPhoto.displayName)
            _isSaving.value = false
            if (savedUri != null) {
                val entity = EnhancedPhotoEntity(
                    originalUri = currentPhoto.uri,
                    enhancedUri = savedUri,
                    originalDisplayName = currentPhoto.displayName,
                    enhancedDisplayName = "AI_Enhanced_${currentPhoto.displayName}",
                    preset = currentState.preset,
                    timestamp = System.currentTimeMillis(),
                    sceneType = currentState.analysis.sceneType,
                    lightingScore = currentState.analysis.lightingScore,
                    sharpnessScore = currentState.analysis.sharpnessScore,
                    dynamicRange = currentState.analysis.dynamicRange,
                    aiInsight = currentState.analysis.aiInsight,
                    resolution = currentState.analysis.resolutionUpscale,
                    width = currentState.enhancedBitmap.width,
                    height = currentState.enhancedBitmap.height
                )
                dao.insert(entity)
                queueManager.markPhotoAsProcessed(currentPhoto)
                repository.markUriAsEnhanced(savedUri, entity.enhancedDisplayName)
                _saveStatusMessage.value = "Enhanced photo saved to Pictures/Camera_AI and AI Gallery!"
            } else {
                _saveStatusMessage.value = "Failed to save photo to storage."
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatusMessage.value = null
    }

    fun loadSamplePhoto() {
        viewModelScope.launch {
            _enhancementState.value = EnhancementUiState.Idle
            val sample = repository.createSampleCameraPhoto()
            refreshDcimPaging()
            if (isAutoProcessEnabled.value) {
                queueManager.enqueue(sample, _selectedPreset.value, force = true)
            }
        }
    }

    fun toggleBackgroundService() {
        val current = isServiceActive.value
        if (current) {
            CameraCaptureService.stop(getApplication())
            repository.setServiceActive(false)
        } else {
            CameraCaptureService.start(getApplication())
            repository.setServiceActive(true)
        }
    }

    fun launchDeviceCamera(): Intent {
        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return cameraIntent
    }

    // AI Gallery Interactions
    fun selectGalleryPhoto(photo: EnhancedPhotoEntity?) {
        _selectedGalleryPhoto.value = photo
    }

    fun deleteEnhancedPhoto(photo: EnhancedPhotoEntity) {
        viewModelScope.launch {
            PixenseAnalytics.logEvent("photo_deleted", mapOf("type" to "enhanced", "scene" to photo.sceneType))
            try {
                getApplication<Application>().contentResolver.delete(photo.enhancedUri, null, null)
            } catch (e: Exception) {
                Log.e("CameraAiViewModel", "Failed to delete enhanced photo file: ${photo.enhancedUri}", e)
                PixenseAnalytics.recordException(e, "Failed to delete enhanced photo contentResolver")
            }
            try {
                if (photo.enhancedUri.scheme == "file" || photo.enhancedUri.path != null) {
                    val f = java.io.File(photo.enhancedUri.path ?: "")
                    if (f.exists()) f.delete()
                }
            } catch (_: Exception) {}
            dao.delete(photo)
            if (_selectedGalleryPhoto.value?.id == photo.id) {
                _selectedGalleryPhoto.value = null
            }
            _saveStatusMessage.value = "Photo deleted from AI Gallery."
        }
    }

    fun deleteDcimPhoto(photo: CameraPhoto) {
        viewModelScope.launch {
            PixenseAnalytics.logEvent("photo_deleted", mapOf("type" to "dcim", "name" to photo.displayName))
            val success = repository.deletePhoto(photo)
            if (_previewPhoto.value?.id == photo.id || _previewPhoto.value?.uri == photo.uri) {
                _previewPhoto.value = null
            }
            if (latestPhoto.value?.id == photo.id || latestPhoto.value?.uri == photo.uri) {
                val newLatest = repository.queryLatestCameraPhoto()
                if (newLatest != null) {
                    repository.setLatestPhoto(newLatest)
                } else {
                    repository.clearLatestPhoto()
                }
                if (_enhancementState.value is EnhancementUiState.Success) {
                    _enhancementState.value = EnhancementUiState.Idle
                }
            }
            refreshDcimPaging()
            loadDcimPhotos()
            _saveStatusMessage.value = if (success) "Photo deleted from DCIM." else "Photo removed."
        }
    }

    fun reEnhanceGalleryPhoto(photo: EnhancedPhotoEntity, newPreset: EnhancementPreset) {
        // Create a CameraPhoto from originalUri and enqueue into the queue manager with the new preset
        val cameraPhoto = CameraPhoto(
            id = photo.id,
            uri = photo.originalUri,
            displayName = photo.originalDisplayName,
            dateTaken = photo.timestamp,
            sizeBytes = 0L,
            width = photo.width,
            height = photo.height,
            mimeType = "image/jpeg"
        )
        if (quotaManager.hasAvailableEntitlement()) {
            queueManager.enqueue(cameraPhoto, newPreset, force = true)
            _saveStatusMessage.value = "Re-enhancing \"${photo.originalDisplayName}\" in ${newPreset.title} style…"
            _selectedGalleryPhoto.value = null
            _currentTab.value = StudioTab.QUEUE
        } else {
            _pendingAdEnhancementPhoto.value = cameraPhoto
        }
    }

    fun setLatestPhotoFromGallery(photo: EnhancedPhotoEntity) {
        val cameraPhoto = CameraPhoto(
            id = photo.id,
            uri = photo.originalUri,
            displayName = photo.originalDisplayName,
            dateTaken = photo.timestamp,
            sizeBytes = 0L,
            width = photo.width,
            height = photo.height,
            mimeType = "image/jpeg"
        )
        repository.setLatestPhoto(cameraPhoto)
        _selectedPreset.value = photo.preset
        _enhancementState.value = EnhancementUiState.Idle
        _currentTab.value = StudioTab.STUDIO
    }
}
