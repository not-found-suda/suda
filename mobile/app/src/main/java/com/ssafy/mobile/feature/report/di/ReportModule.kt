package com.ssafy.mobile.feature.report.di

import com.ssafy.mobile.feature.report.data.api.ReportApiService
import com.ssafy.mobile.feature.report.data.repository.RemoteReportRepository
import com.ssafy.mobile.feature.report.domain.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportModule {
    @Binds
    @Singleton
    abstract fun bindReportRepository(repository: RemoteReportRepository): ReportRepository

    companion object {
        @Provides
        @Singleton
        fun provideReportApiService(retrofit: Retrofit): ReportApiService =
            retrofit.create(ReportApiService::class.java)
    }
}
