package com.pixense.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixense.app.R
import com.pixense.app.data.model.ThemeMode
import com.pixense.app.ui.theme.BentoTheme
import com.pixense.app.ui.viewmodel.CameraAiViewModel
import com.pixense.app.ui.viewmodel.StudioTab

@Composable
fun SettingsScreen(
    viewModel: CameraAiViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { viewModel.selectTab(StudioTab.STUDIO) }
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isAutoProcessEnabled by viewModel.isAutoProcessEnabled.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        SettingsHeader(onBack = onBack)

        // 1. Appearance & Theme Selection Card
        AppearanceSettingsCard(
            currentTheme = themeMode,
            onSelectTheme = { mode -> viewModel.setThemeMode(mode) }
        )

        // 2. DCIM Camera Monitoring & Auto-Remaster Toggle Card
        /*CameraMonitoringSettingsCard(
            isAutoProcessEnabled = isAutoProcessEnabled,
            isServiceActive = isServiceActive,
            onToggleAutoProcess = { enabled -> viewModel.setAutoProcessEnabled(enabled) }
        )*/

        // 3. AI Remastering Engine Details Card
//        AiEngineDetailsCard()

        // 4. Storage & Output Configuration Card
        StorageInfoCard()

        // Version & App Info Footer
        AppInfoFooter()

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun SettingsHeader(onBack: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(BentoTheme.colors.cardBg)
                .border(1.dp, BentoTheme.colors.border, CircleShape)
                .testTag("settings_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Studio",
                tint = BentoTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BentoTheme.colors.purpleContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = BentoTheme.colors.purplePrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Column {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = BentoTheme.colors.textPrimary
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = BentoTheme.colors.textSecondary
            )
        }
    }
}

@Composable
fun AppearanceSettingsCard(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_appearance_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.purpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = BentoTheme.colors.purplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.settings_appearance_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = "Customize application visual theme",
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOptionItem(
                    title = stringResource(R.string.theme_system),
                    subtitle = stringResource(R.string.theme_system_desc),
                    icon = Icons.Default.BrightnessAuto,
                    isSelected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onSelectTheme(ThemeMode.SYSTEM) }
                )

                ThemeOptionItem(
                    title = stringResource(R.string.theme_light),
                    subtitle = stringResource(R.string.theme_light_desc),
                    icon = Icons.Default.LightMode,
                    isSelected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onSelectTheme(ThemeMode.LIGHT) }
                )

                ThemeOptionItem(
                    title = stringResource(R.string.theme_dark),
                    subtitle = stringResource(R.string.theme_dark_desc),
                    icon = Icons.Default.DarkMode,
                    isSelected = currentTheme == ThemeMode.DARK,
                    onClick = { onSelectTheme(ThemeMode.DARK) }
                )
            }
        }
    }
}

@Composable
fun ThemeOptionItem(
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
            .testTag("settings_theme_option_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BentoTheme.colors.purpleContainer.copy(alpha = 0.55f) else BentoTheme.colors.cardMuted,
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

@Composable
fun CameraMonitoringSettingsCard(
    isAutoProcessEnabled: Boolean,
    isServiceActive: Boolean,
    onToggleAutoProcess: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_camera_monitoring_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.purpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = BentoTheme.colors.purplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_camera_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = "Native camera listener & automation",
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAutoProcessEnabled && isServiceActive) BentoTheme.colors.greenActive.copy(alpha = 0.15f) else BentoTheme.colors.cardMuted
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isAutoProcessEnabled && isServiceActive) BentoTheme.colors.greenActive else BentoTheme.colors.textSecondary)
                        )
                        Text(
                            text = if (isAutoProcessEnabled) "ACTIVE" else "DISABLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAutoProcessEnabled && isServiceActive) BentoTheme.colors.greenActive else BentoTheme.colors.textSecondary
                        )
                    }
                }
            }

            // Main Toggle Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = if (isAutoProcessEnabled) BentoTheme.colors.purpleContainer.copy(alpha = 0.45f) else BentoTheme.colors.cardMuted,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAutoProcessEnabled) BentoTheme.colors.purplePrimary.copy(alpha = 0.35f) else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_process_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_auto_process_desc),
                            fontSize = 11.sp,
                            color = BentoTheme.colors.textSecondary,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = isAutoProcessEnabled,
                        onCheckedChange = onToggleAutoProcess,
                        modifier = Modifier.testTag("settings_auto_process_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BentoTheme.colors.purplePrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = BentoTheme.colors.cardMuted
                        )
                    )
                }
            }

            Text(
                text = if (isAutoProcessEnabled) {
                    "✓ Background listener is running. When you take photos using the system Camera app, Pixense will automatically enhance and save them to your AI Gallery."
                } else {
                    "○ Background listener is off. The service will not run in the background and will not start on app launch. You can still manually enhance photos from Studio or with the in-app camera."
                },
                fontSize = 11.sp,
                color = BentoTheme.colors.textSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun AiEngineDetailsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_ai_engine_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.cardAiBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = BentoTheme.colors.aiBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.settings_ai_engine_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = "Google Gemini Multimodal Vision API",
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = BentoTheme.colors.cardMuted
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Model",
                            fontSize = 12.sp,
                            color = BentoTheme.colors.textSecondary
                        )
                        Text(
                            text = "Gemini 3.1 Flash Image (REST)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.aiBlueText
                        )
                    }

                    HorizontalDivider(color = BentoTheme.colors.border.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pipeline",
                            fontSize = 12.sp,
                            color = BentoTheme.colors.textSecondary
                        )
                        Text(
                            text = "Dual-Stage Scene & Remaster",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BentoTheme.colors.textPrimary
                        )
                    }

                    HorizontalDivider(color = BentoTheme.colors.border.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Supported Scenes",
                            fontSize = 12.sp,
                            color = BentoTheme.colors.textSecondary
                        )
                        Text(
                            text = "Portrait, Low-Light, Food, Nature",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BentoTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_storage_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.cardMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = BentoTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Storage & Albums",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = "Local on-device directory paths",
                        fontSize = 11.sp,
                        color = BentoTheme.colors.textSecondary
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = BentoTheme.colors.cardMuted
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Source Camera Folder",
                            fontSize = 11.sp,
                            color = BentoTheme.colors.textSecondary
                        )
                        Text(
                            text = "DCIM/Camera",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.textPrimary
                        )
                    }
                    HorizontalDivider(color = BentoTheme.colors.border.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AI Remaster Album",
                            fontSize = 11.sp,
                            color = BentoTheme.colors.textSecondary
                        )
                        Text(
                            text = "Pictures/Camera_AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.purplePrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = BentoTheme.colors.purplePrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Pixense AI Studio",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTheme.colors.textPrimary
            )
        }
        Text(
            text = "Version 1.2.0 • Powered by Gemini 3.1 Flash Image",
            fontSize = 11.sp,
            color = BentoTheme.colors.textSecondary
        )
    }
}
