package kr.baraplt.material.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kr.baraplt.material.ads.AdConfig
import kr.baraplt.material.ads.AdsSdk

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!AdConfig.SHOW_BANNER) return
    val activity = LocalContext.current.findActivity() ?: return
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val adSize = remember(screenWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, screenWidthDp)
    }
    key(adSize.width, adSize.height) {
        val holder = remember { AdViewHolder() }
        var loaded by remember { mutableStateOf(false) }
        val slot = if (loaded) {
            Modifier.height(adSize.height.coerceAtLeast(50).dp)
        } else {
            Modifier.height(0.dp)
        }
        Column {
            if (loaded) Spacer(Modifier.fillMaxWidth().height(8.dp))
            AndroidView(
            modifier = modifier.fillMaxWidth().then(slot),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        adSize.getHeightInPixels(ctx)
                    )
                    val adView = AdView(activity).apply {
                        setAdSize(adSize)
                        adUnitId = AdConfig.BANNER_UNIT_ID
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                loaded = true
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                loaded = false
                            }
                        }
                    }
                    addView(adView)
                    AdsSdk.whenReady {
                        if (!holder.released) adView.loadAd(AdRequest.Builder().build())
                    }
                }
            },
            onRelease = { layout ->
                holder.released = true
                (layout.getChildAt(0) as? AdView)?.destroy()
            }
            )
        }
    }
}

private class AdViewHolder {
    var released: Boolean = false
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
