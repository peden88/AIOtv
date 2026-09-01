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
    val verificationUri: String,
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
    val expiresIn: Int? = null,
    val configUuid: String? = null
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapData(
    val account: AioTvBootstrapAccount,
    val policy: AioTvBootstrapPolicy,
    val management: AioTvManagementPolicy
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapAccount(
    val uuid: String
)

@JsonClass(generateAdapter = true)
data class AioTvBootstrapPolicy(
    val revision: Int,
    val updatedAt: Long,
    val addons: List<AioTvManagedAddon> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AioTvManagedAddon(
    val name: String = "",
    val manifestUrl: String
)

@JsonClass(generateAdapter = true)
data class AioTvManagementPolicy(
    val addonMembership: String,
    val catalogOrder: String
)
