package com.pixense.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.pixense.app.R
import com.pixense.app.data.analytics.PixenseAnalytics

/**
 * Handles notification channels, topics, and local display for update notifications.
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_UPDATE_APP = "update_app_channel"
    const val CHANNEL_UPDATE_APP_NAME = "Update app"
    const val TOPIC_UPDATE_APP = "update_app"
    const val TOPIC_ALL = "all"
    const val NOTIFICATION_ID_UPDATE = 3001

    /**
     * Initializes the "Update app" notification channel on Android 8.0+ (API 26+).
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val channel = NotificationChannel(
                    CHANNEL_UPDATE_APP,
                    CHANNEL_UPDATE_APP_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about new app updates and features"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel '$CHANNEL_UPDATE_APP_NAME' created.")
            }
        }
    }

    /**
     * Subscribes the device to Firebase Cloud Messaging topics for update broadcasts.
     */
    fun subscribeToUpdateTopics() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_UPDATE_APP)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Successfully subscribed to FCM topic: $TOPIC_UPDATE_APP")
                        PixenseAnalytics.logEvent("fcm_subscribed_topic", mapOf("topic" to TOPIC_UPDATE_APP))
                    } else {
                        Log.w(TAG, "Failed to subscribe to FCM topic: $TOPIC_UPDATE_APP", task.exception)
                    }
                }

            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Successfully subscribed to FCM topic: $TOPIC_ALL")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to FCM topics", e)
        }
    }

    /**
     * Builds and presents the Update App notification with a direct Play Store deep link.
     */
    fun showUpdateNotification(context: Context, title: String?, body: String?) {
        createNotificationChannels(context)

        // Android 13+ runtime permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show update notification.")
                return
            }
        }

        val pendingIntent = PlayStoreDeepLinkHelper.createUpdateAppPendingIntent(context)

        val notificationTitle = title?.takeIf { it.isNotBlank() } ?: "Update Available"
        val notificationBody = body?.takeIf { it.isNotBlank() }
            ?: "A new version of Pixense is available. Tap to update from the Google Play Store!"

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_APP)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
            PixenseAnalytics.logEvent("update_notification_displayed")
            Log.d(TAG, "Update notification displayed with Play Store deep link.")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while notifying update notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying update notification", e)
        }
    }
}
