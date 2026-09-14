package com.pixense.app.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.pixense.app.data.analytics.PixenseAnalytics

/**
 * Manages Google Play In-App Review requests.
 *
 * Google Play imposes strict internal quotas on how often the in-app review dialog
 * is displayed to users. This manager ensures that requests are triggered gracefully
 * without blocking UI or disrupting the user's creative workflow.
 */
object InAppReviewManager {

    private const val TAG = "InAppReviewManager"
    private var reviewManager: ReviewManager? = null
    private var lastReviewRequestTime: Long = 0L
    private const val MIN_REQUEST_INTERVAL_MS = 20_000L // 20-second throttle between calls

    private fun getReviewManager(context: Context): ReviewManager {
        return reviewManager ?: ReviewManagerFactory.create(context.applicationContext).also {
            reviewManager = it
        }
    }

    /**
     * Initiates the in-app review flow on the given [activity].
     *
     * @param activity The foreground Activity required to anchor the in-app review dialog.
     * @param source Description of the trigger source (e.g. "camera_capture", "image_enhanced").
     * @param onComplete Optional callback invoked once the review flow completes or fails.
     */
    fun launchReviewFlow(
        activity: Activity,
        source: String = "unknown",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (now - lastReviewRequestTime < MIN_REQUEST_INTERVAL_MS) {
            Log.d(TAG, "Skipping review request from '$source' due to cooldown throttle.")
            onComplete?.invoke(false)
            return
        }
        lastReviewRequestTime = now

        PixenseAnalytics.logEvent("in_app_review_requested", mapOf("source" to source))
        Log.d(TAG, "Requesting in-app review flow from source: $source")

        val manager = getReviewManager(activity)
        val requestTask = manager.requestReviewFlow()

        requestTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                PixenseAnalytics.logEvent("in_app_review_flow_started", mapOf("source" to source))

                val flowTask = manager.launchReviewFlow(activity, reviewInfo)
                flowTask.addOnCompleteListener {
                    // Google Play API design: flow completion does not report whether the user
                    // submitted a review, dismissed it, or if Google's quota suppressed the UI.
                    // The app must continue normally in all cases.
                    Log.d(TAG, "In-app review flow completed for source: $source")
                    PixenseAnalytics.logEvent("in_app_review_completed", mapOf("source" to source))
                    onComplete?.invoke(true)
                }
            } else {
                val error = task.exception
                Log.w(TAG, "Failed to request in-app review flow for source: $source", error)
                error?.let { PixenseAnalytics.recordException(it, "InAppReview request failed ($source)") }
                onComplete?.invoke(false)
            }
        }
    }
}
