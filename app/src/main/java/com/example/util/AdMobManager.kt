package com.example.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AnimeCrimson
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DimText
import com.example.ui.theme.MediumText
import com.example.ui.theme.OutlineDark
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdMobManager {

    private const val TAG = "TitanAnimeAdMob"

    // Official Google Mobile Ads sample test unit IDs
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private const val PREFS_NAME = "admob_settings"
    private const val KEY_CUSTOM_BANNER_ID = "custom_banner_id"
    private const val KEY_CUSTOM_INTERSTITIAL_ID = "custom_interstitial_id"
    private const val KEY_CUSTOM_REWARDED_ID = "custom_rewarded_id"
    private const val KEY_USE_TEST_ADS = "use_test_ads"

    private var isInitialized = false
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    var isInterstitialLoading = false
        private set
    var isRewardedLoading = false
        private set

    fun initialize(context: Context, onComplete: () -> Unit = {}) {
        if (isInitialized) {
            onComplete()
            return
        }
        try {
            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d(TAG, "AdMob MobileAds initialized: $status")
                onComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdMob initialization error", e)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isUseTestAds(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_TEST_ADS, true)
    }

    fun setUseTestAds(context: Context, useTest: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_TEST_ADS, useTest).apply()
    }

    fun getBannerAdUnitId(context: Context): String {
        val prefs = getPrefs(context)
        if (isUseTestAds(context)) return TEST_BANNER_ID
        val custom = prefs.getString(KEY_CUSTOM_BANNER_ID, "") ?: ""
        return if (custom.isNotBlank()) custom else TEST_BANNER_ID
    }

    fun getInterstitialAdUnitId(context: Context): String {
        val prefs = getPrefs(context)
        if (isUseTestAds(context)) return TEST_INTERSTITIAL_ID
        val custom = prefs.getString(KEY_CUSTOM_INTERSTITIAL_ID, "") ?: ""
        return if (custom.isNotBlank()) custom else TEST_INTERSTITIAL_ID
    }

    fun getRewardedAdUnitId(context: Context): String {
        val prefs = getPrefs(context)
        if (isUseTestAds(context)) return TEST_REWARDED_ID
        val custom = prefs.getString(KEY_CUSTOM_REWARDED_ID, "") ?: ""
        return if (custom.isNotBlank()) custom else TEST_REWARDED_ID
    }

    fun saveCustomAdUnits(
        context: Context,
        bannerId: String,
        interstitialId: String,
        rewardedId: String,
        useTestAds: Boolean
    ) {
        getPrefs(context).edit()
            .putString(KEY_CUSTOM_BANNER_ID, bannerId.trim())
            .putString(KEY_CUSTOM_INTERSTITIAL_ID, interstitialId.trim())
            .putString(KEY_CUSTOM_REWARDED_ID, rewardedId.trim())
            .putBoolean(KEY_USE_TEST_ADS, useTestAds)
            .apply()
    }

    fun getCustomBannerId(context: Context): String =
        getPrefs(context).getString(KEY_CUSTOM_BANNER_ID, "") ?: ""

    fun getCustomInterstitialId(context: Context): String =
        getPrefs(context).getString(KEY_CUSTOM_INTERSTITIAL_ID, "") ?: ""

    fun getCustomRewardedId(context: Context): String =
        getPrefs(context).getString(KEY_CUSTOM_REWARDED_ID, "") ?: ""

    /**
     * Preloads Interstitial Ad
     */
    fun loadInterstitial(
        context: Context,
        onLoaded: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val adUnitId = getInterstitialAdUnitId(context)
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial Ad Loaded successfully")
                    onLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    val errorMsg = loadAdError.message
                    Log.e(TAG, "Interstitial Ad Failed to load: $errorMsg (code: ${loadAdError.code})")
                    onError("Failed to load Interstitial ($errorMsg)")
                }
            }
        )
    }

    /**
     * Shows Interstitial Ad if available, then runs onDismissed
     */
    fun showInterstitial(
        activity: Activity,
        onDismissed: () -> Unit
    ) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    // Preload next
                    loadInterstitial(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onDismissed()
                }
            }
            ad.show(activity)
        } else {
            // Not ready, proceed anyway
            loadInterstitial(activity)
            onDismissed()
        }
    }

    /**
     * Preloads Rewarded Ad
     */
    fun loadRewarded(
        context: Context,
        onLoaded: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val adUnitId = getRewardedAdUnitId(context)
        isRewardedLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "Rewarded Ad Loaded successfully")
                    onLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    val errorMsg = loadAdError.message
                    Log.e(TAG, "Rewarded Ad Failed to load: $errorMsg")
                    onError("Failed to load Rewarded Ad ($errorMsg)")
                }
            }
        )
    }

    /**
     * Shows Rewarded Ad if available
     */
    fun showRewarded(
        activity: Activity,
        onEarnedReward: () -> Unit,
        onDismissed: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewarded(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onDismissed()
                }
            }
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onEarnedReward()
            }
        } else {
            loadRewarded(activity)
            onDismissed()
        }
    }
}

/**
 * Modern Jetpack Compose AdMob Banner Composable
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }
    var adErrorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceContainer)
            .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ad unit container
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdMobManager.getBannerAdUnitId(ctx)
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                isAdLoaded = true
                                adErrorMessage = null
                            }

                            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                isAdLoaded = false
                                adErrorMessage = "AdMob: ${loadAdError.message} (code ${loadAdError.code})"
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            // Minimal Ad indicator tag
            Text(
                text = if (isAdLoaded) "AD • Google AdMob Test Unit" else (adErrorMessage ?: "Loading AdMob Ad Unit..."),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = if (adErrorMessage != null) AnimeCrimson else DimText,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
