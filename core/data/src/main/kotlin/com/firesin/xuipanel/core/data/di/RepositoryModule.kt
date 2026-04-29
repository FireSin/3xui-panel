package com.firesin.xuipanel.core.data.di

import com.firesin.xuipanel.core.common.PanelPinWriter
import com.firesin.xuipanel.core.data.repository.PanelPinWriterImpl
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.data.repository.PanelRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPanelRepository(impl: PanelRepositoryImpl): PanelRepository

    @Binds
    @Singleton
    abstract fun bindPanelPinWriter(impl: PanelPinWriterImpl): PanelPinWriter
}
