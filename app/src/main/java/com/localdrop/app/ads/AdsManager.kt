package com.localdrop.app.ads

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.localdrop.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Test-only integration. Release ads remain disabled until production IDs and consent messages exist. */
class AdsManager(private val activity: Activity, private val scope: CoroutineScope) {
    companion object {
        const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"
        const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    }
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    var ready by mutableStateOf(false)
        private set
    var status by mutableStateOf(if (BuildConfig.DEBUG) "Waiting for consent" else "Ads disabled in release")
        private set
    var privacyRequired by mutableStateOf(false)
        private set
    private var initializing = false
    private var disposed = false
    private var interstitial: InterstitialAd? = null
    private var loadedAt = 0L
    private var lastShown = SystemClock.elapsedRealtime()
    private var completedBatches = 0

    fun requestConsent() {
        if (!BuildConfig.DEBUG) return
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                status = if (error != null) "Consent unavailable; ads may remain off" else "Consent checked"
                refreshConsent()
            }
        }, {
            status = "Consent unavailable; ads may remain off"
            refreshConsent()
        })
    }
    private fun refreshConsent() {
        if (disposed) return
        privacyRequired = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ready = ready && consent.canRequestAds()
        if (!consent.canRequestAds()) { interstitial = null; return }
        if (ready || initializing) return
        initializing = true
        scope.launch(Dispatchers.IO) {
            try {
                MobileAds.initialize(activity.applicationContext, InitializationConfig.Builder(TEST_APP_ID).build()) {}
                withContext(Dispatchers.Main) {
                    if (!disposed && consent.canRequestAds()) { ready = true; status = "Test ads enabled"; preload() }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { if (!disposed) status = "Ad SDK unavailable; transfers still work" }
            } finally { initializing = false }
        }
    }
    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) status = "Privacy options unavailable"
            refreshConsent()
        }
    }
    private fun preload() {
        if (disposed || !ready || !consent.canRequestAds()) return
        InterstitialAd.load(AdRequest.Builder(TEST_INTERSTITIAL_ID).build(), object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(ad: InterstitialAd) {
                activity.runOnUiThread { if (!disposed && ready) { interstitial = ad; loadedAt = SystemClock.elapsedRealtime() } }
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                activity.runOnUiThread { if (!disposed) { interstitial = null; status = "Interstitial unavailable; transfers still work" } }
            }
        })
    }
    // Called on a user-initiated return home after successful Push batches, never on completion itself.
    fun naturalBreak(transferActive: Boolean, pairingActive: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!ready || !consent.canRequestAds() || activity.isFinishing || activity.isDestroyed) return
        if (!AdPolicy.eligible(now, lastShown, completedBatches, transferActive, pairingActive)) return
        val ad = interstitial ?: run { preload(); return }
        if (now - loadedAt >= 60 * 60_000L) { interstitial = null; preload(); return }
        interstitial = null
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() { activity.runOnUiThread { lastShown = SystemClock.elapsedRealtime(); completedBatches = 0 } }
            override fun onAdDismissedFullScreenContent() { activity.runOnUiThread { preload() } }
            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) { activity.runOnUiThread { preload() } }
        }
        ad.show(activity)
    }
    fun completedBatch() { completedBatches++ }
    fun dispose() { disposed = true; ready = false; interstitial = null }
}

@Composable
fun TestBanner() {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        val width = maxWidth.value.toInt().coerceAtLeast(1)
        key(width) {
            val view = remember(context) { AdView(context) }
            var loaded by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (loaded) Text("Advertisement · test", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().wrapContentHeight())
            }
            LaunchedEffect(view, width) {
                val size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, width)
                view.loadAd(BannerAdRequest.Builder(AdsManager.TEST_BANNER_ID, size).build(), object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) { view.post { loaded = true } }
                    override fun onAdFailedToLoad(error: LoadAdError) { view.post { loaded = false } }
                })
            }
            DisposableEffect(view) { onDispose { view.destroy() } }
        }
    }
}
