package com.pixense.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pixense.app.data.analytics.PixenseAnalytics
import com.pixense.app.data.model.ThemeMode
import com.pixense.app.service.CameraCaptureService
import com.pixense.app.ui.screen.CameraAiScreen
import com.pixense.app.ui.theme.MyApplicationTheme
import com.pixense.app.ui.viewmodel.CameraAiViewModel
import com.pixense.app.util.InAppReviewManager
import com.pixense.app.util.PermissionUtils
import com.pixense.app.util.PlayStoreDeepLinkHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: CameraAiViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        PixenseAnalytics.logEvent("notification_permission_result", mapOf("granted" to isGranted))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle Play Store deep link if launched via Update App push notification
        handleUpdateAppIntent(intent)

        // Request notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionUtils.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Listen for In-App Review prompts triggered by camera capture or photo enhancement
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.reviewPromptTrigger.collect { source ->
                    InAppReviewManager.launchReviewFlow(this@MainActivity, source)
                }
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                // If permissions were already granted previously, initialize background service & latest photo
                LaunchedEffect(Unit) {
                    if (PermissionUtils.hasStoragePermission(this@MainActivity)) {
                        if (viewModel.isAutoProcessEnabled.value) {
                            CameraCaptureService.start(this@MainActivity)
                        }
                        viewModel.refreshLatestPhoto()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraAiScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionUtils.hasStoragePermission(this)) {
            viewModel.refreshLatestPhoto()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateAppIntent(intent)
        if (PermissionUtils.hasStoragePermission(this)) {
            viewModel.refreshLatestPhoto()
        }
    }

    private fun handleUpdateAppIntent(intent: Intent?) {
        if (intent == null) return
        val isOpenPlayStore = intent.getBooleanExtra(PlayStoreDeepLinkHelper.EXTRA_OPEN_PLAY_STORE, false)
            || intent.action == PlayStoreDeepLinkHelper.ACTION_UPDATE_APP
            || intent.getStringExtra("action") == "update_app"
            || intent.getStringExtra("click_action") == "update_app"

        if (isOpenPlayStore) {
            intent.removeExtra(PlayStoreDeepLinkHelper.EXTRA_OPEN_PLAY_STORE)
            PlayStoreDeepLinkHelper.openPlayStore(this)
        }
    }
}
