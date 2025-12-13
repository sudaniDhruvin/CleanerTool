package com.example.cleanertool.ads

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun BannerAdView(
    adUnitId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val finalAdUnitId = adUnitId ?: AdConstants.getBannerAdUnitId()
    var adLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = finalAdUnitId
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("BannerAd", "Banner ad loaded successfully")
                    adLoaded = true
                    loadError = null
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val error = "Banner ad failed to load: ${loadAdError.message} (Code: ${loadAdError.code})"
                    Log.e("BannerAd", error)
                    loadError = error
                    adLoaded = false
                }
                
                override fun onAdOpened() {
                    Log.d("BannerAd", "Banner ad opened")
                }
                
                override fun onAdClosed() {
                    Log.d("BannerAd", "Banner ad closed")
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        Log.d("BannerAd", "Loading banner ad with unit ID: $finalAdUnitId")
        adView.loadAd(AdRequest.Builder().build())
    }
    
    DisposableEffect(Unit) {
        onDispose {
            adView.destroy()
        }
    }
    
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                addView(adView)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}

