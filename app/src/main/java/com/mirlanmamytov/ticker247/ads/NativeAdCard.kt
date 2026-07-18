package com.mirlanmamytov.ticker247.ads

import android.graphics.Typeface
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Нативная реклама AdMob в стиле новостных плиток.
 * Пока объявление не загрузилось (или нет заполнения) — показывается fallback
 * (партнёрская визитка), поэтому слот никогда не пустует.
 */
@Composable
fun NativeAdSlot(adUnitId: String, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    var nativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId) {
        var disposed = false
        var loadedAd: NativeAd? = null
        try {
            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    if (disposed) { ad.destroy(); return@forNativeAd }
                    loadedAd = ad
                    nativeAd = ad
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d("NativeAdSlot", "no fill: ${error.message}")
                    }
                })
                .build()
            loader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            Log.w("NativeAdSlot", "load: ${e.message}")
        }
        onDispose {
            disposed = true
            loadedAd?.destroy()
        }
    }

    val ad = nativeAd
    if (ad != null) NativeAdContent(ad) else fallback()
}

@Composable
private fun NativeAdContent(ad: NativeAd) {
    AndroidView(
        modifier = Modifier,
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val adView = NativeAdView(ctx)
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFF8F4FF.toInt())
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val badge = TextView(ctx).apply {
                text = "Реклама"
                textSize = 9f
                setTextColor(0xFF9CA3AF.toInt())
            }
            val headerRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, 0)
            }
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(10) }
            }
            val headline = TextView(ctx).apply {
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF111827.toInt())
                maxLines = 2
            }
            headerRow.addView(icon)
            headerRow.addView(headline)

            val body = TextView(ctx).apply {
                textSize = 12f
                setTextColor(0xFF6B7280.toInt())
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
            }
            val media = MediaView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(160)).apply { topMargin = dp(6) }
            }
            val cta = Button(ctx).apply {
                textSize = 13f
                isAllCaps = false
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF6366F1.toInt())
            }

            root.addView(badge)
            root.addView(headerRow)
            root.addView(body)
            root.addView(media)
            root.addView(cta)
            adView.addView(root)

            adView.iconView = icon
            adView.headlineView = headline
            adView.bodyView = body
            adView.mediaView = media
            adView.callToActionView = cta
            adView
        },
        update = { adView ->
            (adView.headlineView as TextView).text = ad.headline ?: ""
            (adView.bodyView as TextView).text = ad.body ?: ""
            (adView.callToActionView as Button).text = ad.callToAction ?: "Подробнее"
            val iconView = adView.iconView as ImageView
            val icon = ad.icon
            if (icon != null) {
                iconView.setImageDrawable(icon.drawable)
                iconView.visibility = android.view.View.VISIBLE
            } else {
                iconView.visibility = android.view.View.GONE
            }
            adView.setNativeAd(ad)
        }
    )
}
