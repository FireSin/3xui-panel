package com.firesin.xuipanel.core.xui.di

import com.firesin.xuipanel.core.common.PinMismatchEventBus
import com.firesin.xuipanel.core.common.WsUiEventBus
import com.firesin.xuipanel.core.xui.PinMismatchEventDispatcher
import com.firesin.xuipanel.core.xui.WsUiEventDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class XuiModule {

    @Binds
    @Singleton
    abstract fun bindPinMismatchEventBus(impl: PinMismatchEventDispatcher): PinMismatchEventBus

    @Binds
    @Singleton
    abstract fun bindWsUiEventBus(impl: WsUiEventDispatcher): WsUiEventBus
}
