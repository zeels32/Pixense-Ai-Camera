package com.pixense.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixense.app.data.model.ThemeMode
import com.pixense.app.service.CameraCaptureService
import com.pixense.app.ui.screen.CameraAiScreen
import com.pixense.app.ui.theme.MyApplicationTheme
import com.pixense.app.ui.viewmodel.CameraAiViewModel
import com.pixense.app.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val viewModel: CameraAiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        if (PermissionUtils.hasStoragePermission(this)) {
            viewModel.refreshLatestPhoto()
        }
    }
}

