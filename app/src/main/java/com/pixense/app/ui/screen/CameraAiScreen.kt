package com.pixense.app.ui.screen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.pixense.app.ui.view.OptimizedThumbnailImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pixense.app.R
import com.pixense.app.data.db.EnhancedPhotoEntity
import com.pixense.app.data.model.AiPhotoAnalysis
import com.pixense.app.data.model.AiPhotoOperation
import com.pixense.app.data.model.CameraPhoto
import com.pixense.app.data.model.EnhancementQueueItem
import com.pixense.app.data.model.EnhancementUiState
import com.pixense.app.data.model.QueueItemStatus
import com.pixense.app.data.model.ThemeMode
import com.pixense.app.service.CameraCaptureService
import com.pixense.app.ui.theme.BentoTheme
import com.pixense.app.ui.viewmodel.CameraAiViewModel
import com.pixense.app.ui.viewmodel.StudioTab
import com.pixense.app.ui.screen.AiGalleryScreen
import com.pixense.app.ui.view.ZoomableAsyncImage
import com.pixense.app.data.analytics.PixenseAnalytics
import com.pixense.app.util.PermissionUtils
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAiScreen(
    viewModel: CameraAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isCameraOpen by viewModel.isCameraOpen.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val latestPhoto by viewModel.latestPhoto.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val isAutoProcessEnabled by viewModel.isAutoProcessEnabled.collectAsStateWithLifecycle()
    val queueItems by viewModel.queueItems.collectAsStateWithLifecycle()
    val enhancedPhotos by viewModel.enhancedPhotos.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val enhancementState by viewModel.enhancementState.collectAsStateWithLifecycle()
    val isShowingOriginal by viewModel.isShowingOriginal.collectAsStateWithLifecycle()
    val saveStatusMessage by viewModel.saveStatusMessage.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val previewPhoto by viewModel.previewPhoto.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val selectedOperation by viewModel.selectedOperation.collectAsStateWithLifecycle()

    val quotaState by viewModel.quotaState.collectAsStateWithLifecycle()
    val pendingAdPhoto by viewModel.pendingAdEnhancementPhoto.collectAsStateWithLifecycle()
    val isAdLoaded by viewModel.isAdLoaded.collectAsStateWithLifecycle()
    val isAdLoading by viewModel.isAdLoading.collectAsStateWithLifecycle()

    val pendingQueueCount = queueItems.count { it.status is QueueItemStatus.Pending || it.status is QueueItemStatus.InProgress }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val activity = context as? Activity

    // 1. If photo preview overlay is open -> close photo preview on back
    BackHandler(enabled = previewPhoto != null) {
        viewModel.closePhotoPreview()
    }

    // 2. If camera is open -> close camera and return to AI Tools dashboard
    BackHandler(enabled = isCameraOpen && previewPhoto == null) {
        viewModel.closeCamera()
    }

    // 3. If in other studio tabs (Gallery, Queue, Settings, Studio) -> return to AI Tools tab
    BackHandler(enabled = !isCameraOpen && previewPhoto == null && currentTab != StudioTab.AI_TOOLS) {
        viewModel.selectTab(StudioTab.AI_TOOLS)
    }

    // 4. If on AI Tools tab -> double back press to exit app with toast
    BackHandler(enabled = !isCameraOpen && previewPhoto == null && currentTab == StudioTab.AI_TOOLS) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000L) {
            activity?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to close the app", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isCameraOpen, currentTab) {
        if (isCameraOpen) {
            PixenseAnalytics.logScreenView("CameraScreen")
        } else {
            when (currentTab) {
                StudioTab.AI_TOOLS -> PixenseAnalytics.logScreenView("AIToolsScreen")
                StudioTab.STUDIO -> PixenseAnalytics.logScreenView("StudioScreen")
                StudioTab.GALLERY -> PixenseAnalytics.logScreenView("AiGalleryScreen")
                StudioTab.QUEUE -> PixenseAnalytics.logScreenView("AiQueueScreen")
                StudioTab.SETTINGS -> PixenseAnalytics.logScreenView("SettingsScreen")
            }
        }
    }

    LaunchedEffect(saveStatusMessage) {
        saveStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSaveStatus()
        }
    }

    if (pendingAdPhoto != null) {
        LaunchedEffect(pendingAdPhoto) {
            viewModel.refreshAdStatus()
        }

        AlertDialog(
            onDismissRequest = {
                if (!isAdLoading) {
                    viewModel.dismissAdPrompt()
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BentoTheme.colors.purplePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Enhance Photo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = BentoTheme.colors.textPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Watch a short ad to enhance \"${pendingAdPhoto?.displayName ?: "this photo"}\" with Gemini AI.",
                        fontSize = 14.sp,
                        color = BentoTheme.colors.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activity != null) {
                            viewModel.showRewardedAd(activity)
                        } else {
                            Toast.makeText(context, "Activity context not found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isAdLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoTheme.colors.purplePrimary)
                ) {
                    if (isAdLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Loading Ad…", color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Watch Ad", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissAdPrompt() },
                    enabled = !isAdLoading
                ) {
                    Text(text = "Cancel", color = BentoTheme.colors.textSecondary)
                }
            }
        )
    }

    if (isCameraOpen) {
        CameraCaptureScreen(
            onPhotoCaptured = { uri -> viewModel.onPhotoCaptured(uri) },
            onClose = { viewModel.closeCamera() },
            onOpenGallery = {
                viewModel.closeCamera()
                viewModel.selectTab(StudioTab.STUDIO)
            },
            latestPhoto = latestPhoto,
            onOpenPreview = { photo ->
                viewModel.openPhotoPreview(photo)
            },
            onEnhancePhoto = { photo ->
                viewModel.enhanceSpecificPhoto(photo)
            },
            onDeletePhoto = { photo ->
                viewModel.deleteDcimPhoto(photo)
            },
            modifier = modifier
        )
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(BentoTheme.colors.bg),
            containerColor = BentoTheme.colors.bg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                StudioBottomNavigationBar(
                    currentTab = currentTab,
                    galleryCount = enhancedPhotos.size,
                    onSelectTab = { tab -> viewModel.selectTab(tab) },
                    onOpenCamera = { viewModel.openCamera() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    StudioTab.AI_TOOLS -> {
                        AIToolsScreen(
                            viewModel = viewModel,
                            onOpenQueue = { viewModel.selectTab(StudioTab.QUEUE) },
                            onOpenSettings = { viewModel.selectTab(StudioTab.SETTINGS) },
                            onSelectOperation = { op ->
                                viewModel.selectOperation(op)
                                viewModel.selectTab(StudioTab.STUDIO)
                            }
                        )
                    }
                    StudioTab.STUDIO -> {
                        StudioTabScreen(
                            viewModel = viewModel,
                            latestPhoto = latestPhoto,
                            isServiceActive = isServiceActive,
                            pendingQueueCount = pendingQueueCount,
                            enhancementState = enhancementState,
                            isShowingOriginal = isShowingOriginal,
                            isSaving = isSaving
                        )
                    }
                    StudioTab.GALLERY -> {
                        AiGalleryScreen(viewModel = viewModel)
                    }
                    StudioTab.QUEUE -> {
                        AiQueueScreen(viewModel = viewModel)
                    }
                    StudioTab.SETTINGS -> {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Full Screen Overlay Preview & Enhance (Smooth, no window clipping, perfectly consistent)
    AnimatedVisibility(
        visible = previewPhoto != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
        exit = fadeOut()
    ) {
        val currentPreview = previewPhoto
        if (currentPreview != null) {
            UnifiedPhotoPreviewOverlay(
                photo = currentPreview,
                operation = selectedOperation,
                onDismiss = { viewModel.closePhotoPreview() },
                onEnhance = { photo ->
                    viewModel.enhanceSpecificPhotoWithOperation(photo, selectedOperation)
                },
                onDelete = { photo ->
                    viewModel.deleteDcimPhoto(photo)
                },
                isFromCamera = isCameraOpen,
                onOpenStudio = {
                    viewModel.closePhotoPreview()
                    viewModel.closeCamera()
                    viewModel.selectTab(StudioTab.AI_TOOLS)
                },
                queueItems = queueItems,
                enhancedPhotos = enhancedPhotos
            )
        }
    }
}

@Composable
fun StudioBottomNavigationBar(
    currentTab: StudioTab,
    galleryCount: Int,
    onSelectTab: (StudioTab) -> Unit,
    onOpenCamera: () -> Unit
) {
    NavigationBar(
        containerColor = BentoTheme.colors.cardBg,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoTheme.colors.border)
            .testTag("main_bottom_nav")
    ) {
        NavigationBarItem(
            selected = currentTab == StudioTab.AI_TOOLS,
            onClick = { onSelectTab(StudioTab.AI_TOOLS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Tools",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("AI Tools", fontWeight = if (currentTab == StudioTab.AI_TOOLS) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BentoTheme.colors.purplePrimary,
                selectedTextColor = BentoTheme.colors.purplePrimary,
                indicatorColor = BentoTheme.colors.purpleContainer,
                unselectedIconColor = BentoTheme.colors.textSecondary,
                unselectedTextColor = BentoTheme.colors.textSecondary
            ),
            modifier = Modifier.testTag("nav_tab_ai_tools")
        )

        NavigationBarItem(
            selected = currentTab == StudioTab.STUDIO,
            onClick = { onSelectTab(StudioTab.STUDIO) },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Studio",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Studio", fontWeight = if (currentTab == StudioTab.STUDIO) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BentoTheme.colors.purplePrimary,
                selectedTextColor = BentoTheme.colors.purplePrimary,
                indicatorColor = BentoTheme.colors.purpleContainer,
                unselectedIconColor = BentoTheme.colors.textSecondary,
                unselectedTextColor = BentoTheme.colors.textSecondary
            ),
            modifier = Modifier.testTag("nav_tab_studio")
        )

        NavigationBarItem(
            selected = false,
            onClick = onOpenCamera,
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.purplePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            label = { Text("Camera", fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BentoTheme.colors.purplePrimary,
                selectedTextColor = BentoTheme.colors.purplePrimary,
                indicatorColor = Color.Transparent,
                unselectedIconColor = BentoTheme.colors.purplePrimary,
                unselectedTextColor = BentoTheme.colors.textSecondary
            ),
            modifier = Modifier.testTag("nav_tab_camera")
        )

        NavigationBarItem(
            selected = currentTab == StudioTab.GALLERY,
            onClick = { onSelectTab(StudioTab.GALLERY) },
            icon = {
                BadgedBox(
                    badge = {
                        if (galleryCount > 0) {
                            Badge(containerColor = BentoTheme.colors.purplePrimary) {
                                Text(galleryCount.toString(), color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "AI Gallery",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = { Text("AI Gallery", fontWeight = if (currentTab == StudioTab.GALLERY) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BentoTheme.colors.purplePrimary,
                selectedTextColor = BentoTheme.colors.purplePrimary,
                indicatorColor = BentoTheme.colors.purpleContainer,
                unselectedIconColor = BentoTheme.colors.textSecondary,
                unselectedTextColor = BentoTheme.colors.textSecondary
            ),
            modifier = Modifier.testTag("nav_tab_gallery")
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StudioTabScreen(
    viewModel: CameraAiViewModel,
    latestPhoto: CameraPhoto?,
    isServiceActive: Boolean,
    pendingQueueCount: Int,
    enhancementState: EnhancementUiState,
    isShowingOriginal: Boolean,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storagePermission = remember { PermissionUtils.getStoragePermission() }
    val storagePermissionState = rememberPermissionState(permission = storagePermission)
    var hasRequestedPermissionOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(storagePermissionState.status.isGranted) {
        if (storagePermissionState.status.isGranted) {
            viewModel.refreshDcimPaging()
            viewModel.refreshLatestPhoto()
            if (viewModel.isAutoProcessEnabled.value) {
                CameraCaptureService.start(context)
            }
        } else if (!hasRequestedPermissionOnce) {
            hasRequestedPermissionOnce = true
            storagePermissionState.launchPermissionRequest()
        }
    }

    if (storagePermissionState.status.isGranted) {
        val dcimLazyPagingItems = viewModel.dcimPagingFlow.collectAsLazyPagingItems()
        StudioWorkspaceContent(
            viewModel = viewModel,
            latestPhoto = latestPhoto,
            dcimLazyPagingItems = dcimLazyPagingItems,
            isServiceActive = isServiceActive,
            pendingQueueCount = pendingQueueCount,
            enhancementState = enhancementState,
            isShowingOriginal = isShowingOriginal,
            isSaving = isSaving
        )
    } else {
        StudioPermissionPlaceholderView(
            onRequestPermission = { storagePermissionState.launchPermissionRequest() },
            onOpenSettings = { PermissionUtils.openAppSettings(context) },
            onOpenCamera = { viewModel.openCamera() },
            onOpenQueue = { viewModel.selectTab(StudioTab.QUEUE) },
            modifier = modifier
        )
    }
}

@Composable
fun StudioPermissionPlaceholderView(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BentoHeader(
            onOpenQueue = onOpenQueue,
            onOpenSettings = onOpenSettings,
            pendingQueueCount = 0
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("studio_permission_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.purpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = BentoTheme.colors.purplePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Gallery Access Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Grant permission to browse your device photos, preview them, and remaster them with Gemini AI.",
                        fontSize = 14.sp,
                        color = BentoTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoTheme.colors.purplePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("studio_grant_permission_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grant Permission",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("studio_open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = BentoTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BentoTheme.colors.textPrimary
                        )
                    }

                    TextButton(
                        onClick = onOpenCamera,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("studio_open_camera_fallback_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = BentoTheme.colors.purplePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Take a Photo Instead",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BentoTheme.colors.purplePrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioWorkspaceContent(
    viewModel: CameraAiViewModel,
    latestPhoto: CameraPhoto?,
    dcimLazyPagingItems: LazyPagingItems<CameraPhoto>,
    isServiceActive: Boolean,
    pendingQueueCount: Int,
    enhancementState: EnhancementUiState,
    isShowingOriginal: Boolean,
    isSaving: Boolean
) {
    val context = LocalContext.current
    val selectedOperation by viewModel.selectedOperation.collectAsStateWithLifecycle()
    val totalLoaded = dcimLazyPagingItems.itemCount
    val refreshState = dcimLazyPagingItems.loadState.refresh
    val appendState = dcimLazyPagingItems.loadState.append

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Bento Header Section
        item(span = { GridItemSpan(maxLineSpan) }) {
            BentoHeader(
                onOpenQueue = { viewModel.selectTab(StudioTab.QUEUE) },
                onOpenSettings = { viewModel.selectTab(StudioTab.SETTINGS) },
                pendingQueueCount = pendingQueueCount
            )
        }

        // Contextual Active Tool Banner
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = BentoTheme.colors.cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoTheme.colors.purpleContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedOperation.iconEmoji,
                                fontSize = 20.sp
                            )
                        }
                        Column {
                            Text(
                                text = "Select photo to ${selectedOperation.title}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.textPrimary
                            )
                            Text(
                                text = selectedOperation.description,
                                fontSize = 11.sp,
                                color = BentoTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.selectTab(StudioTab.AI_TOOLS) }
                    ) {
                        Text(
                            text = "Change",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.purplePrimary
                        )
                    }
                }
            }
        }

        // Active Queue Banner (when photos are actively processing)
        if (pendingQueueCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectTab(StudioTab.QUEUE) }
                        .testTag("active_queue_banner"),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0288D1).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0288D1).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color(0xFF0288D1),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$pendingQueueCount photo${if (pendingQueueCount > 1) "s" else ""} processing in AI Queue…",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.textPrimary
                            )
                        }
                        Text(
                            text = "VIEW QUEUE →",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0288D1)
                        )
                    }
                }
            }
        }

        // Active Enhancement Result (Hero Before/After & AI Insights)
        if (enhancementState is EnhancementUiState.Success && latestPhoto != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BentoHeroPhotoCard(
                    photo = latestPhoto,
                    enhancementState = enhancementState,
                    isShowingOriginal = isShowingOriginal,
                    onToggleOriginal = { showOriginal -> viewModel.toggleShowOriginal(showOriginal) },
                    onShare = {
                        val currentSuccess = enhancementState as? EnhancementUiState.Success
                        if (currentSuccess != null) {
                            shareBitmap(context, currentSuccess.enhancedBitmap)
                        } else {
                            shareUri(context, latestPhoto.uri)
                        }
                    }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                BentoAiAnalysisCard(analysis = enhancementState.analysis)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioActionControls(
                    enhancementState = enhancementState,
                    isSaving = isSaving,
                    onSave = { viewModel.saveEnhancedPhoto() },
                    onReset = { viewModel.resetEnhancement() }
                )
            }
        }

        // Error Card (Visible when Gemini API or network fails)
        if (enhancementState is EnhancementUiState.Error) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val error = enhancementState as? EnhancementUiState.Error
                if (error != null) {
                    BentoErrorCard(
                        errorMessage = error.message,
                        onRetry = { viewModel.enhancePhoto() },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }
        }


        // Initial Loading, Error, Empty, or Photo Grid Items
        if (refreshState is LoadState.Loading && totalLoaded == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DcimLoadingState()
            }
        } else if (refreshState is LoadState.Error && totalLoaded == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DcimErrorState(onRetry = { dcimLazyPagingItems.retry() })
            }
        } else if (totalLoaded == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DcimEmptyState(onOpenCamera = { viewModel.openCamera() })
            }
        } else {
            // Lazy Photos: Only items entering the viewport are composed!
            // When scrolling, Paging 3 auto-loads next set of photos via LazyPagingItems
            items(
                count = totalLoaded,
                key = { index -> 
                    if (index < dcimLazyPagingItems.itemCount) {
                        dcimLazyPagingItems.peek(index)?.id ?: index
                    } else {
                        index
                    }
                }
            ) { index ->
                val photo = if (index < dcimLazyPagingItems.itemCount) dcimLazyPagingItems[index] else null
                if (photo != null) {
                    DcimPhotoGridItem(
                        photo = photo,
                        onClick = {
                            viewModel.openPhotoPreview(photo)
                            PixenseAnalytics.logAiPhotoSelected(selectedOperation.analyticsTag)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BentoTheme.colors.cardMuted)
                    )
                }
            }

            // Append state indicators at the bottom of the grid
            if (appendState is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DcimAppendLoadingIndicator()
                }
            } else if (appendState is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DcimAppendErrorIndicator(onRetry = { dcimLazyPagingItems.retry() })
                }
            } else if (appendState.endOfPaginationReached && totalLoaded > 4) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DcimAllLoadedIndicator(totalLoaded = totalLoaded)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AutoProcessToggleCard(
    isAutoProcessEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_process_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoProcessEnabled) BentoTheme.colors.purpleContainer.copy(alpha = 0.5f) else BentoTheme.colors.cardBg
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAutoProcessEnabled) BentoTheme.colors.purplePrimary.copy(alpha = 0.4f) else BentoTheme.colors.border
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isAutoProcessEnabled) BentoTheme.colors.purplePrimary else BentoTheme.colors.cardMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isAutoProcessEnabled) Color.White else BentoTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.auto_process_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.textPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAutoProcessEnabled) BentoTheme.colors.purplePrimary else BentoTheme.colors.cardMuted
                        ) {
                            Text(
                                text = if (isAutoProcessEnabled) "DCIM ACTIVE" else "DCIM ONLY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAutoProcessEnabled) Color.White else BentoTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Auto-enhances with Gemini 3.1 Flash Image for photos captured in native DCIM camera",
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isAutoProcessEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("auto_process_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BentoTheme.colors.purplePrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BentoTheme.colors.cardMuted
                )
            )
        }
    }
}

@Composable
fun BentoHeader(
    onOpenQueue: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    pendingQueueCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Pixense",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = BentoTheme.colors.textPrimary
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AI Queue Header Button with Pending Badge
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BentoTheme.colors.cardBg)
                    .border(1.dp, BentoTheme.colors.border, CircleShape)
                    .testTag("header_button_queue")
            ) {
                BadgedBox(
                    badge = {
                        if (pendingQueueCount > 0) {
                            Badge(
                                containerColor = Color(0xFF0288D1),
                                modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                            ) {
                                Text(pendingQueueCount.toString(), color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "AI Queue",
                        tint = if (pendingQueueCount > 0) Color(0xFF0288D1) else BentoTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Settings Header Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BentoTheme.colors.cardBg)
                    .border(1.dp, BentoTheme.colors.border, CircleShape)
                    .testTag("header_button_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = BentoTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BentoHeroPhotoCard(
    photo: CameraPhoto,
    enhancementState: EnhancementUiState,
    isShowingOriginal: Boolean,
    onToggleOriginal: (Boolean) -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color(0x1A000000)
            )
            .border(1.dp, BentoTheme.colors.border, RoundedCornerShape(32.dp))
            .testTag("photo_display_card"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardMuted)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .background(BentoTheme.colors.cardMuted)
                .pointerInput(enhancementState) {
                    if (enhancementState is EnhancementUiState.Success) {
                        detectTapGestures(
                            onPress = {
                                onToggleOriginal(true)
                                tryAwaitRelease()
                                onToggleOriginal(false)
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (enhancementState) {
                is EnhancementUiState.Success -> {
                    val bitmapToDisplay = if (isShowingOriginal) enhancementState.originalBitmap else enhancementState.enhancedBitmap
                    Image(
                        bitmap = bitmapToDisplay.asImageBitmap(),
                        contentDescription = "Captured camera photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                else -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Processing Overlay
            if (enhancementState is EnhancementUiState.Processing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BentoTheme.colors.purpleLight,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = enhancementState.stage,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Top-Left Bento Pill: "LATEST CAPTURE" / "AI ENHANCED" / "ERROR"
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = when {
                    enhancementState is EnhancementUiState.Error -> Color(0xFFD32F2F).copy(alpha = 0.85f)
                    isShowingOriginal -> Color.Black.copy(alpha = 0.65f)
                    enhancementState is EnhancementUiState.Success -> BentoTheme.colors.purpleDark.copy(alpha = 0.85f)
                    else -> Color.Black.copy(alpha = 0.45f)
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            enhancementState is EnhancementUiState.Error -> Icons.Default.Warning
                            isShowingOriginal -> Icons.Default.Compare
                            enhancementState is EnhancementUiState.Success -> Icons.Default.AutoAwesome
                            else -> Icons.Outlined.PhotoCamera
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            enhancementState is EnhancementUiState.Error -> "AI ENHANCE FAILED"
                            isShowingOriginal -> "VIEWING ORIGINAL"
                            enhancementState is EnhancementUiState.Success -> "GEMINI 3.1 4K REMASTER"
                            else -> "NATIVE CAMERA CAPTURE"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Hold-to-compare instruction tooltip
            if (enhancementState is EnhancementUiState.Success) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 70.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Hold photo to compare with original",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Overlay Gradient with File Title & Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = photo.displayName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Captured ${getRelativeTimeSpan(photo.dateTaken)}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BentoTheme.colors.purpleLight)
                        .testTag("share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = BentoTheme.colors.purpleText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DcimHeaderCard(
    totalLoaded: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dcim_photos_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.purpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = BentoTheme.colors.purplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "DCIM Camera Photos",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = if (totalLoaded > 0) "$totalLoaded photo${if (totalLoaded == 1) "" else "s"} loaded" else "DCIM Gallery",
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun DcimLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.5.dp,
                color = BentoTheme.colors.purplePrimary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Loading DCIM gallery…",
                fontSize = 12.sp,
                color = BentoTheme.colors.textSecondary
            )
        }
    }
}

@Composable
fun DcimErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Failed to load photos",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE53935)
            )
            FilledTonalButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DcimEmptyState(
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Collections,
                contentDescription = null,
                tint = BentoTheme.colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No photos in DCIM folder",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = BentoTheme.colors.textSecondary
            )
            FilledTonalButton(
                onClick = onOpenCamera,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BentoTheme.colors.purpleContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = BentoTheme.colors.purplePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Take a Photo", color = BentoTheme.colors.purplePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DcimAppendLoadingIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag("dcim_load_more_indicator"),
        color = BentoTheme.colors.purpleContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.purplePrimary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = BentoTheme.colors.purplePrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Paging 3 loading more photos…",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BentoTheme.colors.purplePrimary
            )
        }
    }
}

@Composable
fun DcimAppendErrorIndicator(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Error loading next page",
            fontSize = 12.sp,
            color = Color(0xFFE53935)
        )
        TextButton(onClick = onRetry) {
            Text("Retry", fontSize = 12.sp, color = BentoTheme.colors.purplePrimary)
        }
    }
}

@Composable
fun DcimAllLoadedIndicator(
    totalLoaded: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✓ All $totalLoaded photos loaded",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = BentoTheme.colors.textSecondary
        )
    }
}

@Composable
fun DcimPhotoPagingGrid(
    pagingItems: LazyPagingItems<CameraPhoto>,
    onPhotoClick: (CameraPhoto) -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalLoaded = pagingItems.itemCount
    val refreshState = pagingItems.loadState.refresh
    val appendState = pagingItems.loadState.append

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dcim_photos_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DcimHeaderCard(totalLoaded = totalLoaded)

            if (refreshState is LoadState.Loading && totalLoaded == 0) {
                DcimLoadingState()
            } else if (refreshState is LoadState.Error && totalLoaded == 0) {
                DcimErrorState(onRetry = { pagingItems.retry() })
            } else if (totalLoaded == 0) {
                DcimEmptyState(onOpenCamera = onOpenCamera)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = totalLoaded,
                        key = { index -> 
                            if (index < pagingItems.itemCount) {
                                pagingItems.peek(index)?.id ?: index
                            } else {
                                index
                            }
                        }
                    ) { index ->
                        val photo = if (index < pagingItems.itemCount) pagingItems[index] else null
                        if (photo != null) {
                            DcimPhotoGridItem(
                                photo = photo,
                                onClick = { onPhotoClick(photo) }
                            )
                        }
                    }
                }

                if (appendState is LoadState.Loading) {
                    DcimAppendLoadingIndicator()
                } else if (appendState is LoadState.Error) {
                    DcimAppendErrorIndicator(onRetry = { pagingItems.retry() })
                } else if (appendState.endOfPaginationReached && totalLoaded > 4) {
                    DcimAllLoadedIndicator(totalLoaded = totalLoaded)
                }
            }
        }
    }
}

@Composable
fun DcimPhotoGridItem(
    photo: CameraPhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("dcim_grid_item_${photo.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardMuted),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OptimizedThumbnailImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                targetSizePx = 400,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                memoryCacheKey = "dcim_thumb_${photo.id}"
            )

            // Top gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )

            // Bottom gradient overlay with metadata
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = photo.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = photo.resolutionText,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = photo.formattedSize,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedPhotoPreviewOverlay(
    photo: CameraPhoto,
    operation: AiPhotoOperation = AiPhotoOperation.FIX,
    onDismiss: () -> Unit,
    onEnhance: (CameraPhoto) -> Unit,
    onDelete: ((CameraPhoto) -> Unit)? = null,
    isFromCamera: Boolean = false,
    onOpenStudio: (() -> Unit)? = null,
    queueItems: List<EnhancementQueueItem> = emptyList(),
    enhancedPhotos: List<EnhancedPhotoEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Check if this photo is currently being processed by the Gemini AI Queue
    val isProcessing = queueItems.any { item ->
        (item.photo.id == photo.id || item.photo.uri == photo.uri || item.photo.displayName == photo.displayName) &&
        (item.status is QueueItemStatus.Pending || item.status is QueueItemStatus.InProgress)
    }

    // Check if photo is enhanced or has an enhanced entity in Room DB
    val matchingEnhanced = enhancedPhotos.firstOrNull { entity ->
        entity.originalUri == photo.uri ||
        entity.originalDisplayName == photo.displayName ||
        (entity.id != 0L && entity.id == photo.id) ||
        entity.enhancedUri == photo.uri
    }
    val isEnhanced = photo.isEnhancedImage || matchingEnhanced != null

    // Before/After comparison toggle state (false = show enhanced after, true = show original before)
    var isShowingBefore by remember(photo.id, matchingEnhanced?.id) { mutableStateOf(false) }

    // Active displayed image URI
    val currentDisplayedUri = when {
        !isEnhanced -> photo.uri
        isShowingBefore -> matchingEnhanced?.originalUri ?: photo.uri
        else -> matchingEnhanced?.enhancedUri ?: photo.uri
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = "Delete Photo?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BentoTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${photo.displayName}\" from your device storage?",
                    fontSize = 14.sp,
                    color = BentoTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete?.invoke(photo)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_dcim_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = BentoTheme.colors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* consume background taps */ }
            .testTag(if (isFromCamera) "camera_thumbnail_preview_overlay" else "dcim_photo_preview_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .testTag(if (isFromCamera) "close_preview_overlay_button" else "close_preview_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = if (isEnhanced) {
                            if (isShowingBefore) "[ORIGINAL] ${photo.displayName}" else "[ENHANCED] ${photo.displayName}"
                        } else {
                            photo.displayName
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${photo.resolutionText} • ${photo.formattedSize}",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Compare Before/After Toggle Icon Button (ONLY added if image is enhanced by AI)
                    if (isEnhanced) {
                        IconButton(
                            onClick = { isShowingBefore = !isShowingBefore },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isShowingBefore) Color(0xFFF59E0B).copy(alpha = 0.35f) else BentoTheme.colors.purplePrimary.copy(alpha = 0.45f))
                                .testTag("preview_compare_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = "Toggle Before/After Comparison",
                                tint = if (isShowingBefore) Color(0xFFFBBF24) else Color(0xFFC084FC),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.3f))
                                .testTag("preview_dialog_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Photo",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isFromCamera && onOpenStudio != null) {
                        IconButton(
                            onClick = onOpenStudio,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .testTag("preview_open_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Open Studio Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                shareUri(context, currentDisplayedUri)
                                PixenseAnalytics.logEvent("ai_result_shared", mapOf("feature" to operation.analyticsTag))
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .testTag("preview_dialog_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Image Preview (Takes weight(1f), perfectly scaled, never pushes bottom bar out of view)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Zoomable preview so users can pinch-to-zoom and pan the image
                ZoomableAsyncImage(
                    model = currentDisplayedUri,
                    contentDescription = photo.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Before / After Indicator Pill over the image (ONLY if enhanced by AI)
                if (isEnhanced) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isShowingBefore) Color(0xFF1F2937).copy(alpha = 0.88f) else BentoTheme.colors.purplePrimary.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isShowingBefore) Color(0xFFF59E0B) else Color(0xFFC084FC)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isShowingBefore = !isShowingBefore }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isShowingBefore) Icons.Default.Compare else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isShowingBefore) Color(0xFFFBBF24) else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isShowingBefore) "BEFORE (ORIGINAL) • TAP FOR AI" else "AFTER (GEMINI AI ENHANCED) • TAP FOR BEFORE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom Actions Card (Guaranteed on-screen above system navigation bar)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1E1E24).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage: ${photo.relativePath}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isEnhanced) Color(0xFF10B981).copy(alpha = 0.25f) else BentoTheme.colors.purplePrimary.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isEnhanced) Color(0xFF34D399) else BentoTheme.colors.purplePrimary)
                        ) {
                            Text(
                                text = if (isEnhanced) "ENHANCED • 100% QUALITY" else "${operation.iconEmoji} ${operation.title.uppercase()} • GEMINI AI",
                                color = if (isEnhanced) Color(0xFF34D399) else Color(0xFFC084FC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isProcessing) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("preview_processing_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = BentoTheme.colors.purplePrimary.copy(alpha = 0.7f),
                                    disabledContentColor = Color.White
                                )
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = operation.statusMessage,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        } else if (isEnhanced) {
                            Button(
                                onClick = { isShowingBefore = !isShowingBefore },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("preview_compare_toggle_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isShowingBefore) Color(0xFF374151) else BentoTheme.colors.purplePrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Compare,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isShowingBefore) "Showing Original • Tap for AI Enhanced" else "Showing AI Enhanced • Tap for Original",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    onEnhance(photo)
                                    // Keep overlay open so user can see progress and enhanced result
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag(if (isFromCamera) "preview_enhance_button" else "modal_enhance_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoTheme.colors.purplePrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${operation.title} with Gemini AI",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bento_error_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFCDD2))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD32F2F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Gemini AI Enhancement Error",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        tint = Color(0xFFB71C1C),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = Color(0xFFC62828),
                lineHeight = 17.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.testTag("retry_ai_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Retry Gemini AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BentoAiAnalysisCard(analysis: AiPhotoAnalysis) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_analysis_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(BentoTheme.colors.purpleContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BentoTheme.colors.purpleDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Detected Scene: ${analysis.category.emoji} ${analysis.sceneType}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Text(
                        text = "${analysis.confidenceScore}% CONFIDENCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF166534),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                    )
                }
            }

            Text(
                text = analysis.aiInsight,
                fontSize = 12.sp,
                color = BentoTheme.colors.textSecondary,
                lineHeight = 17.sp
            )

            if (analysis.detectedElements.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    analysis.detectedElements.take(3).forEach { element ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BentoTheme.colors.cardMuted
                        ) {
                            Text(
                                text = "• $element",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BentoTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiScorePill(
                    label = "Lighting",
                    value = "${analysis.lightingScore}%",
                    color = Color(0xFFE65100),
                    bg = Color(0xFFFFF3E0),
                    modifier = Modifier.weight(1f)
                )
                AiScorePill(
                    label = "Sharpness",
                    value = "${analysis.sharpnessScore}%",
                    color = BentoTheme.colors.aiBlueText,
                    bg = BentoTheme.colors.cardAiBlue,
                    modifier = Modifier.weight(1f)
                )
                AiScorePill(
                    label = "Noise Red.",
                    value = "${analysis.noiseReductionScore}%",
                    color = BentoTheme.colors.purpleText,
                    bg = BentoTheme.colors.purpleContainer,
                    modifier = Modifier.weight(1f)
                )
                AiScorePill(
                    label = "Output",
                    value = "4K HDR",
                    color = Color(0xFF2E7D32),
                    bg = Color(0xFFE8F5E9),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AiScorePill(
    label: String,
    value: String,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StudioActionControls(
    enhancementState: EnhancementUiState,
    isSaving: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    if (enhancementState is EnhancementUiState.Success) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_enhanced_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSaving) "Saving…" else "Save to Gallery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reset_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoTheme.colors.textPrimary)
                ) {
                    Text("Reset", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyCameraState(
    onLoadSample: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_camera_state"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardMuted),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BentoTheme.colors.purpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = BentoTheme.colors.purplePrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Camera Photos Yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the floating camera button below to snap a photo in-app. Pixense will automatically process and enhance it with Gemini AI.",
                    fontSize = 13.sp,
                    color = BentoTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            FilledTonalButton(
                onClick = onLoadSample,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("load_sample_photo_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BentoTheme.colors.cardAiBlue)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BentoTheme.colors.aiBluePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Scenic Demo Photo", color = BentoTheme.colors.aiBlueText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .testTag("theme_selection_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BentoTheme.colors.purpleContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = BentoTheme.colors.purplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.theme_dialog_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.textPrimary
                            )
                            Text(
                                text = "Choose app appearance",
                                fontSize = 12.sp,
                                color = BentoTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = BentoTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionRow(
                        title = stringResource(R.string.theme_system),
                        subtitle = "Follows Android OS system theme",
                        icon = Icons.Default.BrightnessAuto,
                        isSelected = currentMode == ThemeMode.SYSTEM,
                        onClick = { onSelectMode(ThemeMode.SYSTEM) }
                    )

                    ThemeOptionRow(
                        title = stringResource(R.string.theme_light),
                        subtitle = "Clean light aesthetic",
                        icon = Icons.Default.LightMode,
                        isSelected = currentMode == ThemeMode.LIGHT,
                        onClick = { onSelectMode(ThemeMode.LIGHT) }
                    )

                    ThemeOptionRow(
                        title = stringResource(R.string.theme_dark),
                        subtitle = "Dark aesthetic for low light",
                        icon = Icons.Default.DarkMode,
                        isSelected = currentMode == ThemeMode.DARK,
                        onClick = { onSelectMode(ThemeMode.DARK) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Done",
                            color = BentoTheme.colors.purplePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
            .testTag("theme_option_${title.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BentoTheme.colors.purpleContainer.copy(alpha = 0.6f) else BentoTheme.colors.cardMuted,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) BentoTheme.colors.purplePrimary.copy(alpha = 0.5f) else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) BentoTheme.colors.purplePrimary else BentoTheme.colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary
                    )
                }
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = BentoTheme.colors.purplePrimary,
                    unselectedColor = BentoTheme.colors.textSecondary
                )
            )
        }
    }
}

private fun getRelativeTimeSpan(timeMillis: Long): String {
    val diff = System.currentTimeMillis() - timeMillis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "recently"
    }
}

private fun shareUri(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Photo"))
}

private fun shareBitmap(context: android.content.Context, bitmap: Bitmap) {
    try {
        val cachePath = java.io.File(context.cacheDir, "shared_images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "enhanced_share.jpg")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.close()

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Enhanced Photo"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing ready in local gallery", Toast.LENGTH_SHORT).show()
    }
}
