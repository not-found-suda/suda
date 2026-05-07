package com.ssafy.mobile.feature.learning.di

import com.ssafy.mobile.feature.learning.data.api.LearningCategoryApiService
import com.ssafy.mobile.feature.learning.data.api.LearningQuizApiService
import com.ssafy.mobile.feature.learning.data.api.LearningWordApiService
import com.ssafy.mobile.feature.learning.data.repository.RemoteLearningCategoryRepository
import com.ssafy.mobile.feature.learning.data.repository.RemoteLearningQuizRepository
import com.ssafy.mobile.feature.learning.data.repository.RemoteLearningWordRepository
import com.ssafy.mobile.feature.learning.domain.repository.LearningCategoryRepository
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizRepository
import com.ssafy.mobile.feature.learning.domain.repository.LearningWordRepository
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

    @Binds
    @Singleton
    abstract fun bindLearningWordRepository(
        repository: RemoteLearningWordRepository,
    ): LearningWordRepository

    @Binds
    @Singleton
    abstract fun bindLearningQuizRepository(
        repository: RemoteLearningQuizRepository,
    ): LearningQuizRepository

    companion object {
        @Provides
        @Singleton
        fun provideLearningCategoryApiService(retrofit: Retrofit): LearningCategoryApiService =
            retrofit.create(LearningCategoryApiService::class.java)

        @Provides
        @Singleton
        fun provideLearningWordApiService(retrofit: Retrofit): LearningWordApiService =
            retrofit.create(LearningWordApiService::class.java)

        @Provides
        @Singleton
        fun provideLearningQuizApiService(retrofit: Retrofit): LearningQuizApiService =
            retrofit.create(LearningQuizApiService::class.java)
    }
}
