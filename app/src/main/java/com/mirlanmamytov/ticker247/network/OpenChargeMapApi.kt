package com.mirlanmamytov.ticker247.network

import retrofit2.http.GET
import retrofit2.http.Query

data class ChargingPoint(
    val AddressInfo: AddressInfo?
)

data class AddressInfo(
    val Title: String?,
    val Town: String?
)

interface OpenChargeMapApi {
    @GET("v3/poi/")
    suspend fun getChargingPoints(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distance: Int = 10,
        @Query("distanceunit") distanceUnit: String = "KM",
        @Query("maxresults") maxResults: Int = 5,
        @Query("compact") compact: Boolean = true,
        @Query("output") output: String = "json"
    ): List<ChargingPoint>
}
