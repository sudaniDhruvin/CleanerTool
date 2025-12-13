package com.example.cleanertool.ads

import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.example.cleanertool.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdView(
    adUnitId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val finalAdUnitId = adUnitId ?: AdConstants.getNativeAdUnitId()
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    val adLoader = remember {
        AdLoader.Builder(context, finalAdUnitId)
            .forNativeAd { ad ->
                android.util.Log.d("NativeAd", "Native ad loaded successfully")
                nativeAd = ad
                isLoading = false
                loadError = null
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .build()
            )
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    val error = "Native ad failed to load: ${loadAdError.message} (Code: ${loadAdError.code})"
                    android.util.Log.e("NativeAd", error)
                    loadError = error
                    isLoading = false
                }
                
                override fun onAdLoaded() {
                    android.util.Log.d("NativeAd", "Native ad loaded")
                }
            })
            .build()
    }
    
    LaunchedEffect(Unit) {
        android.util.Log.d("NativeAd", "Loading native ad with unit ID: $finalAdUnitId")
        adLoader.loadAd(AdRequest.Builder().build())
    }
    
    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
        }
    }
    
    if (isLoading) {
        // Show placeholder while loading
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(320.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "Loading ad...",
                    color = Color.Gray
                )
            }
        }
    } else if (loadError != null) {
        // Hide error - don't show error message to user
        Spacer(modifier = modifier.height(0.dp))
    } else {
        nativeAd?.let { ad ->
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val inflater = LayoutInflater.from(ctx)
                        val nativeAdView = inflater.inflate(
                            R.layout.native_ad_layout,
                            null
                        ) as NativeAdView
                        
                        // Get views from layout
                        val headlineView = nativeAdView.findViewById<TextView>(R.id.ad_headline)
                        val bodyView = nativeAdView.findViewById<TextView>(R.id.ad_body)
                        val callToActionView = nativeAdView.findViewById<Button>(R.id.ad_call_to_action)
                        val mediaView = nativeAdView.findViewById<MediaView>(R.id.ad_media)
                        
                        // Populate views
                        headlineView?.text = ad.headline
                        bodyView?.text = ad.body
                        callToActionView?.text = ad.callToAction
                        
                        // Set views to native ad view
                        nativeAdView.headlineView = headlineView
                        nativeAdView.bodyView = bodyView
                        nativeAdView.callToActionView = callToActionView
                        nativeAdView.mediaView = mediaView
                        
                        // Set native ad
                        nativeAdView.setNativeAd(ad)
                        
                        // Create container
                        FrameLayout(ctx).apply {
                            addView(nativeAdView)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } ?: run {
            // No ad available - hide it
            Spacer(modifier = modifier.height(0.dp))
        }
    }
}

