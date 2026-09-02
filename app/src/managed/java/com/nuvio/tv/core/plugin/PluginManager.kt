package com.nuvio.tv.core.plugin

import android.content.Context
import com.nuvio.tv.domain.model.LocalScraperResult
import com.nuvio.tv.domain.model.PluginRepository
import com.nuvio.tv.domain.model.RemotePluginInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Managed-build plugin manager.
 *
 * AIOtv managed builds intentionally disable local/plugin installation and execution,
 * but common app code still depends on PluginManager through dependency injection.
 * This flavour-specific implementation preserves that API surface while returning
 * no local plugin results and rejecting plugin-management operations.
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getRepositories(): List<PluginRepository> = emptyList()

    suspend fun getInstalledPlugins(): List<RemotePluginInfo> = emptyList()

    suspend fun getAvailablePlugins(): List<RemotePluginInfo> = emptyList()

    suspend fun search(
        query: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null
    ): List<LocalScraperResult> = emptyList()

    suspend fun installRepository(url: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Plugin repositories are disabled in managed builds"))

    suspend fun removeRepository(url: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Plugin repositories are disabled in managed builds"))

    suspend fun installPlugin(plugin: RemotePluginInfo): Result<Unit> =
        Result.failure(UnsupportedOperationException("Plugin installation is disabled in managed builds"))

    suspend fun removePlugin(plugin: RemotePluginInfo): Result<Unit> =
        Result.failure(UnsupportedOperationException("Plugin installation is disabled in managed builds"))

    suspend fun refreshRepositories() = Unit

    suspend fun initialize() = Unit
}
