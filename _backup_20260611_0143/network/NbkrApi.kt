package com.mirlanmamytov.ticker247.network

import retrofit2.http.GET

interface NbkrApi {
    // НБКР отдаёт XML с курсами валют к сому
    @GET("XML/daily.xml")
    suspend fun getDailyRates(): retrofit2.Response<okhttp3.ResponseBody>
}
