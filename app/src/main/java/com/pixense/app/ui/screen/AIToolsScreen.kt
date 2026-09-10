package com.pixense.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixense.app.data.analytics.PixenseAnalytics
import com.pixense.app.data.model.AiPhotoOperation
import com.pixense.app.data.model.QueueItemStatus
import com.pixense.app.ui.theme.BentoTheme
import com.pixense.app.ui.viewmodel.CameraAiViewModel
import com.pixense.app.ui.viewmodel.StudioTab

/**
 * AI Tools Dashboard Screen:
 * Default start screen and central entry point for Pixense AI photo capabilities.
 * Features 4 primary AI tools:
 * 1. ✨ Fix: Automatically improve your photo with AI
 * 2. 🧹 Clean: Remove unwanted objects and distractions
 * 3. 🔍 Unblur: Recover clarity and detail
 * 4. 🕰️ Restore: Restore old, faded and damaged photos
 *
 * User flow:
 * Selecting a feature card -> navigates to StudioTabScreen (device gallery photos) ->
 * user taps a photo -> full-screen preview overlay appears with feature badge ->
 * user taps Gemini enhance button for the selected feature.
 */
@Composable
fun AIToolsScreen(
    viewModel: CameraAiViewModel,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectOperation: (AiPhotoOperation) -> Unit = { op ->
        viewModel.selectOperation(op)
        viewModel.selectTab(StudioTab.STUDIO)
    },
    modifier: Modifier = Modifier
) {
    val queueItems by viewModel.queueItems.collectAsStateWithLifecycle()
    val pendingQueueCount = queueItems.count { it.status is QueueItemStatus.Pending || it.status is QueueItemStatus.InProgress }

    LaunchedEffect(Unit) {
        PixenseAnalytics.logScreenView("AIToolsScreen")
        PixenseAnalytics.logEvent("ai_tools_opened")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BentoTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ai_tools_screen")
    ) {
        // Top Bento Header (Pixense brand + Queue badge button + Settings button)
        BentoHeader(
            onOpenQueue = onOpenQueue,
            onOpenSettings = onOpenSettings,
            pendingQueueCount = pendingQueueCount
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Screen Header Title & Subtitle
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "AI Photo Tools",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BentoTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose an AI tool to remaster photos from your gallery",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = BentoTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Queue Banner (visible when enhancements are actively processing)
        if (pendingQueueCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenQueue() }
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4 Primary AI Feature Cards in a 2x2 Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Fix & Clean
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AiFeatureCard(
                    operation = AiPhotoOperation.FIX,
                    onClick = {
                        viewModel.selectOperation(AiPhotoOperation.FIX)
                        PixenseAnalytics.logAiToolSelected(AiPhotoOperation.FIX.analyticsTag)
                        onSelectOperation(AiPhotoOperation.FIX)
                    },
                    accentColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f).testTag("ai_card_fix")
                )
                AiFeatureCard(
                    operation = AiPhotoOperation.CLEAN,
                    onClick = {
                        viewModel.selectOperation(AiPhotoOperation.CLEAN)
                        PixenseAnalytics.logAiToolSelected(AiPhotoOperation.CLEAN.analyticsTag)
                        onSelectOperation(AiPhotoOperation.CLEAN)
                    },
                    accentColor = Color(0xFF06B6D4),
                    modifier = Modifier.weight(1f).testTag("ai_card_clean")
                )
            }

            // Row 2: Unblur & Restore
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AiFeatureCard(
                    operation = AiPhotoOperation.UNBLUR,
                    onClick = {
                        viewModel.selectOperation(AiPhotoOperation.UNBLUR)
                        PixenseAnalytics.logAiToolSelected(AiPhotoOperation.UNBLUR.analyticsTag)
                        onSelectOperation(AiPhotoOperation.UNBLUR)
                    },
                    accentColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f).testTag("ai_card_unblur")
                )
                AiFeatureCard(
                    operation = AiPhotoOperation.RESTORE,
                    onClick = {
                        viewModel.selectOperation(AiPhotoOperation.RESTORE)
                        PixenseAnalytics.logAiToolSelected(AiPhotoOperation.RESTORE.analyticsTag)
                        onSelectOperation(AiPhotoOperation.RESTORE)
                    },
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f).testTag("ai_card_restore")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bento Workflow Guide Card
        BentoWorkflowGuideCard(
            onSelectFix = {
                viewModel.selectOperation(AiPhotoOperation.FIX)
                PixenseAnalytics.logAiToolSelected(AiPhotoOperation.FIX.analyticsTag)
                onSelectOperation(AiPhotoOperation.FIX)
            }
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Bento card representing an AI photo operation with title, description, and custom emoji icon.
 */
@Composable
fun AiFeatureCard(
    operation: AiPhotoOperation,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = accentColor.copy(alpha = 0.15f)
            )
            .border(1.dp, BentoTheme.colors.border, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon Pill with Gradient Tint & Action Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.28f),
                                    accentColor.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = operation.iconEmoji,
                        fontSize = 22.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BentoTheme.colors.cardMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Select",
                        tint = BentoTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title & Description
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = operation.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = operation.description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = BentoTheme.colors.textSecondary,
                    lineHeight = 16.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Bento guide card explaining the seamless 3-step workflow.
 */
@Composable
fun BentoWorkflowGuideCard(
    onSelectFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BentoTheme.colors.border, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BentoTheme.colors.purplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "How It Works",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary
                    )
                    Text(
                        text = "Powered by Google Gemini 4K AI",
                        fontSize = 12.sp,
                        color = BentoTheme.colors.purplePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WorkflowStepRow(
                    stepNumber = "1",
                    title = "Choose an AI Tool",
                    subtitle = "Select Fix, Clean, Unblur, or Restore above"
                )
                WorkflowStepRow(
                    stepNumber = "2",
                    title = "Pick Any Gallery Photo",
                    subtitle = "Browse your camera roll and device photos in Studio"
                )
                WorkflowStepRow(
                    stepNumber = "3",
                    title = "Enhance & Compare",
                    subtitle = "Tap enhance for 4K AI remastering and before/after slider"
                )
            }
        }
    }
}

@Composable
private fun WorkflowStepRow(
    stepNumber: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(BentoTheme.colors.cardMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTheme.colors.textPrimary
            )
        }
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTheme.colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = BentoTheme.colors.textSecondary
            )
        }
    }
}
