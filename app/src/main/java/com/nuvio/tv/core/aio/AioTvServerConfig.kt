package com.nuvio.tv.core.aio

object AioTvServerConfig {
    /**
     * Public AIOStreams origin used for Pocket ID QR approval and the AIOtv API.
     *
     * This must be the Pangolin-exposed AIOStreams origin that the TV and the
     * user's phone can both reach (for example https://streams.example.com),
     * with no trailing slash. It intentionally remains blank in source control
     * until the deployment-specific origin is selected.
     */
    const val BASE_URL: String = ""
}
