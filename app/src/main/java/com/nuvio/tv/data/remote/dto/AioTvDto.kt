package com.nuvio.tv.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AioTvApiEnvelope<T>(
    val success: Boolean,
    val detail: String? = null,
    val data: T? = null,
    val error: AioTvApiError? = null
)

@JsonClass(generateAdapter = true)
data class AioTvApiError(
    val code: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class AioTvDeviceStartData(
    val deviceCode: String,
    val userCode: String,
    val expiresIn: Int,
    val interval: Int
)

@JsonClass(generateAdapter = true)
data class AioTvDeviceTokenRequest(
    val deviceCode: String
)

@JsonClass(generateAdapter = true)
data class AioTvDeviceTokenData(
    val status: String,
    val interval: Int? = null,
    val accessToken: String? = null,
    val tokenType: String? = null,
    val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapData(
    val device: AioTvBootstrapDevice,
    val profile: AioTvBootstrapProfile,
    val policy: AioTvBootstrapPolicy,
    val management: AioTvManagementPolicy
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapDevice(
    val id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapProfile(
    val id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapPolicy(
    val revision: Int,
    val updatedAt: String,
    val addons: List<AioTvManagedAddon> = emptyList(),
    val collections: List<AioTvManagedCollection> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AioTvManagedAddon(
    val name: String = "",
    val manifestUrl: String
)

@JsonClass(generateAdapter = true)
data class AioTvManagedCollection(
    val id: String,
    val name: String = "",
    val json: String
)

@JsonClass(generateAdapter = true)
data class AioTvManagementPolicy(
    val addonMembership: String,
    val catalogOrder: String
)
