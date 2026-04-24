package com.ssafy.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleTodoDao {
    @Query("SELECT * FROM sample_todo ORDER BY savedAt DESC LIMIT 1")
    fun observeLatestTodo(): Flow<SampleTodoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: SampleTodoEntity)
}
