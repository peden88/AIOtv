package com.nuvio.tv.core.aio

import com.nuvio.tv.BuildConfig

object AioTvServerConfig {
    /**
     * Standalone AIOtv Control origin compiled into this APK. No trailing slash.
     * Configure AIOTV_CONTROL_URL in local.properties or the build environment.
     */
    val BASE_URL: String
        get() = BuildConfig.AIOTV_CONTROL_URL
}
