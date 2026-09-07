package com.pixense.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pixense.app.MainActivity
import com.pixense.app.R
import com.pixense.app.data.repository.CameraCaptureRepository
import com.pixense.app.data.repository.EnhancementQuotaManager
import com.pixense.app.data.analytics.PixenseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraCaptureService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: CameraCaptureRepository
    private var contentObserver: ContentObserver? = null
    private var lastSeenPhotoId: Long = -1L
    private var debounceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = CameraCaptureRepository.getInstance(applicationContext)
        createNotificationChannels()
        startForegroundServiceNotification()
        registerCameraContentObserver()
        repository.setServiceActive(true)
        Log.d(TAG, "CameraCaptureService created and observer registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun registerCameraContentObserver() {
        val handler = Handler(Looper.getMainLooper())
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "MediaStore change observed at: $uri")
                
                // Debounce rapid changes during photo file flush
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(700)
                    checkNewCameraCapture()
                }
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register content observer", e)
            PixenseAnalytics.recordException(e, "Failed to register content observer in CameraCaptureService")
        }
    }

    private suspend fun checkNewCameraCapture() {
        val photo = repository.queryLatestCameraPhoto() ?: return

        // 1. Strictly ignore if this photo is an already enhanced image
        if (photo.isEnhancedImage) {
            Log.d(TAG, "Ignoring already enhanced photo: ${photo.displayName}")
            return
        }

        // 2. Strictly verify that the photo is from native camera path (DCIM)
        if (!photo.isNativeCameraPath) {
            Log.d(TAG, "Ignoring non-DCIM photo: ${photo.displayName}, path: ${photo.relativePath}")
            return
        }

        // 3. Prevent duplicate triggers on same photo id
        if (photo.id == lastSeenPhotoId) {
            return
        }

        val queueManager = com.pixense.app.data.repository.AiQueueManager.getInstance(applicationContext)

        // 4. Check if already processed or recorded in database
        if (queueManager.isPhotoAlreadyProcessedOrEnhanced(photo)) {
            Log.d(TAG, "Photo already enhanced or in database (${photo.displayName}), skipping auto-enhance.")
            lastSeenPhotoId = photo.id
            return
        }

        if (lastSeenPhotoId != -1L) {
            lastSeenPhotoId = photo.id
            Log.d(TAG, "New original camera photo captured from DCIM! ID: ${photo.id}, name: ${photo.displayName}")
            val isAutoEnabled = queueManager.isAutoProcessEnabled.value

            // STRICT: Only native DCIM camera captures auto-process once
            if (isAutoEnabled) {
                val quotaManager = EnhancementQuotaManager.getInstance(applicationContext)
                if (quotaManager.hasAvailableEntitlement()) {
                    queueManager.enqueue(photo)
                    showNewPhotoNotification(photo.displayName, isAutoProcessing = true)
                } else {
                    showQuotaLimitNotification(applicationContext, photo.displayName)
                }
            } else {
                showNewPhotoNotification(photo.displayName, isAutoProcessing = false)
            }
        } else {
            lastSeenPhotoId = photo.id
        }
    }

    private fun startForegroundServiceNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(getString(R.string.service_running_desc))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showNewPhotoNotification(photoName: String, isAutoProcessing: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NEW_PHOTO_TRIGGERED, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val desc = if (isAutoProcessing) {
            "Captured \"$photoName\" • Auto-restoring with Gemini AI…"
        } else {
            "Captured \"$photoName\" • Tap to enhance with AI"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(getString(R.string.new_photo_detected_title))
            .setContentText(desc)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "Camera Listener Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service monitoring for native camera captures"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "New Photo Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a new camera photo is captured"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(monitorChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        serviceJob.cancel()
        repository.setServiceActive(false)
        Log.d(TAG, "CameraCaptureService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CameraCaptureService"
        const val CHANNEL_MONITOR_ID = "camera_ai_monitor_channel"
        const val CHANNEL_ALERT_ID = "camera_ai_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_CAMERA_SERVICE"
        const val EXTRA_NEW_PHOTO_TRIGGERED = "extra_new_photo_triggered"

        fun showQuotaLimitNotification(context: Context, photoName: String) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setContentTitle("Enhance with Gemini AI")
                .setContentText("Tap to watch an ad and enhance \"$photoName\".")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        }

        fun start(context: Context) {
            val intent = Intent(context, CameraCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CameraCaptureService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
