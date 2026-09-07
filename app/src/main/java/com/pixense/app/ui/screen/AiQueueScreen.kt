package com.pixense.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pixense.app.ui.view.OptimizedThumbnailImage
import com.pixense.app.R
import com.pixense.app.data.model.EnhancementQueueItem
import com.pixense.app.data.model.QueueItemStatus
import com.pixense.app.ui.theme.BentoTheme
import com.pixense.app.ui.viewmodel.CameraAiViewModel
import com.pixense.app.ui.viewmodel.StudioTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiQueueScreen(
    viewModel: CameraAiViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { viewModel.selectTab(StudioTab.STUDIO) }
) {
    val queueItems by viewModel.queueItems.collectAsState()
    val isAutoProcessEnabled by viewModel.isAutoProcessEnabled.collectAsState()

    val pendingCount = queueItems.count { it.status is QueueItemStatus.Pending || it.status is QueueItemStatus.InProgress }
    val completedCount = queueItems.count { it.status is QueueItemStatus.Completed }
    val failedOrStoppedCount = queueItems.count { it.status is QueueItemStatus.Failed || it.status is QueueItemStatus.Stopped }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BentoTheme.colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Queue Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("queue_header_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BentoTheme.colors.cardMuted)
                                    .testTag("queue_back_button")
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
                                    .clip(CircleShape)
                                    .background(BentoTheme.colors.aiBlueContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = BentoTheme.colors.aiBluePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.queue_title),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Sequential Gemini 3.1 Flash Image processing",
                                    fontSize = 12.sp,
                                    color = BentoTheme.colors.textSecondary
                                )
                            }
                        }

                        if (completedCount > 0) {
                            IconButton(
                                onClick = { viewModel.clearCompletedQueue() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BentoTheme.colors.cardMuted)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = "Clear completed",
                                    tint = BentoTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Queue Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueueStatBadge(
                            label = "Active / Pending",
                            count = pendingCount,
                            color = BentoTheme.colors.aiBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        QueueStatBadge(
                            label = "Saved to Gallery",
                            count = completedCount,
                            color = BentoTheme.colors.greenActive,
                            modifier = Modifier.weight(1f)
                        )
                        QueueStatBadge(
                            label = "Stopped / Failed",
                            count = failedOrStoppedCount,
                            color = if (failedOrStoppedCount > 0) Color(0xFFF59E0B) else BentoTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (queueItems.isEmpty()) {
                // Empty Queue State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(BentoTheme.colors.cardMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = BentoTheme.colors.textSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Text(
                            text = "AI Enhancement Queue is Empty",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTheme.colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "When capturing photos rapidly with Auto-Process ON, they queue here automatically and process non-destructively in sequence.",
                            fontSize = 13.sp,
                            color = BentoTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        Button(
                            onClick = { viewModel.enqueueCurrentPhoto() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BentoTheme.colors.aiBluePrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("enqueue_current_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enqueue Latest Camera Photo")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ai_queue_list")
                ) {
                    items(queueItems, key = { it.id }) { item ->
                        QueueItemCard(
                            item = item,
                            onStop = { viewModel.stopQueueItem(item.id) },
                            onRetry = { viewModel.retryQueueItem(item.id) },
                            onCancel = { viewModel.cancelQueueItem(item.id) },
                            onViewInGallery = { viewModel.selectTab(StudioTab.GALLERY) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueStatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QueueItemCard(
    item: EnhancementQueueItem,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onViewInGallery: () -> Unit
) {
    val timeStr = remember(item.queuedAt) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.queuedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_card_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoTheme.colors.cardMuted)
                ) {
                    val displayUri = when (val status = item.status) {
                        is QueueItemStatus.Completed -> status.enhancedUri
                        else -> item.photo.uri
                    }
                    OptimizedThumbnailImage(
                        model = displayUri,
                        contentDescription = item.photo.displayName,
                        targetSizePx = 200,
                        contentScale = ContentScale.Crop,
                        memoryCacheKey = "queue_thumb_${item.id}",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.photo.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BentoTheme.colors.purplePrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = item.preset.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.purplePrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Queued at $timeStr",
                            fontSize = 10.sp,
                            color = BentoTheme.colors.textSecondary
                        )
                    }
                }

                // Status Icon or Action
                when (val status = item.status) {
                    is QueueItemStatus.Pending -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BentoTheme.colors.aiBluePrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "WAITING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTheme.colors.aiBluePrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(BentoTheme.colors.errorBg)
                                    .testTag("stop_pending_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel/Stop",
                                    tint = BentoTheme.colors.errorText,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    is QueueItemStatus.InProgress -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp,
                                color = BentoTheme.colors.aiBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            // Prominent Stop Button
                            Surface(
                                onClick = onStop,
                                shape = RoundedCornerShape(10.dp),
                                color = BentoTheme.colors.errorBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.errorBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .testTag("stop_button_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = BentoTheme.colors.errorText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "STOP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BentoTheme.colors.errorText
                                    )
                                }
                            }
                        }
                    }
                    is QueueItemStatus.Stopped -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "STOPPED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(BentoTheme.colors.aiBlueContainer)
                                    .testTag("resume_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Resume",
                                    tint = BentoTheme.colors.aiBluePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    is QueueItemStatus.Completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = BentoTheme.colors.greenActive,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is QueueItemStatus.Failed -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BentoTheme.colors.errorBg)
                                .testTag("retry_button_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = BentoTheme.colors.errorText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Progress / Status Stage Bar
            when (val status = item.status) {
                is QueueItemStatus.InProgress -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = BentoTheme.colors.aiBluePrimary,
                            trackColor = BentoTheme.colors.aiBlueContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = status.stage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = BentoTheme.colors.aiBluePrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Cancel/Stop",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.errorText,
                                modifier = Modifier
                                    .clickable { onStop() }
                                    .padding(start = 6.dp)
                            )
                        }
                    }
                }
                is QueueItemStatus.Stopped -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = status.reason,
                                fontSize = 11.sp,
                                color = BentoTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                modifier = Modifier.clickable { onRetry() },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "RESUME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                is QueueItemStatus.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BentoTheme.colors.errorBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoTheme.colors.errorBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = BentoTheme.colors.errorText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = status.error,
                                fontSize = 11.sp,
                                color = BentoTheme.colors.errorText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "TAP RETRY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.errorText
                            )
                        }
                    }
                }
                is QueueItemStatus.Completed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✓ Remastered in 4K & Saved to Gallery",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = BentoTheme.colors.greenActive
                        )
                        Surface(
                            modifier = Modifier.clickable { onViewInGallery() },
                            shape = RoundedCornerShape(6.dp),
                            color = BentoTheme.colors.greenActive.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "VIEW IN GALLERY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTheme.colors.greenActive,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
