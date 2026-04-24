package com.ssafy.mobile.feature.sample.di

import android.content.Context
import com.ssafy.mobile.core.database.MobileDatabase
import com.ssafy.mobile.core.network.NetworkModule
import com.ssafy.mobile.feature.sample.data.local.preference.SamplePreferenceDataSource
import com.ssafy.mobile.feature.sample.data.repository.DefaultSampleRepository
import com.ssafy.mobile.feature.sample.domain.repository.SampleRepository

object SampleDataModule {
    @Volatile
    private var sampleRepository: SampleRepository? = null

    fun provideSampleRepository(context: Context): SampleRepository =
        sampleRepository ?: synchronized(this) {
            sampleRepository ?: createSampleRepository(context.applicationContext).also {
                sampleRepository = it
            }
        }

    private fun createSampleRepository(context: Context): SampleRepository {
        val database = MobileDatabase.getInstance(context)
        val apiService = NetworkModule.createSampleApiService()
        val preferenceDataSource = SamplePreferenceDataSource.create(context)

        return DefaultSampleRepository(
            apiService = apiService,
            sampleTodoDao = database.sampleTodoDao(),
            preferenceDataSource = preferenceDataSource,
        )
    }
}
