package com.ssafy.mobile.data.repository

import android.content.Context
import com.ssafy.mobile.data.local.db.MobileDatabase
import com.ssafy.mobile.data.local.preference.MobilePreferenceDataSource
import com.ssafy.mobile.data.remote.NetworkModule

object RepositoryModule {
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
        val preferenceDataSource = MobilePreferenceDataSource.create(context)

        return DefaultSampleRepository(
            apiService = apiService,
            sampleTodoDao = database.sampleTodoDao(),
            preferenceDataSource = preferenceDataSource
        )
    }
}
