package com.pixense.app.data.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object PixenseAnalytics {
    private const val TAG = "PixenseAnalytics"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var firebaseCrashlytics: FirebaseCrashlytics? = null

    /**
     * Initializes Firebase Analytics and Firebase Crashlytics.
     * Safe to call multiple times or from Application.onCreate().
     */
    fun init(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseCrashlytics = FirebaseCrashlytics.getInstance()
            firebaseCrashlytics?.isCrashlyticsCollectionEnabled = true
            Log.d(TAG, "PixenseAnalytics initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Firebase Analytics / Crashlytics", e)
        }
    }

    /**
     * Logs a custom or standard analytics event with optional key-value parameters.
     * Also records a breadcrumb to Firebase Crashlytics.
     */
    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap()) {
        try {
            val bundle = if (params.isNotEmpty()) {
                Bundle().apply {
                    for ((key, value) in params) {
                        when (value) {
                            null -> putString(key, "null")
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Double -> putDouble(key, value)
                            is Float -> putDouble(key, value.toDouble())
                            is Boolean -> putBoolean(key, value)
                            else -> putString(key, value.toString())
                        }
                    }
                }
            } else null

            firebaseAnalytics?.logEvent(eventName, bundle)

            // Record breadcrumb in Crashlytics
            val paramStr = if (params.isNotEmpty()) " with params: $params" else ""
            firebaseCrashlytics?.log("[Event] $eventName$paramStr")
            Log.d(TAG, "[Analytics Event] $eventName$paramStr")
        } catch (e: Exception) {
            Log.w(TAG, "Error logging event: $eventName", e)
        }
    }

    /**
     * Tracks screen views for user flow analytics.
     */
    fun logScreenView(screenName: String, screenClass: String? = null) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                if (screenClass != null) {
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
                }
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            firebaseCrashlytics?.log("[ScreenView] $screenName")
            setCustomKey("current_screen", screenName)
            Log.d(TAG, "[Screen View] $screenName")
        } catch (e: Exception) {
            Log.w(TAG, "Error logging screen view: $screenName", e)
        }
    }

    /**
     * Sets user property for behavioral cohort analysis.
     */
    fun setUserProperty(name: String, value: String?) {
        try {
            firebaseAnalytics?.setUserProperty(name, value)
            if (value != null) {
                firebaseCrashlytics?.setCustomKey("user_prop_$name", value)
            }
            Log.d(TAG, "[User Property] $name = $value")
        } catch (e: Exception) {
            Log.w(TAG, "Error setting user property: $name", e)
        }
    }

    /**
     * Sets Crashlytics custom key to provide diagnostic context in crash reports.
     */
    fun setCustomKey(key: String, value: Any) {
        try {
            when (value) {
                is String -> firebaseCrashlytics?.setCustomKey(key, value)
                is Boolean -> firebaseCrashlytics?.setCustomKey(key, value)
                is Int -> firebaseCrashlytics?.setCustomKey(key, value)
                is Long -> firebaseCrashlytics?.setCustomKey(key, value)
                is Float -> firebaseCrashlytics?.setCustomKey(key, value)
                is Double -> firebaseCrashlytics?.setCustomKey(key, value)
                else -> firebaseCrashlytics?.setCustomKey(key, value.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting Crashlytics custom key: $key", e)
        }
    }

    /**
     * Records a non-fatal exception to Firebase Crashlytics and logs an analytics error event.
     */
    fun recordException(throwable: Throwable, message: String? = null) {
        try {
            if (message != null) {
                firebaseCrashlytics?.log("Exception context: $message")
            }
            firebaseCrashlytics?.recordException(throwable)

            val params = mutableMapOf<String, Any?>("error_class" to throwable.javaClass.simpleName)
            throwable.message?.let { params["error_message"] = it.take(100) }
            message?.let { params["context"] = it.take(100) }
            logEvent("app_exception_recorded", params)

            Log.e(TAG, "Recorded non-fatal exception: ${throwable.message}", throwable)
        } catch (e: Exception) {
            Log.w(TAG, "Error recording exception", e)
        }
    }

    /**
     * Appends a log line / breadcrumb to Crashlytics reports.
     */
    fun logBreadcrumb(message: String) {
        try {
            firebaseCrashlytics?.log(message)
            Log.d(TAG, "[Breadcrumb] $message")
        } catch (e: Exception) {
            Log.w(TAG, "Error adding breadcrumb", e)
        }
    }
}
