package com.ssafy.mobile.feature.sample.domain.repository

import com.ssafy.mobile.feature.sample.domain.model.SampleTodo
import kotlinx.coroutines.flow.Flow

data class SampleLocalSnapshot(
    val todo: SampleTodo?,
    val lastSyncAt: Long?,
    val lastSyncedTodoId: Int?,
)

interface SampleRepository {
    val localSnapshot: Flow<SampleLocalSnapshot>

    suspend fun refreshSampleTodo(todoId: Int = 1): Result<SampleTodo>
}
