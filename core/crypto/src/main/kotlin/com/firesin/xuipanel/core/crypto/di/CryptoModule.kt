package com.firesin.xuipanel.core.crypto.di

import com.firesin.xuipanel.core.crypto.DbPassphraseProvider
import com.firesin.xuipanel.core.crypto.DbPassphraseProviderImpl
import com.firesin.xuipanel.core.crypto.KeystoreWrapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindDbPassphraseProvider(impl: DbPassphraseProviderImpl): DbPassphraseProvider

    companion object {
        @Provides
        @Singleton
        fun provideKeystoreWrapper(): KeystoreWrapper = KeystoreWrapper()
    }
}
