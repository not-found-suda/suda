package com.ssafy.mobile.core.auth.di

import com.ssafy.mobile.core.auth.AndroidKeystoreTokenStorage
import com.ssafy.mobile.core.auth.TokenStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindTokenStorage(
        androidKeystoreTokenStorage: AndroidKeystoreTokenStorage,
    ): TokenStorage
}
