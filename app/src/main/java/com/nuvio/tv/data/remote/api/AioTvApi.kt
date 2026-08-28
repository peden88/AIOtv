package com.nuvio.tv.data.remote.api

import com.nuvio.tv.data.remote.dto.AioTvApiEnvelope
import com.nuvio.tv.data.remote.dto.AioTvBootstrapData
import com.nuvio.tv.data.remote.dto.AioTvDeviceStartData
import com.nuvio.tv.data.remote.dto.AioTvDeviceTokenData
import com.nuvio.tv.data.remote.dto.AioTvDeviceTokenRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface AioTvApi {
    @POST
    suspend fun startDeviceAuth(
        @Url url: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<AioTvApiEnvelope<AioTvDeviceStartData>>

    @POST
    suspend fun pollDeviceToken(
        @Url url: String,
        @Body body: AioTvDeviceTokenRequest
    ): Response<AioTvApiEnvelope<AioTvDeviceTokenData>>

    @GET
    suspend fun bootstrap(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<AioTvApiEnvelope<AioTvBootstrapData>>
}
