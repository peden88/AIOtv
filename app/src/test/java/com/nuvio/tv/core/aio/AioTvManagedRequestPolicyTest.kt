package com.nuvio.tv.core.aio

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AioTvManagedRequestPolicyTest {
    @Test
    fun `authorises only metadata proxy paths on exact control origin`() {
        val control = "https://aiocontrol.peden88.stream"

        assertTrue(
            AioTvManagedRequestPolicy.isMetadataProxyRequest(
                "https://aiocontrol.peden88.stream/api/v1/metadata/manifest.json".toHttpUrl(),
                control
            )
        )
        assertFalse(
            AioTvManagedRequestPolicy.isMetadataProxyRequest(
                "https://aiocontrol.peden88.stream/api/v1/device/bootstrap".toHttpUrl(),
                control
            )
        )
        assertFalse(
            AioTvManagedRequestPolicy.isMetadataProxyRequest(
                "https://example.com/api/v1/metadata/manifest.json".toHttpUrl(),
                control
            )
        )
        assertFalse(
            AioTvManagedRequestPolicy.isMetadataProxyRequest(
                "https://aiocontrol.peden88.stream.evil.example/api/v1/metadata/manifest.json".toHttpUrl(),
                control
            )
        )
    }

    @Test
    fun `supports a control server base path without widening token scope`() {
        val control = "https://example.com/aiotv"

        assertTrue(
            AioTvManagedRequestPolicy.isMetadataProxyRequest(
                "https://example.com/aiotv/api/v1/metadata/catalog/movie/popular.json".toHttpUrl(),
                control
            )
        )
        assertFalse(
            AioTvManagedRequestPolicy.isMetadataProxyRequest(
                "https://example.com/api/v1/metadata/catalog/movie/popular.json".toHttpUrl(),
                control
            )
        )
    }
}
