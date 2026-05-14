package com.firesin.xuipanel.core.sampler.di

import com.firesin.xuipanel.core.sampler.BackupSafWriter
import com.firesin.xuipanel.core.sampler.BackupSafWriterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindBackupSafWriter(impl: BackupSafWriterImpl): BackupSafWriter
}
