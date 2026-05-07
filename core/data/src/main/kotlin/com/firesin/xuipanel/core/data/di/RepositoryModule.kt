package com.firesin.xuipanel.core.data.di

import com.firesin.xuipanel.core.common.PanelPinWriter
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
import com.firesin.xuipanel.core.data.repository.AppSecurityRepositoryImpl
import com.firesin.xuipanel.core.data.repository.BackupRepository
import com.firesin.xuipanel.core.data.repository.BackupRepositoryImpl
import com.firesin.xuipanel.core.data.repository.PanelPinWriterImpl
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.data.repository.PanelRepositoryImpl
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindTrafficHistoryRepository(impl: TrafficHistoryRepositoryImpl): TrafficHistoryRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindAppSecurityRepository(impl: AppSecurityRepositoryImpl): AppSecurityRepository
}
