package com.mirlanmamytov.ticker247.network

import retrofit2.http.GET
import retrofit2.http.Path

data class ExchangeRateResponse(
    val result: String?,
    val base_code: String?,
    val rates: Map<String, Double>?
)

interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun getRates(@Path("base") base: String = "USD"): ExchangeRateResponse
}
