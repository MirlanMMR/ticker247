package com.mirlanmamytov.ticker247.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val coinGecko: CoinGeckoApi = Retrofit.Builder()
        .baseUrl("https://api.coingecko.com/api/v3/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CoinGeckoApi::class.java)

    // CoinCap — бесплатный без ключа, заменяет CoinGecko для цен
    val coinCap: CoinCapApi = Retrofit.Builder()
        .baseUrl("https://api.coincap.io/v2/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CoinCapApi::class.java)

    val nbkr: NbkrApi = Retrofit.Builder()
        .baseUrl("https://www.nbkr.kg/")
        .client(okHttpClient)
        .build()
        .create(NbkrApi::class.java)

    var youtubeApiKey: String = ""

    val youtubeChannel: YouTubeChannelApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeChannelApi::class.java)
    }

    val openChargeMap: OpenChargeMapApi = Retrofit.Builder()
        .baseUrl("https://api.openchargemap.io/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenChargeMapApi::class.java)

    val exchangeRate: ExchangeRateApi = Retrofit.Builder()
        .baseUrl("https://open.er-api.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ExchangeRateApi::class.java)
}
