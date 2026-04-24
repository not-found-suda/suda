package com.ssafy.mobile.data.repository

import com.ssafy.mobile.data.local.db.SampleTodoDao
import com.ssafy.mobile.data.local.db.toModel
import com.ssafy.mobile.data.local.preference.MobilePreferenceDataSource
import com.ssafy.mobile.data.model.SampleTodo
import com.ssafy.mobile.data.remote.SampleApiService
import com.ssafy.mobile.data.remote.toEntity
import com.ssafy.mobile.data.remote.toModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class DefaultSampleRepository(
    private val apiService: SampleApiService,
    private val sampleTodoDao: SampleTodoDao,
    private val preferenceDataSource: MobilePreferenceDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SampleRepository {
    override val localSnapshot: Flow<SampleLocalSnapshot> = combine(
        sampleTodoDao.observeLatestTodo(),
        preferenceDataSource.lastSyncAt,
        preferenceDataSource.lastSyncedTodoId
    ) { latestTodo, lastSyncAt, lastSyncedTodoId ->
        SampleLocalSnapshot(
            todo = latestTodo?.toModel(),
            lastSyncAt = lastSyncAt,
            lastSyncedTodoId = lastSyncedTodoId
        )
    }

    override suspend fun refreshSampleTodo(todoId: Int): Result<SampleTodo> = withContext(ioDispatcher) {
        runCatching {
            val response = apiService.getSampleTodo(todoId)
            val syncedAt = System.currentTimeMillis()

            sampleTodoDao.upsert(response.toEntity(savedAt = syncedAt))
            preferenceDataSource.updateSyncInfo(todoId = response.id, syncedAt = syncedAt)

            response.toModel()
        }
    }
}
