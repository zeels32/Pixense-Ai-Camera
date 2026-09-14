package com.pixense.app.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.pixense.app.BuildConfig
import com.pixense.app.data.analytics.PixenseAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RewardedInterstitialAdManager private constructor(private val context: Context) {

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val pendingLoadListeners = mutableListOf<Pair<(() -> Unit)?, ((String) -> Unit)?>>()

    // Official Google Test Rewarded Interstitial Ad Unit ID
    // https://developers.google.com/admob/android/test-ads#demo-units
    private val testAdUnitId = "ca-app-pub-3940256099942544/5354046379"

    private val adUnitId: String
        get() {
            val prodId = try {
                BuildConfig.REWARDED_INTERSTITIAL_AD_UNIT_ID
            } catch (_: Exception) {
                ""
            }
            return if (BuildConfig.DEBUG || prodId.isNullOrBlank() || prodId == "null") {
                testAdUnitId
            } else {
                prodId
            }
        }

    init {
        // Initialize Mobile Ads SDK if not already initialized
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "Mobile Ads SDK Initialized for Rewarded Interstitial: ${status.adapterStatusMap}")
            // Preload the first ad proactively
            loadAd()
        }
        if (BuildConfig.DEBUG) {
            val configuration = RequestConfiguration.Builder().setTestDeviceIds(
                listOf(
                    "310EED0071ED1F1ABEBE19909BC7DE85",
                    "71E88520A9C3C9069EEA413417D8524A"
                )
            ).build()
            MobileAds.setRequestConfiguration(configuration)
        }
    }

    fun isAdLoaded(): Boolean {
        return rewardedInterstitialAd != null
    }

    fun loadAd(onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        if (rewardedInterstitialAd != null) {
            _isAdLoaded.value = true
            _isLoading.value = false
            onLoaded?.invoke()
            return
        }

        synchronized(pendingLoadListeners) {
            if (onLoaded != null || onFailed != null) {
                pendingLoadListeners.add(Pair(onLoaded, onFailed))
            }
        }

        if (_isLoading.value) return
        _isLoading.value = true
        PixenseAnalytics.logEvent("rewarded_interstitial_ad_requested")

        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Rewarded interstitial ad failed to load: ${loadAdError.message}")
                    rewardedInterstitialAd = null
                    _isLoading.value = false
                    _isAdLoaded.value = false
                    PixenseAnalytics.logEvent(
                        "rewarded_interstitial_ad_load_failed",
                        mapOf("error" to loadAdError.message)
                    )

                    val listeners = synchronized(pendingLoadListeners) {
                        val list = ArrayList(pendingLoadListeners)
                        pendingLoadListeners.clear()
                        list
                    }
                    listeners.forEach { it.second?.invoke(loadAdError.message) }
                }

                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d(TAG, "Rewarded interstitial ad loaded successfully.")
                    rewardedInterstitialAd = ad
                    _isLoading.value = false
                    _isAdLoaded.value = true
                    PixenseAnalytics.logEvent("rewarded_interstitial_ad_loaded")

                    val listeners = synchronized(pendingLoadListeners) {
                        val list = ArrayList(pendingLoadListeners)
                        pendingLoadListeners.clear()
                        list
                    }
                    listeners.forEach { it.first?.invoke() }
                }
            }
        )
    }

    fun showAd(
        activity: Activity,
        onRewardEarned: (() -> Unit)? = null,
        onAdDismissed: () -> Unit
    ) {
        val currentAd = rewardedInterstitialAd
        if (currentAd == null) {
            // If ad is not preloaded yet, trigger load for next time and proceed with the transition
            loadAd()
            onAdDismissed()
            return
        }

        rewardedInterstitialAd = null
        _isAdLoaded.value = false
        PixenseAnalytics.logEvent("rewarded_interstitial_ad_shown")

        var isDismissHandled = false
        fun handleDismiss() {
            if (!isDismissHandled) {
                isDismissHandled = true
                loadAd()
                onAdDismissed()
            }
        }

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded interstitial ad clicked.")
                PixenseAnalytics.logEvent("rewarded_interstitial_ad_clicked")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial ad dismissed.")
                PixenseAnalytics.logEvent("rewarded_interstitial_ad_dismissed")
                handleDismiss()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Rewarded interstitial ad failed to show: ${adError.message}")
                PixenseAnalytics.logEvent(
                    "rewarded_interstitial_ad_show_failed",
                    mapOf("error" to adError.message)
                )
                handleDismiss()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded interstitial ad impression logged.")
                PixenseAnalytics.logEvent("rewarded_interstitial_ad_impression")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial ad showed full screen content.")
            }
        }

        currentAd.show(activity) { rewardItem ->
            Log.d(TAG, "Rewarded interstitial reward earned: ${rewardItem.amount} ${rewardItem.type}")
            PixenseAnalytics.logEvent(
                "rewarded_interstitial_ad_reward_earned",
                mapOf("type" to rewardItem.type, "amount" to rewardItem.amount)
            )
            onRewardEarned?.invoke()
        }
    }

    companion object {
        private const val TAG = "RewardedInterstitialAdManager"

        @Volatile
        private var instance: RewardedInterstitialAdManager? = null

        fun getInstance(context: Context): RewardedInterstitialAdManager {
            return instance ?: synchronized(this) {
                instance ?: RewardedInterstitialAdManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
