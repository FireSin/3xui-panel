package com.firesin.xuipanel.core.sampler.di

import com.firesin.xuipanel.core.sampler.SamplerClock
import com.firesin.xuipanel.core.sampler.SystemSamplerClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SamplerModule {

    @Provides
    @Singleton
    fun provideSamplerClock(): SamplerClock = SystemSamplerClock
}
