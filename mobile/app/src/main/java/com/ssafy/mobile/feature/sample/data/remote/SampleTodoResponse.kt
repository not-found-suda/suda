package com.ssafy.mobile.feature.sample.data.remote

import com.ssafy.mobile.feature.sample.data.local.db.SampleTodoEntity
import com.ssafy.mobile.feature.sample.domain.model.SampleTodo

data class SampleTodoResponse(
    val userId: Int,
    val id: Int,
    val title: String,
    val completed: Boolean,
)

fun SampleTodoResponse.toEntity(savedAt: Long): SampleTodoEntity =
    SampleTodoEntity(
        id = id,
        userId = userId,
        title = title,
        completed = completed,
        savedAt = savedAt,
    )

fun SampleTodoResponse.toModel(): SampleTodo =
    SampleTodo(
        id = id,
        userId = userId,
        title = title,
        completed = completed,
    )
