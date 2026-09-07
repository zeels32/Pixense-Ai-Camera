package com.pixense.app.ui.screen

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlin.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.pixense.app.ui.view.OptimizedThumbnailImage
import com.pixense.app.data.camera.CameraFocusHelper
import com.pixense.app.data.camera.CameraLensDetector
import com.pixense.app.data.camera.CameraLensPreset
import com.pixense.app.data.model.CameraPhoto
import com.pixense.app.ui.theme.BentoPurplePrimary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

enum class CameraFlashMode {
    AUTO, ON, OFF
}

enum class CameraAspectRatio(
    val displayName: String,
    val ratioWidthToHeight: Float, // width / height for portrait
    val cameraXRatio: Int
) {
    RATIO_4_3("4:3", 3f / 4f, androidx.camera.core.AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", 9f / 16f, androidx.camera.core.AspectRatio.RATIO_16_9),
    RATIO_1_1("1:1", 1f, androidx.camera.core.AspectRatio.RATIO_4_3)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCaptureScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    onOpenGallery: () -> Unit,
    latestPhoto: CameraPhoto?,
    onOpenPreview: (CameraPhoto) -> Unit = {},
    onEnhancePhoto: (CameraPhoto) -> Unit = {},
    onDeletePhoto: (CameraPhoto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        CameraViewContent(
            onPhotoCaptured = onPhotoCaptured,
            onClose = onClose,
            onOpenGallery = onOpenGallery,
            latestPhoto = latestPhoto,
            onOpenPreview = onOpenPreview,
            onEnhancePhoto = onEnhancePhoto,
            onDeletePhoto = onDeletePhoto,
            modifier = modifier
        )
    } else {
        CameraPermissionRequestView(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
            onClose = onClose,
            modifier = modifier
        )
    }
}

@Composable
private fun CameraViewContent(
    onPhotoCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    onOpenGallery: () -> Unit,
    latestPhoto: CameraPhoto?,
    onOpenPreview: (CameraPhoto) -> Unit = {},
    onEnhancePhoto: (CameraPhoto) -> Unit = {},
    onDeletePhoto: (CameraPhoto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT
    var lensRotationAngle by remember { mutableFloatStateOf(0f) }
    val animatedLensRotation by animateFloatAsState(
        targetValue = lensRotationAngle,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "lensRotation"
    )
    var mirrorSelfie by remember { mutableStateOf(true) }
    var flashMode by remember { mutableStateOf(CameraFlashMode.AUTO) }
    var selectedAspectRatio by remember { mutableStateOf(CameraAspectRatio.RATIO_4_3) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var flashScreenEffect by remember { mutableStateOf(false) }

    // Zoom & Hardware Lens state
    var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(8.0f) }
    var activeLensToast by remember { mutableStateOf<String?>(null) }
    var activeLensToastJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Detect optical / physical camera lenses from hardware (e.g. 0.6x ultra-wide, 1x wide, 2x/3x telephoto)
    val detectedLenses = remember(lensFacing, minZoomRatio, maxZoomRatio) {
        CameraLensDetector.detectAvailableLenses(
            context = context,
            lensFacing = lensFacing,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio
        )
    }

    // Exposure compensation state
    var exposureIndex by remember { mutableIntStateOf(0) }
    var minExposureIndex by remember { mutableIntStateOf(-4) }
    var maxExposureIndex by remember { mutableIntStateOf(4) }
    var exposureStep by remember { mutableFloatStateOf(0.5f) }
    var isExposureSupported by remember { mutableStateOf(false) }
    var showExposureSlider by remember { mutableStateOf(false) }

    // Focus & Exposure indicator state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var isFocusLocked by remember { mutableStateOf<Boolean?>(null) }
    var focusRingScale by remember { mutableFloatStateOf(1.3f) }
    var focusDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // CameraX instance references
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    // Helper to safely set zoom ratio with optional lens feedback
    val setZoom: (Float, String?) -> Unit = { targetRatio, lensFeedback ->
        val clamped = targetRatio.coerceIn(minZoomRatio, maxZoomRatio)
        currentZoomRatio = clamped
        camera?.cameraControl?.setZoomRatio(clamped)

        val feedbackText = lensFeedback ?: run {
            val matched = detectedLenses.find { kotlin.math.abs(it.ratio - clamped) < 0.08f }
            matched?.let { "${it.label} • ${it.lensName}" }
        }

        if (feedbackText != null) {
            activeLensToastJob?.cancel()
            activeLensToastJob = coroutineScope.launch {
                activeLensToast = feedbackText
                delay(1800)
                activeLensToast = null
            }
        }
    }

    // Helper to safely update exposure compensation
    val setExposure: (Int) -> Unit = { targetIndex ->
        val clamped = targetIndex.coerceIn(minExposureIndex, maxExposureIndex)
        exposureIndex = clamped
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
    }

    // Re-bind Camera Provider when lensFacing, flashMode, or target aspect ratio changes
    LaunchedEffect(lensFacing, flashMode, selectedAspectRatio) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val aspectRatioStrategy = AspectRatioStrategy(
                    selectedAspectRatio.cameraXRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )

                val previewResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectRatioStrategy)
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val captureFlashMode = when (flashMode) {
                    CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                }

                // Select highest available physical resolution matching the selected aspect ratio
                val captureResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectRatioStrategy)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val captureBuilder = ImageCapture.Builder()
                    .setResolutionSelector(captureResolutionSelector)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .setFlashMode(captureFlashMode)

                // Safe hardware ISP enhancements (OIS, chromatic aberration correction, distortion correction)
                // Note: Auto White Balance and Tonemapping are managed by CameraX & ISP to match viewfinder vibrancy
                val camera2Extender = Camera2Interop.Extender(captureBuilder)
                camera2Extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
                camera2Extender.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
                camera2Extender.setCaptureRequestOption(
                    CaptureRequest.DISTORTION_CORRECTION_MODE,
                    CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY
                )

                val capture = captureBuilder.build()

                val baseSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                // Check and apply OEM CameraX Extensions (Auto HDR / Night / Scene Optimization)
                val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider)
                extensionsManagerFuture.addListener({
                    try {
                        val extensionsManager = extensionsManagerFuture.get()
                        val cameraSelector = when {
                            extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.AUTO) -> {
                                extensionsManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.AUTO)
                            }
                            extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.HDR) -> {
                                extensionsManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
                            }
                            else -> baseSelector
                        }

                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )

                        camera = boundCamera
                        imageCapture = capture

                        // Observe Zoom State
                        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                            if (zoomState != null) {
                                minZoomRatio = zoomState.minZoomRatio
                                maxZoomRatio = zoomState.maxZoomRatio
                                currentZoomRatio = zoomState.zoomRatio
                            }
                        }

                        // Query Exposure State
                        val expState = boundCamera.cameraInfo.exposureState
                        isExposureSupported = expState.isExposureCompensationSupported
                        if (expState.isExposureCompensationSupported) {
                            minExposureIndex = expState.exposureCompensationRange.lower
                            maxExposureIndex = expState.exposureCompensationRange.upper
                            exposureIndex = expState.exposureCompensationIndex
                            val num = expState.exposureCompensationStep.numerator.toFloat()
                            val den = expState.exposureCompensationStep.denominator.toFloat().coerceAtLeast(1f)
                            exposureStep = num / den
                        } else {
                            minExposureIndex = -4
                            maxExposureIndex = 4
                            exposureIndex = 0
                            exposureStep = 0.5f
                        }
                    } catch (extExc: Exception) {
                        Log.w("CameraCaptureScreen", "Extensions binding failed, falling back to base selector", extExc)
                        try {
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                baseSelector,
                                preview,
                                capture
                            )
                            camera = boundCamera
                            imageCapture = capture
                        } catch (bindExc: Exception) {
                            Log.e("CameraCaptureScreen", "Fallback binding failed", bindExc)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (exc: Exception) {
                Log.e("CameraCaptureScreen", "Camera setup failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camerax_capture_screen")
    ) {
        // Centered Camera Viewfinder Container adhering to Selected Aspect Ratio
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(selectedAspectRatio.ratioWidthToHeight)
                    .clip(RoundedCornerShape(if (selectedAspectRatio == CameraAspectRatio.RATIO_16_9) 0.dp else 12.dp))
                    .background(Color.Black)
            ) {
                // CameraX Preview Layer with Touch-to-Focus (Front & Rear) and Pinch-to-Zoom
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(lensFacing, detectedLenses) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (detectedLenses.size > 1) {
                                        val currentIndex = detectedLenses.indexOfFirst { kotlin.math.abs(it.ratio - currentZoomRatio) < 0.12f }
                                        val nextIndex = if (currentIndex == -1 || currentIndex == detectedLenses.size - 1) 0 else currentIndex + 1
                                        val nextLens = detectedLenses[nextIndex]
                                        setZoom(nextLens.ratio, "${nextLens.label} • ${nextLens.lensName}")
                                    }
                                },
                                onTap = { offset ->
                                    focusPoint = offset
                                    isFocusLocked = null
                                    setExposure(0)
                                    dragAccumulator = 0f
                                    val pView = previewView
                                    val activeCam = camera ?: return@detectTapGestures

                                    // Trigger CameraX tap-to-focus and tap-to-expose via CameraFocusHelper
                                    CameraFocusHelper.tapToFocusAndExpose(
                                        previewView = pView,
                                        camera = activeCam,
                                        x = offset.x,
                                        y = offset.y,
                                        autoCancelSeconds = 3L
                                    ) { isSuccess ->
                                        if (focusPoint == offset) {
                                            isFocusLocked = isSuccess
                                        }
                                    }

                                    focusDismissJob?.cancel()
                                    focusDismissJob = coroutineScope.launch {
                                        focusRingScale = 1.4f
                                        delay(100)
                                        focusRingScale = 1.0f
                                        delay(3000)
                                        if (focusPoint == offset) {
                                            focusPoint = null
                                            isFocusLocked = null
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(minZoomRatio, maxZoomRatio) {
                            detectTransformGestures { _, _, zoomFactor, _ ->
                                val newZoom = (currentZoomRatio * zoomFactor).coerceIn(minZoomRatio, maxZoomRatio)
                                currentZoomRatio = newZoom
                                camera?.cameraControl?.setZoomRatio(newZoom)
                            }
                        }
                )

                // Rule of Thirds Composition Grid Overlay
                if (showGrid) {
                    CameraGridOverlay(modifier = Modifier.fillMaxSize())
                }

                // Touch-to-Focus Indicator with Draggable Vertical Exposure Slider
                focusPoint?.let { point ->
                    val density = LocalDensity.current
                    val animatedScale by animateFloatAsState(
                        targetValue = focusRingScale,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "focusScale"
                    )

                    // Helper to reset auto-dismiss timer while interacting
                    val restartDismissTimer: () -> Unit = {
                        focusDismissJob?.cancel()
                        focusDismissJob = coroutineScope.launch {
                            delay(3000)
                            focusPoint = null
                            isFocusLocked = null
                        }
                    }

                    // Dynamic color: Emerald Green when locked, Amber on failure/timeout, Gold while actively focusing
                    val reticleColor = when (isFocusLocked) {
                        true -> Color(0xFF10B981)
                        false -> Color(0xFFF59E0B)
                        null -> Color(0xFFFFD700)
                    }

                    // Focus reticle
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (point.x - with(density) { 36.dp.toPx() }).roundToInt(),
                                    y = (point.y - with(density) { 36.dp.toPx() }).roundToInt()
                                )
                            }
                            .size(72.dp)
                            .scale(animatedScale)
                            .border(2.dp, reticleColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(reticleColor, CircleShape)
                        )
                    }

                    // Minimalist Single Vertical Draggable Exposure Slider beside the focus reticle
                    val trackHeightDp = 110.dp
                    val trackHeightPx = with(density) { trackHeightDp.toPx() }
                    val rangeSpan = (maxExposureIndex - minExposureIndex).coerceAtLeast(1)
                    val progress = ((exposureIndex - minExposureIndex).toFloat() / rangeSpan.toFloat()).coerceIn(0f, 1f)
                    val thumbOffsetYDp = trackHeightDp * (1f - progress)

                    Box(
                        modifier = Modifier
                            .offset {
                                val sliderX = (point.x + with(density) { 42.dp.toPx() })
                                    .coerceIn(
                                        with(density) { 12.dp.toPx() },
                                        with(density) { 320.dp.toPx() }
                                    )
                                val sliderY = (point.y - with(density) { 55.dp.toPx() })
                                    .coerceIn(
                                        with(density) { 20.dp.toPx() },
                                        with(density) { 420.dp.toPx() }
                                    )
                                IntOffset(sliderX.roundToInt(), sliderY.roundToInt())
                            }
                            .width(44.dp)
                            .height(trackHeightDp)
                            .pointerInput(minExposureIndex, maxExposureIndex) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        restartDismissTimer()
                                        val frac = 1f - (offset.y / trackHeightPx).coerceIn(0f, 1f)
                                        val newIndex = (minExposureIndex + frac * rangeSpan).roundToInt()
                                        setExposure(newIndex)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        restartDismissTimer()
                                        val touchY = change.position.y
                                        val frac = 1f - (touchY / trackHeightPx).coerceIn(0f, 1f)
                                        val newIndex = (minExposureIndex + frac * rangeSpan).roundToInt()
                                        setExposure(newIndex)
                                    },
                                    onDragEnd = {
                                        restartDismissTimer()
                                    },
                                    onDragCancel = {
                                        restartDismissTimer()
                                    }
                                )
                            }
                            .testTag("focus_draggable_exposure_slider"),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Background Track Line with Center Tick
                        Canvas(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .align(Alignment.Center)
                        ) {
                            // Dark subtle backdrop line for high contrast
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                size = androidx.compose.ui.geometry.Size(size.width + 2.dp.toPx(), size.height),
                                topLeft = Offset(-1.dp.toPx(), 0f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                            )
                            // White track line
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.75f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                            )
                            // Zero/Center Mark
                            val zeroProgress = (-minExposureIndex).toFloat() / rangeSpan.toFloat()
                            val zeroY = size.height * (1f - zeroProgress)
                            drawLine(
                                color = Color.White,
                                start = Offset(-5.dp.toPx(), zeroY),
                                end = Offset(size.width + 5.dp.toPx(), zeroY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // Luminous Sun Handle Thumb on Single Slider Track
                        Box(
                            modifier = Modifier
                                .offset(y = (thumbOffsetYDp - 13.dp).coerceIn(0.dp, trackHeightDp - 26.dp))
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = "Exposure Slider Handle",
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top Controls Bar (Exit to Studio, Flash, Aspect Ratio, Mirror Selfie [front only], Grid)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit / Back to Studio Button
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.testTag("camera_close_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Studio",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Studio",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Top Quick Action Icons (Flash, Aspect Ratio, Grid, Selfie Mirror [front-only])
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Flash Toggle Button
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                CameraFlashMode.AUTO -> CameraFlashMode.ON
                                CameraFlashMode.ON -> CameraFlashMode.OFF
                                CameraFlashMode.OFF -> CameraFlashMode.AUTO
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("camera_flash_toggle")
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                CameraFlashMode.AUTO -> Icons.Default.FlashAuto
                                CameraFlashMode.ON -> Icons.Default.FlashOn
                                CameraFlashMode.OFF -> Icons.Default.FlashOff
                            },
                            contentDescription = "Flash: $flashMode",
                            tint = if (flashMode == CameraFlashMode.OFF) Color.White.copy(alpha = 0.6f) else Color(0xFFFFD700),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Aspect Ratio Selector Toggle
                    Surface(
                        onClick = { showAspectRatioMenu = !showAspectRatioMenu },
                        shape = CircleShape,
                        color = if (showAspectRatioMenu) BentoPurplePrimary else Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("camera_aspect_ratio_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = selectedAspectRatio.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Grid Toggle Button
                    IconButton(
                        onClick = { showGrid = !showGrid },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("camera_grid_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Toggle Grid",
                            tint = if (showGrid) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Selfie Mirror Toggle (ONLY visible when selfie / front camera is active)
                    if (isFrontCamera) {
                        IconButton(
                            onClick = { mirrorSelfie = !mirrorSelfie },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (mirrorSelfie) BentoPurplePrimary
                                    else Color.Black.copy(alpha = 0.55f)
                                )
                                .testTag("camera_mirror_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flip,
                                contentDescription = "Mirror Selfie: ${if (mirrorSelfie) "Enabled" else "Disabled"}",
                                tint = if (mirrorSelfie) Color.White else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Aspect Ratio Selection Pill Row
            AnimatedVisibility(
                visible = showAspectRatioMenu,
                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                exit = fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraAspectRatio.entries.forEach { ratio ->
                        val isSelected = selectedAspectRatio == ratio
                        Surface(
                            onClick = {
                                selectedAspectRatio = ratio
                                showAspectRatioMenu = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) BentoPurplePrimary else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.testTag("ratio_option_${ratio.displayName.replace(":", "_")}")
                        ) {
                            Text(
                                text = ratio.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Capture Controls Bar with Hardware Lens Presets & Mode Badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(bottom = 24.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Active Lens Switch HUD Pill Notification
            AnimatedVisibility(
                visible = activeLensToast != null,
                enter = fadeIn(tween(150)) + androidx.compose.animation.scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(200)) + androidx.compose.animation.scaleOut(targetScale = 0.9f)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700))
                        )
                        Text(
                            text = activeLensToast ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }

            // Hardware Camera Lens Preset Selector (e.g. 0.6x, 1x, 2x, 3x, 5x)
            ZoomPresetSelector(
                lensPresets = detectedLenses,
                currentZoomRatio = currentZoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                onSelectPreset = { preset ->
                    setZoom(preset.ratio, "${preset.label} • ${preset.lensName}")
                }
            )

            // Capture Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Photo Preview Thumbnail / Gallery Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            if (latestPhoto != null) {
                                onOpenPreview(latestPhoto)
                            } else {
                                onOpenGallery()
                            }
                        }
                        .testTag("camera_gallery_thumbnail"),
                    contentAlignment = Alignment.Center
                ) {
                    if (latestPhoto != null) {
                        OptimizedThumbnailImage(
                            model = latestPhoto.uri,
                            contentDescription = "Latest Photo",
                            targetSizePx = 200,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            memoryCacheKey = "cam_thumb_${latestPhoto.id}"
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Center: Shutter Button
                CameraShutterButton(
                    isCapturing = isCapturing,
                    onClick = {
                        if (isCapturing) return@CameraShutterButton
                        val capture = imageCapture ?: return@CameraShutterButton
                        isCapturing = true

                        // Trigger shutter flash
                        flashScreenEffect = true
                        coroutineScope.launch {
                            delay(100)
                            flashScreenEffect = false
                        }

                        takePhotoAndSaveToDcim(
                            context = context,
                            imageCapture = capture,
                            isFrontCamera = isFrontCamera,
                            mirrorSelfie = mirrorSelfie,
                            aspectRatio = selectedAspectRatio,
                            onSuccess = { savedUri ->
                                isCapturing = false
                                onPhotoCaptured(savedUri)
                            },
                            onError = { exc ->
                                isCapturing = false
                                Log.e("CameraCaptureScreen", "Capture failed: ${exc.message}", exc)
                            }
                        )
                    }
                )

                // Right: Flip Front/Back Lens Button with Animated 180° Rotation
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            lensRotationAngle += 180f
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            exposureIndex = 0
                        }
                        .testTag("camera_flip_lens"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera Lens",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(animatedLensRotation)
                    )
                }
            }
        }

        // Shutter Screen Flash Effect
        AnimatedVisibility(
            visible = flashScreenEffect,
            enter = fadeIn(animationSpec = tween(40)),
            exit = fadeOut(animationSpec = tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
fun ZoomPresetSelector(
    lensPresets: List<CameraLensPreset>,
    currentZoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onSelectPreset: (CameraLensPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier.testTag("zoom_selector_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Render all detected lenses (e.g. 0.6x, 1x, 2x, 3x, 5x)
            lensPresets.forEach { preset ->
                val isSelected = kotlin.math.abs(currentZoomRatio - preset.ratio) < 0.10f
                ZoomPillButton(
                    label = preset.label,
                    isSelected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    testTag = "zoom_preset_${preset.label.replace(".", "_")}"
                )
            }

            // If current zoom is custom (pinch zoomed away from all presets), display dynamic zoom badge
            val isMatchingAnyPreset = lensPresets.any { kotlin.math.abs(currentZoomRatio - it.ratio) < 0.10f }
            if (!isMatchingAnyPreset) {
                Surface(
                    shape = CircleShape,
                    color = BentoPurplePrimary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = String.format(Locale.US, "%.1fx", currentZoomRatio),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomPillButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFD700) else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "zoomBgColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White,
        animationSpec = tween(durationMillis = 150),
        label = "zoomTextColor"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        modifier = Modifier
            .size(36.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = if (label.length > 3) 10.5.sp else 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CameraShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed || isCapturing) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "shutterScale"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("camera_shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                color = BentoPurplePrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color(0xFFE2E8F0), CircleShape)
            )
        }
    }
}

@Composable
fun CameraGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val gridColor = Color.White.copy(alpha = 0.25f)
        val strokeWidth = 1.dp.toPx()

        // Vertical lines (1/3 and 2/3)
        drawLine(
            color = gridColor,
            start = Offset(width / 3f, 0f),
            end = Offset(width / 3f, height),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = gridColor,
            start = Offset(width * 2f / 3f, 0f),
            end = Offset(width * 2f / 3f, height),
            strokeWidth = strokeWidth
        )

        // Horizontal lines (1/3 and 2/3)
        drawLine(
            color = gridColor,
            start = Offset(0f, height / 3f),
            end = Offset(width, height / 3f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 2f / 3f),
            end = Offset(width, height * 2f / 3f),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun CameraPermissionRequestView(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFFA855F7),
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Camera Access Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allow camera access to capture crisp photos directly within the app and automatically enhance them using Gemini AI.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BentoPurplePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_camera_permission_button")
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Camera Permission", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onClose,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Back to Studio", fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

/**
 * Captures image via CameraX with maximum sensor resolution and 100% JPEG quality.
 * Handles selfie horizontal mirroring losslessly via CameraX metadata.
 * Performs square crop with full ARGB_8888 fidelity and EXIF preservation when 1:1 aspect ratio is selected.
 * Stores directly into standard DCIM/Camera/ MediaStore directory.
 */
private fun takePhotoAndSaveToDcim(
    context: Context,
    imageCapture: ImageCapture,
    isFrontCamera: Boolean,
    mirrorSelfie: Boolean,
    aspectRatio: CameraAspectRatio,
    onSuccess: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val timestamp = System.currentTimeMillis()
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
    val fileName = "IMG_$dateStr"

    // Configure CameraX native metadata for lossless front-camera selfie mirroring
    val metadata = ImageCapture.Metadata().apply {
        isReversedHorizontal = (isFrontCamera && mirrorSelfie)
    }

    // Only 1:1 square aspect ratio requires post-capture bitmap cropping since camera sensors are 4:3 or 16:9
    val shouldCropSquare = (aspectRatio == CameraAspectRatio.RATIO_1_1)

    if (shouldCropSquare) {
        val tempFile = File.createTempFile("temp_cam_", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val sourceExif = ExifInterface(tempFile.absolutePath)
                            val orientation = sourceExif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            val rotationDegrees = when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }

                            val decodeOptions = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                inMutable = true
                            }
                            val originalBitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
                            if (originalBitmap == null) {
                                withContext(Dispatchers.Main) {
                                    onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Failed to decode captured image", null))
                                }
                                return@launch
                            }

                            val matrix = Matrix()
                            if (rotationDegrees != 0f) {
                                matrix.postRotate(rotationDegrees)
                            }

                            val transformedBitmap = if (rotationDegrees != 0f) {
                                Bitmap.createBitmap(
                                    originalBitmap,
                                    0,
                                    0,
                                    originalBitmap.width,
                                    originalBitmap.height,
                                    matrix,
                                    true
                                )
                            } else {
                                originalBitmap
                            }

                            // Apply square crop centered
                            val squareSize = min(transformedBitmap.width, transformedBitmap.height)
                            val xOffset = (transformedBitmap.width - squareSize) / 2
                            val yOffset = (transformedBitmap.height - squareSize) / 2
                            val squareBitmap = Bitmap.createBitmap(
                                transformedBitmap,
                                xOffset,
                                yOffset,
                                squareSize,
                                squareSize
                            )

                            val contentValues = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                }
                            }

                            val savedUri = context.contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )
                            if (savedUri != null) {
                                context.contentResolver.openOutputStream(savedUri)?.use { stream ->
                                    // Save at 100% maximum JPEG quality
                                    squareBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                }
                                squareBitmap.recycle()
                                if (transformedBitmap != originalBitmap && transformedBitmap != squareBitmap) {
                                    transformedBitmap.recycle()
                                }
                                originalBitmap.recycle()

                                // Preserve EXIF tags from original capture into the cropped image
                                try {
                                    context.contentResolver.openFileDescriptor(savedUri, "rw")?.use { pfd ->
                                        val destExif = ExifInterface(pfd.fileDescriptor)
                                        val exifTags = arrayOf(
                                            ExifInterface.TAG_DATETIME,
                                            ExifInterface.TAG_DATETIME_ORIGINAL,
                                            ExifInterface.TAG_DATETIME_DIGITIZED,
                                            ExifInterface.TAG_MAKE,
                                            ExifInterface.TAG_MODEL,
                                            ExifInterface.TAG_FOCAL_LENGTH,
                                            ExifInterface.TAG_FLASH,
                                            ExifInterface.TAG_WHITE_BALANCE,
                                            ExifInterface.TAG_ISO_SPEED_RATINGS,
                                            ExifInterface.TAG_EXPOSURE_TIME,
                                            ExifInterface.TAG_F_NUMBER,
                                            ExifInterface.TAG_COLOR_SPACE
                                        )
                                        for (tag in exifTags) {
                                            val value = sourceExif.getAttribute(tag)
                                            if (value != null) {
                                                destExif.setAttribute(tag, value)
                                            }
                                        }
                                        destExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                                        destExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, squareBitmap.width.toString())
                                        destExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, squareBitmap.height.toString())
                                        destExif.saveAttributes()
                                    }
                                } catch (exifErr: Exception) {
                                    Log.w("CameraCaptureScreen", "Failed to copy EXIF attributes: ${exifErr.message}")
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val updateValues = ContentValues().apply {
                                        put(MediaStore.Images.Media.IS_PENDING, 0)
                                    }
                                    try {
                                        context.contentResolver.update(savedUri, updateValues, null, null)
                                    } catch (e: Exception) {
                                        Log.w("CameraCaptureScreen", "Failed to clear pending flag", e)
                                    }
                                }

                                tempFile.delete()
                                withContext(Dispatchers.Main) {
                                    onSuccess(savedUri)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Failed to insert image to MediaStore", null))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CameraCaptureScreen", "Error processing photo", e)
                            withContext(Dispatchers.Main) {
                                onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, e.message ?: "Processing failed", e))
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
        return
    }

    // Direct capture path for native 4:3 and 16:9 aspect ratios (maximum resolution, zero recompression)
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
        put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).setMetadata(metadata).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = outputFileResults.savedUri
                if (savedUri != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val updateValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        try {
                            context.contentResolver.update(savedUri, updateValues, null, null)
                        } catch (e: Exception) {
                            Log.w("CameraCaptureScreen", "Failed to clear pending flag", e)
                        }
                    }
                    onSuccess(savedUri)
                } else {
                    onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Saved URI is null", null))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}
