package com.firesin.xuipanel.core.data.di

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
}
