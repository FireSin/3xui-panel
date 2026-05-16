package com.firesin.xuipanel.core.common.twofactor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TwoFactorOtpModule {

    @Binds
    @Singleton
    abstract fun bindTwoFactorOtpBus(impl: TwoFactorOtpDispatcher): TwoFactorOtpBus
}
