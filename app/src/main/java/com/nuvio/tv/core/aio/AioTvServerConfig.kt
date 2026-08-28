package com.nuvio.tv.core.aio

object AioTvServerConfig {
    /**
     * Public AIOStreams origin used for Pocket ID QR approval and the AIOtv API.
     * This is the Pangolin-exposed deployment origin reachable by both the TV
     * and the user's phone. No trailing slash.
     */
    const val BASE_URL: String = "https://aiohealth.peden88.stream"
}
