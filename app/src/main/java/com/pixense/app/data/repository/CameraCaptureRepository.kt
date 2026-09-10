package com.pixense.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.pixense.app.data.model.CameraPhoto
import com.pixense.app.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCaptureRepository(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val _latestPhoto = MutableStateFlow<CameraPhoto?>(null)
    val latestPhoto: StateFlow<CameraPhoto?> = _latestPhoto.asStateFlow()

    private val _isServiceActive = MutableStateFlow(true)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    // In-memory set of known enhanced photo URIs, IDs, and file names to prevent loop detection
    private val knownEnhancedUris = java.util.Collections.synchronizedSet(mutableSetOf<Uri>())
    private val knownEnhancedIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
    private val knownEnhancedNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Persistent blacklist of deleted photo IDs, names, and URIs
    private val deletedPrefs = context.getSharedPreferences("Pixense_deleted_photos_repo", Context.MODE_PRIVATE)
    private val deletedPhotoIds = java.util.Collections.synchronizedSet(
        deletedPrefs.getStringSet("deleted_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toMutableSet() ?: mutableSetOf()
    )
    private val deletedPhotoNames = java.util.Collections.synchronizedSet(
        deletedPrefs.getStringSet("deleted_names", emptySet())?.toMutableSet() ?: mutableSetOf()
    )
    private val deletedPhotoUris = java.util.Collections.synchronizedSet(
        deletedPrefs.getStringSet("deleted_uris", emptySet())?.map { Uri.parse(it) }?.toMutableSet() ?: mutableSetOf()
    )

    private fun persistDeletedPhotos() {
        try {
            deletedPrefs.edit()
                .putStringSet("deleted_ids", deletedPhotoIds.map { it.toString() }.toSet())
                .putStringSet("deleted_names", deletedPhotoNames.toSet())
                .putStringSet("deleted_uris", deletedPhotoUris.map { it.toString() }.toSet())
                .apply()
        } catch (e: Exception) {
            Log.e("CameraRepository", "Failed to persist deleted photos prefs", e)
        }
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
    }

    fun markUriAsEnhanced(uri: Uri, name: String? = null) {
        knownEnhancedUris.add(uri)
        try {
            val id = ContentUris.parseId(uri)
            knownEnhancedIds.add(id)
        } catch (_: Exception) {}
        if (name != null) {
            knownEnhancedNames.add(name)
        }
    }

    fun clearLatestPhoto() {
        _latestPhoto.value = null
    }

    suspend fun queryLatestCameraPhoto(): CameraPhoto? = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasStoragePermission(context)) {
            return@withContext null
        }
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.RELATIVE_PATH
                } else {
                    MediaStore.Images.Media.DATA
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                } else {
                    MediaStore.Images.Media.DATA
                }
            )

            // Strictly query Native Camera captures in DCIM/ folder
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?) " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?"
            } else {
                "(${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?) " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
            }
            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%DCIM%", "%Camera%", "AI_Enhanced_%", "AI_%", "%Camera_AI%")
            } else {
                arrayOf("%DCIM%", "%Camera%", "AI_Enhanced_%", "AI_%")
            }

            var photo: CameraPhoto? = null

            // Query native DCIM camera photos
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val candidate = cursorToPhoto(cursor)
                    // Strict validation: must be native camera DCIM path, not enhanced, not deleted
                    if (candidate.isNativeCameraPath &&
                        !candidate.isEnhancedImage &&
                        !knownEnhancedUris.contains(candidate.uri) &&
                        !knownEnhancedIds.contains(candidate.id) &&
                        !knownEnhancedNames.contains(candidate.displayName) &&
                        !deletedPhotoIds.contains(candidate.id) &&
                        !deletedPhotoUris.contains(candidate.uri) &&
                        !deletedPhotoNames.contains(candidate.displayName)
                    ) {
                        photo = candidate
                        break
                    }
                }
            }

            if (photo != null) {
                _latestPhoto.value = photo
            }
            photo
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error querying latest camera photo", e)
            null
        }
    }

    suspend fun queryAllDcimPhotos(): List<CameraPhoto> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasStoragePermission(context)) {
            return@withContext emptyList()
        }
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.RELATIVE_PATH
                } else {
                    MediaStore.Images.Media.DATA
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                } else {
                    MediaStore.Images.Media.DATA
                }
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?) " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
            } else {
                "(${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?) " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
            }
            val selectionArgs = arrayOf("%DCIM%", "%Camera%", "AI_Enhanced_%", "AI_%")

            val list = mutableListOf<CameraPhoto>()
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val candidate = cursorToPhoto(cursor)
                    if (candidate.isNativeCameraPath &&
                        !candidate.isEnhancedImage &&
                        !knownEnhancedUris.contains(candidate.uri) &&
                        !knownEnhancedIds.contains(candidate.id) &&
                        !knownEnhancedNames.contains(candidate.displayName) &&
                        !deletedPhotoIds.contains(candidate.id) &&
                        !deletedPhotoUris.contains(candidate.uri) &&
                        !deletedPhotoNames.contains(candidate.displayName)
                    ) {
                        list.add(candidate)
                    }
                }
            }
            list
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error querying all DCIM photos", e)
            emptyList()
        }
    }

    suspend fun queryDcimPhotosPaged(offset: Int, limit: Int): List<CameraPhoto> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasStoragePermission(context)) {
            return@withContext emptyList()
        }
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.RELATIVE_PATH
                } else {
                    MediaStore.Images.Media.DATA
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                } else {
                    MediaStore.Images.Media.DATA
                }
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?) " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
            } else {
                "(${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?) " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ? " +
                "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
            }
            val selectionArgs = arrayOf("%DCIM%", "%Camera%", "AI_Enhanced_%", "AI_%")

            val list = mutableListOf<CameraPhoto>()
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                var skipped = 0
                while (cursor.moveToNext() && list.size < limit) {
                    val candidate = cursorToPhoto(cursor)
                    if (candidate.isNativeCameraPath &&
                        !candidate.isEnhancedImage &&
                        !knownEnhancedUris.contains(candidate.uri) &&
                        !knownEnhancedIds.contains(candidate.id) &&
                        !knownEnhancedNames.contains(candidate.displayName) &&
                        !deletedPhotoIds.contains(candidate.id) &&
                        !deletedPhotoUris.contains(candidate.uri) &&
                        !deletedPhotoNames.contains(candidate.displayName)
                    ) {
                        if (skipped < offset) {
                            skipped++
                        } else {
                            list.add(candidate)
                        }
                    }
                }
            }
            list
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error querying paged DCIM photos", e)
            emptyList()
        }
    }

    private fun cursorToPhoto(cursor: android.database.Cursor): CameraPhoto {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

        val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            -1
        }
        val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

        val id = cursor.getLong(idCol)
        val name = cursor.getString(nameCol) ?: "IMG_${System.currentTimeMillis()}.jpg"
        val dateTaken = if (dateTakenCol != -1 && !cursor.isNull(dateTakenCol)) {
            cursor.getLong(dateTakenCol)
        } else {
            cursor.getLong(dateAddedCol) * 1000
        }
        val size = cursor.getLong(sizeCol)
        val width = cursor.getInt(widthCol)
        val height = cursor.getInt(heightCol)
        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

        val relativePath = if (relativePathCol != -1 && !cursor.isNull(relativePathCol)) {
            cursor.getString(relativePathCol)
        } else if (dataCol != -1 && !cursor.isNull(dataCol)) {
            val dataPath = cursor.getString(dataCol) ?: ""
            if (dataPath.contains("/DCIM/")) "DCIM/" + dataPath.substringAfter("/DCIM/") else dataPath
        } else {
            "DCIM/Camera"
        }

        val bucketDisplayName = if (bucketCol != -1 && !cursor.isNull(bucketCol)) {
            cursor.getString(bucketCol)
        } else {
            "Camera"
        }

        return CameraPhoto(
            id = id,
            uri = uri,
            displayName = name,
            dateTaken = dateTaken,
            sizeBytes = size,
            width = width,
            height = height,
            mimeType = mime,
            relativePath = relativePath,
            bucketDisplayName = bucketDisplayName
        )
    }

    fun setLatestPhoto(photo: CameraPhoto) {
        _latestPhoto.value = photo
    }

    suspend fun deletePhoto(photo: CameraPhoto): Boolean = withContext(Dispatchers.IO) {
        // 1. Immediately blacklist so it never appears again in queries or UI
        deletedPhotoIds.add(photo.id)
        deletedPhotoNames.add(photo.displayName)
        deletedPhotoUris.add(photo.uri)
        persistDeletedPhotos()

        var deleted = false

        // 2. Sample photo / cache removal
        if (photo.isSample) {
            try {
                val sampleFile = File(context.cacheDir, "sample_camera_photo.jpg")
                if (sampleFile.exists()) {
                    sampleFile.delete()
                }
                deleted = true
            } catch (_: Exception) {}
        }

        // 3. File scheme removal
        if (photo.uri.scheme == "file" || (photo.uri.path != null && !photo.uri.path!!.startsWith("/external/"))) {
            try {
                val file = java.io.File(photo.uri.path ?: "")
                if (file.exists() && file.delete()) {
                    deleted = true
                }
            } catch (_: Exception) {}
        }

        // 4. Direct ContentResolver delete
        try {
            val rows = contentResolver.delete(photo.uri, null, null)
            if (rows > 0) deleted = true
        } catch (e: Exception) {
            Log.w("CameraRepository", "Failed to delete photo from MediaStore: ${photo.uri}", e)
        }

        // 5. Query DATA path from MediaStore and delete physical file
        try {
            contentResolver.query(
                photo.uri,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (dataIdx != -1) {
                        val filePath = cursor.getString(dataIdx)
                        if (!filePath.isNullOrBlank()) {
                            val f = java.io.File(filePath)
                            if (f.exists() && f.delete()) {
                                deleted = true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 6. Refresh latest photo
        if (_latestPhoto.value?.id == photo.id || _latestPhoto.value?.uri == photo.uri) {
            _latestPhoto.value = queryLatestCameraPhoto()
        }

        true
    }

    suspend fun queryPhotoByUri(uri: Uri): CameraPhoto? = withContext(Dispatchers.IO) {
        // 1. First attempt standard MediaStore projection query
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.RELATIVE_PATH
                } else {
                    MediaStore.Images.Media.DATA
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                } else {
                    MediaStore.Images.Media.DATA
                }
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return@withContext cursorToPhoto(cursor)
                }
            }
        } catch (e: Exception) {
            Log.d("CameraRepository", "MediaStore projection not supported for URI: $uri (e.g. Photo Picker / Document provider). Using generic openable query.")
        }

        // 2. Safe fallback query for modern Android Photo Picker and Document Provider URIs
        try {
            var displayName = "IMG_${System.currentTimeMillis()}.jpg"
            var sizeBytes = 0L

            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("CameraRepository", "Could not read OpenableColumns for $uri: ${e.message}")
            }

            // Decode image bounds safely without reading full bitmap into memory
            var width = 1920
            var height = 1080
            var mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        width = opts.outWidth
                        height = opts.outHeight
                    }
                    if (!opts.outMimeType.isNullOrBlank()) {
                        mimeType = opts.outMimeType
                    }
                }
            } catch (e: Exception) {
                Log.d("CameraRepository", "Could not decode bounds for $uri: ${e.message}")
            }

            val id = try { ContentUris.parseId(uri) } catch (_: Exception) { System.currentTimeMillis() }
            return@withContext CameraPhoto(
                id = id,
                uri = uri,
                displayName = displayName,
                dateTaken = System.currentTimeMillis(),
                sizeBytes = sizeBytes,
                width = width,
                height = height,
                mimeType = mimeType,
                relativePath = "Photos",
                bucketDisplayName = "Photo Picker"
            )
        } catch (e: Exception) {
            Log.e("CameraRepository", "Failed to query photo by URI: $uri", e)
            null
        }
    }

    suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "Failed to decode bitmap from URI: $uri", e)
            null
        }
    }

    suspend fun saveEnhancedBitmap(bitmap: Bitmap, originalName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cleanName = originalName.substringBeforeLast(".")
            val newFileName = "AI_Enhanced_${cleanName}_$timestamp.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newFileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera_AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            // Immediately mark as enhanced so content observers ignore this file
            markUriAsEnhanced(uri, newFileName)

            contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error saving enhanced image", e)
            null
        }
    }

    suspend fun createSampleCameraPhoto(): CameraPhoto = withContext(Dispatchers.IO) {
        // Generate a beautiful scenic sample photo bitmap for instant testing
        val width = 1200
        val height = 900
        val sampleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sampleBitmap)

        // Draw scenic sunset sky gradient
        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.65f,
                intArrayOf(Color.parseColor("#1B263B"), Color.parseColor("#774936"), Color.parseColor("#CD7B32"), Color.parseColor("#E09F3E")),
                floatArrayOf(0f, 0.4f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.65f, skyPaint)

        // Glowing Sun
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF3B0")
            setShadowLayer(40f, 0f, 0f, Color.parseColor("#E09F3E"))
        }
        canvas.drawCircle(width * 0.5f, height * 0.42f, 65f, sunPaint)

        // Mountain Ranges
        val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#335C67")
        }
        val mountainPath = android.graphics.Path().apply {
            moveTo(0f, height * 0.65f)
            lineTo(width * 0.2f, height * 0.48f)
            lineTo(width * 0.45f, height * 0.58f)
            lineTo(width * 0.75f, height * 0.45f)
            lineTo(width.toFloat(), height * 0.62f)
            lineTo(width.toFloat(), height * 0.65f)
            close()
        }
        canvas.drawPath(mountainPath, mountainPaint)

        // Lake Water
        val waterPaint = Paint().apply {
            shader = LinearGradient(
                0f, height * 0.65f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor("#264653"), Color.parseColor("#1D3557")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.65f, width.toFloat(), height.toFloat(), waterPaint)

        // Save to cache as sample
        val sampleFile = File(context.cacheDir, "sample_camera_photo.jpg")
        FileOutputStream(sampleFile).use { out ->
            sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val sampleUri = Uri.fromFile(sampleFile)
        val photo = CameraPhoto(
            id = -1L,
            uri = sampleUri,
            displayName = "CAMERA_SAMPLE_2026.jpg",
            dateTaken = System.currentTimeMillis(),
            sizeBytes = sampleFile.length(),
            width = width,
            height = height,
            mimeType = "image/jpeg",
            relativePath = "DCIM/Camera (Demo)",
            isSample = true
        )
        _latestPhoto.value = photo
        photo
    }

    companion object {
        @Volatile
        private var instance: CameraCaptureRepository? = null

        fun getInstance(context: Context): CameraCaptureRepository {
            return instance ?: synchronized(this) {
                instance ?: CameraCaptureRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
