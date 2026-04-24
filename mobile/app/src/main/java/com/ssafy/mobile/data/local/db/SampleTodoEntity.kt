package com.ssafy.mobile.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ssafy.mobile.data.model.SampleTodo

@Entity(tableName = "sample_todo")
data class SampleTodoEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val title: String,
    val completed: Boolean,
    val savedAt: Long
)

fun SampleTodoEntity.toModel(): SampleTodo =
    SampleTodo(
        id = id,
        userId = userId,
        title = title,
        completed = completed
    )
