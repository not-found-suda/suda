package com.ssafy.mobile.feature.childprofile.di

import com.ssafy.mobile.feature.childprofile.data.api.ChildProfileApiService
import com.ssafy.mobile.feature.childprofile.data.repository.RemoteChildProfileRepository
import com.ssafy.mobile.feature.childprofile.domain.repository.ChildProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class ChildProfileModule {
    @Binds
    @Singleton
    abstract fun bindChildProfileRepository(
        remoteChildProfileRepository: RemoteChildProfileRepository,
    ): ChildProfileRepository

    companion object {
        @Provides
        @Singleton
        fun provideChildProfileApiService(retrofit: Retrofit): ChildProfileApiService =
            retrofit.create(ChildProfileApiService::class.java)
    }
}
