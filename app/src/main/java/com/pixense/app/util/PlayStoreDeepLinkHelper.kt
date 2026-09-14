package com.pixense.app.util

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.pixense.app.MainActivity
import com.pixense.app.data.analytics.PixenseAnalytics

/**
 * Helper to handle deep linking into the Google Play Store for app updates.
 */
object PlayStoreDeepLinkHelper {

    private const val TAG = "PlayStoreDeepLink"
    const val EXTRA_OPEN_PLAY_STORE = "com.pixense.app.OPEN_PLAY_STORE"
    const val ACTION_UPDATE_APP = "com.pixense.app.ACTION_UPDATE_APP"

    /**
     * Creates a PendingIntent that opens the app's Google Play Store listing when tapped.
     */
    fun createUpdateAppPendingIntent(context: Context, requestCode: Int = 2001): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_UPDATE_APP
            putExtra(EXTRA_OPEN_PLAY_STORE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags)
    }

    /**
     * Directly launches the Google Play Store on the app's page, with browser fallback.
     */
    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        PixenseAnalytics.logEvent("playstore_deeplink_opened", mapOf("package_name" to packageName))

        try {
            // Try opening via Play Store app directly
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(marketIntent)
            Log.d(TAG, "Opened Play Store app for $packageName")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Play Store app not found, falling back to browser", e)
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store in browser", e2)
                PixenseAnalytics.recordException(e2, "Failed to open Play Store link")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching Play Store", e)
            PixenseAnalytics.recordException(e, "Unexpected error launching Play Store")
        }
    }
}
