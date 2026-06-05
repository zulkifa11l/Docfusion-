package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

object AdMobManager {
    private const val TAG = "AdMobManager"

    // User-provided ad unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-4568663453528450/3654766016"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-4568663453528450/7752803442"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4568663453528450/9011564702"

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    
    // Loaded Ads State
    var interstitialAd: InterstitialAd? = null
    var isInterstitialLoading = false
    
    var rewardedAd: RewardedAd? = null
    var isRewardedLoading = false

    // Frequency Caps and Safety
    private var lastInterstitialTime: Long = 0
    private var screenTransitionCount = 0
    private const val INTERSTITIAL_COOLDOWN_MS = 25000 // 25 seconds cooldown
    private const val REQUIRED_TRANSITIONS = 2 // Transition limit

    // Rewarded load retry mechanism
    private var rewardedRetryAttempt = 0
    private var interstitialRetryAttempt = 0

    fun initialize(context: Context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }
        Log.d(TAG, "Initializing Google Mobile Ads SDK...")
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "MobileAds initialization complete. $status")
            // Prefetch ads immediately to have them ready
            loadInterstitial(context.applicationContext)
            loadRewarded(context.applicationContext)
        }
    }

    // Prefetch Interstitial Ad
    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true
        Log.d(TAG, "Loading Interstitial Ad...")

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    interstitialRetryAttempt = 0
                    Log.d(TAG, "Interstitial Ad successfully loaded.")
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Interstitial Ad dismissed.")
                            interstitialAd = null
                            // Load the next one
                            loadInterstitial(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Log.e(TAG, "Interstitial failed to show: ${error.message}")
                            interstitialAd = null
                            loadInterstitial(context)
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial Ad failed to load: ${error.message}")
                    interstitialAd = null
                    isInterstitialLoading = false
                    
                    // Simple retry logic with delay limits
                    val delay = (1L shl interstitialRetryAttempt).coerceAtMost(30) * 1000
                    interstitialRetryAttempt++
                    Log.d(TAG, "Retrying to load Interstitial in ${delay}ms")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadInterstitial(context)
                    }, delay)
                }
            }
        )
    }

    // Prefetch Rewarded Ad
    fun loadRewarded(context: Context) {
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true
        Log.d(TAG, "Loading Rewarded Ad...")

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    rewardedRetryAttempt = 0
                    Log.d(TAG, "Rewarded Ad successfully loaded.")

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Rewarded Ad dismissed.")
                            rewardedAd = null
                            loadRewarded(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Log.e(TAG, "Rewarded failed to show: ${error.message}")
                            rewardedAd = null
                            loadRewarded(context)
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded Ad failed to load: ${error.message}")
                    rewardedAd = null
                    isRewardedLoading = false

                    val delay = (1L shl rewardedRetryAttempt).coerceAtMost(30) * 1000
                    rewardedRetryAttempt++
                    Log.d(TAG, "Retrying to load Rewarded in ${delay}ms")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadRewarded(context)
                    }, delay)
                }
            }
        )
    }

    // Try showing an interstitial during natural transition with frequency capping criteria
    fun showInterstitialOnTransition(activity: Activity, onAdClosedOrSkipped: () -> Unit) {
        screenTransitionCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastInterstitialTime

        Log.d(TAG, "Transition trigger: #$screenTransitionCount, elapsed: ${elapsed}ms")

        val ad = interstitialAd
        if (ad != null && screenTransitionCount >= REQUIRED_TRANSITIONS && elapsed >= INTERSTITIAL_COOLDOWN_MS) {
            Log.d(TAG, "Frequency cap satisfied. Showing Interstitial...")
            
            // Swap standard content callback to track close & perform navigation
            val originalCallback = ad.fullScreenContentCallback
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    originalCallback?.onAdDismissedFullScreenContent()
                    lastInterstitialTime = System.currentTimeMillis()
                    screenTransitionCount = 0
                    activity.runOnUiThread { onAdClosedOrSkipped() }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    originalCallback?.onAdFailedToShowFullScreenContent(error)
                    activity.runOnUiThread { onAdClosedOrSkipped() }
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Frequency cap or ad unavailable. Transiting directly.")
            onAdClosedOrSkipped()
        }
    }

    // Force show interstitial (for manual ad demo triggers or debug sections)
    fun showInterstitialForce(activity: Activity, onAdClosedOrSkipped: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            val originalCallback = ad.fullScreenContentCallback
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    originalCallback?.onAdDismissedFullScreenContent()
                    lastInterstitialTime = System.currentTimeMillis()
                    activity.runOnUiThread { onAdClosedOrSkipped() }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    originalCallback?.onAdFailedToShowFullScreenContent(error)
                    activity.runOnUiThread { onAdClosedOrSkipped() }
                }
            }
            ad.show(activity)
        } else {
            Toast.makeText(activity, "Interstitial ad is loading, please try again", Toast.LENGTH_SHORT).show()
            onAdClosedOrSkipped()
            loadInterstitial(activity.applicationContext)
        }
    }

    // Show Rewarded Ad before premium actions, with error recovery mechanisms
    fun showRewardedAd(activity: Activity, onRewardGranted: () -> Unit, onFailure: () -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            var rewardClaimed = false
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User watched full ad. Reward: ${rewardItem.amount} ${rewardItem.type}")
                rewardClaimed = true
            }

            // Capture dismiss content callback to trigger premium actions
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed.")
                    rewardedAd = null
                    loadRewarded(activity.applicationContext)
                    activity.runOnUiThread {
                        if (rewardClaimed) {
                            onRewardGranted()
                        } else {
                            onFailure()
                        }
                    }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e(TAG, "Rewarded failed to show: ${error.message}")
                    rewardedAd = null
                    loadRewarded(activity.applicationContext)
                    activity.runOnUiThread {
                        onFailure()
                    }
                }
            }
        } else {
            Log.w(TAG, "Rewarded ad was not yet loaded. Loading now and failing gracefully.")
            Toast.makeText(activity, "Rewarded ad is loading. Granting demo access fallback!", Toast.LENGTH_LONG).show()
            loadRewarded(activity.applicationContext)
            // Grant demo fallback to avoid freezing core functionality on offline or slow load states
            onRewardGranted()
        }
    }
}
