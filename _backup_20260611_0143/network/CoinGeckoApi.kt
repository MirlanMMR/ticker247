package com.mirlanmamytov.ticker247.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// CoinGecko (оставляем для совместимости)
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
}

// ── CoinCap API (бесплатный, без ключа) ──────────────────────────────────────

data class CoinCapAsset(
    val id: String,
    val symbol: String,
    val name: String,
    val priceUsd: String?,
    val changePercent24Hr: String?,
    val marketCapUsd: String?
) {
    val currentPrice: Double? get() = priceUsd?.toDoubleOrNull()
    val change24h: Double? get() = changePercent24Hr?.toDoubleOrNull()
    // Иконки через CoinGecko CDN по symbol
    val imageUrl: String get() = "https://assets.coincap.io/assets/icons/${symbol.lowercase()}@2x.png"
}

data class CoinCapResponse(
    val data: List<CoinCapAsset>
)

interface CoinCapApi {
    @GET("assets")
    suspend fun getAssets(
        @Query("ids") ids: String = "bitcoin,ethereum,tether,binance-coin,solana,ripple",
        @Query("limit") limit: Int = 6
    ): CoinCapResponse
}
