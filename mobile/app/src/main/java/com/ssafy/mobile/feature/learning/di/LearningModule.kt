package com.ssafy.mobile.feature.learning.di

import com.ssafy.mobile.feature.learning.data.api.LearningCategoryApiService
import com.ssafy.mobile.feature.learning.data.repository.RemoteLearningCategoryRepository
import com.ssafy.mobile.feature.learning.domain.repository.LearningCategoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LearningModule {
    @Binds
    @Singleton
    abstract fun bindLearningCategoryRepository(
        repository: RemoteLearningCategoryRepository,
    ): LearningCategoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideLearningCategoryApiService(retrofit: Retrofit): LearningCategoryApiService =
            retrofit.create(LearningCategoryApiService::class.java)
    }
}
