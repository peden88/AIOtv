package com.nuvio.tv.core.di

import com.nuvio.tv.data.remote.api.AioTvApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AioTvNetworkModule {
    @Provides
    @Singleton
    fun provideAioTvApi(retrofit: Retrofit): AioTvApi =
        retrofit.create(AioTvApi::class.java)
}
