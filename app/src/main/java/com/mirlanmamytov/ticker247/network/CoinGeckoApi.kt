package com.mirlanmamytov.ticker247.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class CoinData(
    val id: String,
    val symbol: String,
    val name: String,
    @SerializedName("current_price") val currentPrice: Double?,
    @SerializedName("price_change_percentage_24h") val change24h: Double?,
    @SerializedName("image") val imageUrl: String?
)

interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("ids") ids: String = "bitcoin,ethereum,solana,tether,binancecoin,ripple",
        @Query("order") order: String = "market_cap_desc",
        @Query("sparkline") sparkline: Boolean = false,
        @Query("price_change_percentage") priceChangePercentage: String = "24h"
    ): List<CoinData>

    // Старый эндпоинт для бегущей строки
    @GET("simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String = "bitcoin,ethereum,solana",
        @Query("vs_currencies") vsCurrencies: String = "usd"
    ): Map<String, Map<String, Double>>
}
