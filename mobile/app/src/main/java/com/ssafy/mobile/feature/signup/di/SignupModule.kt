package com.ssafy.mobile.feature.signup.di

import com.ssafy.mobile.feature.signup.data.api.SignupApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object SignupModule {
    @Provides
    @Singleton
    fun provideSignupApiService(
        @Named("NoAuth") retrofit: Retrofit,
    ): SignupApiService = retrofit.create(SignupApiService::class.java)
}
