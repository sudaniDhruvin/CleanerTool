package com.example.cleanertool.ads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cleanertool.R
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdView(
    adUnitId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val finalAdUnitId = adUnitId ?: AdConstants.getNativeAdUnitId()
    
    LaunchedEffect(Unit) {
        MobileAds.initialize(context) {}
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val container = FrameLayout(ctx)
                val shimmer = LayoutInflater.from(ctx)
                    .inflate(R.layout.native_ad_shimmer, container, false) as ShimmerFrameLayout
                
                container.addView(shimmer)
                
                // Load native ad using helper
                NativeAdHelper.loadNativeAd(ctx, container, shimmer, finalAdUnitId)
                
                container
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

object NativeAdHelper {
    private var nativeAd: NativeAd? = null

    fun loadNativeAd(
        context: Context,
        adContainer: FrameLayout,
        shimmerView: ShimmerFrameLayout,
        adUnitId: String
    ) {
        // Start shimmer effect and hide ad container initially
        shimmerView.startShimmer()
        shimmerView.visibility = View.VISIBLE
        adContainer.visibility = View.GONE

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                // Store the loaded ad
                nativeAd?.destroy() // Destroy old ad if exists to prevent memory leaks
                nativeAd = ad

                showLoadedAd(adContainer, shimmerView)
            }
            .withAdListener(object : AdListener() {
                override fun onAdClicked() {
                    super.onAdClicked()
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                }

                override fun onAdFailedToLoad(@NonNull loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    shimmerView.stopShimmer()
                    shimmerView.visibility = View.GONE
                    adContainer.visibility = View.GONE
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    // Function to display already loaded ad
    private fun showLoadedAd(adContainer: FrameLayout, shimmerView: ShimmerFrameLayout) {
        val ad = nativeAd ?: return

        // Hide shimmer and show ad
        shimmerView.stopShimmer()
        shimmerView.visibility = View.GONE
        adContainer.visibility = View.VISIBLE

        // Inflate and populate ad layout
        val inflater = LayoutInflater.from(adContainer.context)
        val adView = inflater.inflate(R.layout.native_ad_layout, null) as NativeAdView
        populateNativeAdView(ad, adView)

        // Remove old views and add new ad
        adContainer.removeAllViews()
        adContainer.addView(adView)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // Headline (Required)
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        adView.headlineView = headlineView
        headlineView?.text = nativeAd.headline

        // Body Text
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        adView.bodyView = bodyView
        if (nativeAd.body != null) {
            bodyView?.text = nativeAd.body
            bodyView?.visibility = View.VISIBLE
        } else {
            bodyView?.visibility = View.GONE
        }

        // Call-to-Action Button
        val ctaButton = adView.findViewById<Button>(R.id.ad_call_to_action)
        adView.callToActionView = ctaButton
        if (nativeAd.callToAction != null) {
            ctaButton?.text = nativeAd.callToAction
            ctaButton?.visibility = View.VISIBLE
        } else {
            ctaButton?.visibility = View.GONE
        }

        // Ad Icon (App Logo)
        val adIcon = adView.findViewById<ImageView>(R.id.ad_app_icon)
        adView.iconView = adIcon
        if (nativeAd.icon != null) {
            adIcon?.setImageDrawable(nativeAd.icon?.drawable)
            adIcon?.visibility = View.VISIBLE
        } else {
            adIcon?.visibility = View.GONE
        }

        // Star Rating (Optional)
        val ratingBar = adView.findViewById<RatingBar>(R.id.ad_stars)
        adView.starRatingView = ratingBar
        if (nativeAd.starRating != null) {
            ratingBar?.rating = nativeAd.starRating!!.toFloat()
            ratingBar?.visibility = View.VISIBLE
        } else {
            ratingBar?.visibility = View.GONE
        }

        // Bind the native ad object to the view
        adView.setNativeAd(nativeAd)
    }
}
