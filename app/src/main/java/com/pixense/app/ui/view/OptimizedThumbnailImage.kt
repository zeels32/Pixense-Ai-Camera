package com.pixense.app.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.pixense.app.ui.theme.BentoTheme

/**
 * High-performance thumbnail image component designed for grids and lists.
 *
 * Downsamples heavy bitmaps (e.g. 12MP–108MP camera captures) using Coil's inexact
 * precision so BitmapFactory decodes directly into downscaled memory via inSampleSize,
 * reducing memory usage by 90–98% compared to loading full-resolution images.
 */
@Composable
fun OptimizedThumbnailImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetSizePx: Int = 400,
    memoryCacheKey: String? = null,
    diskCacheKey: String? = null
) {
    val context = LocalContext.current

    val imageRequest = remember(model, targetSizePx, memoryCacheKey, diskCacheKey) {
        if (model is ImageRequest) {
            model
        } else {
            ImageRequest.Builder(context)
                .data(model)
                // Downsample to thumbnail dimensions: prevents decoding multi-megabyte bitmaps
                .size(targetSizePx, targetSizePx)
                // Precision.INEXACT allows BitmapFactory to use power-of-two inSampleSize downsampling
                .precision(Precision.INEXACT)
                .scale(Scale.FILL)
                .crossfade(150)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .apply {
                    if (memoryCacheKey != null) memoryCacheKey(memoryCacheKey)
                    if (diskCacheKey != null) diskCacheKey(diskCacheKey)
                }
                .build()
        }
    }

    Box(
        modifier = modifier.background(BentoTheme.colors.cardMuted)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}
