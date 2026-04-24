package com.ssafy.mobile.feature.sample.data.repository

import com.ssafy.mobile.feature.sample.data.local.db.SampleTodoDao
import com.ssafy.mobile.feature.sample.data.local.db.toModel
import com.ssafy.mobile.feature.sample.data.local.preference.SamplePreferenceDataSource
import com.ssafy.mobile.feature.sample.data.remote.SampleApiService
import com.ssafy.mobile.feature.sample.data.remote.toEntity
import com.ssafy.mobile.feature.sample.data.remote.toModel
import com.ssafy.mobile.feature.sample.domain.model.SampleTodo
import com.ssafy.mobile.feature.sample.domain.repository.SampleLocalSnapshot
import com.ssafy.mobile.feature.sample.domain.repository.SampleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class DefaultSampleRepository(
    private val apiService: SampleApiService,
    private val sampleTodoDao: SampleTodoDao,
    private val preferenceDataSource: SamplePreferenceDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SampleRepository {
    override val localSnapshot: Flow<SampleLocalSnapshot> =
        combine(
            sampleTodoDao.observeLatestTodo(),
            preferenceDataSource.lastSyncAt,
            preferenceDataSource.lastSyncedTodoId,
        ) { latestTodo, lastSyncAt, lastSyncedTodoId ->
            SampleLocalSnapshot(
                todo = latestTodo?.toModel(),
                lastSyncAt = lastSyncAt,
                lastSyncedTodoId = lastSyncedTodoId,
            )
        }

    override suspend fun refreshSampleTodo(todoId: Int): Result<SampleTodo> =
        withContext(ioDispatcher) {
            runCatching {
                val response = apiService.getSampleTodo(todoId)
                val syncedAt = System.currentTimeMillis()

                sampleTodoDao.upsert(response.toEntity(savedAt = syncedAt))
                preferenceDataSource.updateSyncInfo(todoId = response.id, syncedAt = syncedAt)

                response.toModel()
            }
        }
}
