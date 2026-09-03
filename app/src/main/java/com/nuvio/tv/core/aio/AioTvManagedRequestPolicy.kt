package com.nuvio.tv.core.aio

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AioTvManagedRequestPolicy {
    fun isMetadataProxyRequest(requestUrl: HttpUrl, controlBaseUrl: String): Boolean {
        val controlUrl = controlBaseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return false
        if (requestUrl.scheme != controlUrl.scheme ||
            requestUrl.host != controlUrl.host ||
            requestUrl.port != controlUrl.port
        ) {
            return false
        }
        val controlPath = controlUrl.encodedPath.trimEnd('/')
        val metadataPrefix = "$controlPath/api/v1/metadata/".replace("//", "/")
        return requestUrl.encodedPath.startsWith(metadataPrefix)
    }
}
