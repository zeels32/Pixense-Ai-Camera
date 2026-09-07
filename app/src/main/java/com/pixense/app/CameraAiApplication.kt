package com.pixense.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

import com.pixense.app.data.analytics.PixenseAnalytics

class CameraAiApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        PixenseAnalytics.init(this)
        PixenseAnalytics.setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
        PixenseAnalytics.setCustomKey("app_version_code", BuildConfig.VERSION_CODE)
        PixenseAnalytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
        PixenseAnalytics.logBreadcrumb("CameraAiApplication onCreate completed")
        PixenseAnalytics.logEvent("app_open")
        /*try {
            CameraCaptureService.start(this)
            Log.d("CameraAiApplication", "CameraCaptureService auto-started")
        } catch (e: Exception) {
            Log.w("CameraAiApplication", "Background service will start after runtime permissions are granted", e)
        }*/
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Allocate 25% of available app memory to Coil image cache
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                    .build()
            }
            // Hardware bitmaps render directly on the GPU, avoiding JVM heap allocations
            .allowHardware(true)
            // Allow RGB_565 for opaque photos (uses 50% less RAM than ARGB_8888)
            .allowRgb565(true)
            .crossfade(true)
            .crossfade(150)
            .respectCacheHeaders(false)
            .build()
    }
}

