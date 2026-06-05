package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AdMobManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobManager.BANNER_AD_UNIT_ID
) {
    val context = LocalContext.current
    
    // Calculate adaptive ad size based on window width pixels
    val adSize = remember(context) {
        val activity = context as? Activity
        if (activity != null) {
            try {
                val display = activity.windowManager.defaultDisplay
                val outMetrics = DisplayMetrics()
                display.getMetrics(outMetrics)
                val widthPixels = outMetrics.widthPixels.toFloat()
                val density = outMetrics.density
                val adWidth = (widthPixels / density).toInt()
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
            } catch (e: Exception) {
                Log.e("AdBanner", "Error fetching adaptive banner size: ${e.message}. Using standard BANNER size.")
                AdSize.BANNER
            }
        } else {
            AdSize.BANNER
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.08f))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                // Use a framing layout to anchor spacing
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    
                    val adView = AdView(ctx).apply {
                        this.adUnitId = adUnitId
                        this.setAdSize(adSize)
                        this.adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                Log.d("AdBanner", "Ad successfully loaded under banner $adUnitId.")
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e("AdBanner", "Banner ad failed loading: ${error.message}.")
                            }
                        }
                    }
                    
                    addView(adView)
                    
                    val request = AdRequest.Builder().build()
                    adView.loadAd(request)
                }
            }
        )
    }
}
