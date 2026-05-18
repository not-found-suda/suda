package com.ssafy.mobile.feature.login.di

import com.ssafy.mobile.feature.login.data.api.LoginApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object LoginModule {
    @Provides
    @Singleton
    fun provideLoginApiService(
        @Named("NoAuth") retrofit: Retrofit,
    ): LoginApiService = retrofit.create(LoginApiService::class.java)
}
