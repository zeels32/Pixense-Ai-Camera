package com.pixense.app.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pixense.app.data.analytics.PixenseAnalytics
import com.pixense.app.util.NotificationHelper

/**
 * Service to handle Firebase Cloud Messaging (FCM) incoming push notifications and token refreshes.
 */
class PixenseFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Token: $token")
        PixenseAnalytics.logEvent("fcm_token_refreshed")
        // Ensure topics are subscribed when token refreshes
        NotificationHelper.subscribeToUpdateTopics()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        PixenseAnalytics.logEvent(
            "fcm_message_received",
            mapOf("from" to (remoteMessage.from ?: "unknown"))
        )

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Update Available"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "A new version of Pixense is available. Tap to update from Google Play Store!"

        // Present the notification with the Play Store deep link
        NotificationHelper.showUpdateNotification(
            context = applicationContext,
            title = title,
            body = body
        )
    }

    companion object {
        private const val TAG = "PixenseFCMService"
    }
}
