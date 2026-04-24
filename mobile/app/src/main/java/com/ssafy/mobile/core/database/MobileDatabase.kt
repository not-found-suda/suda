package com.ssafy.mobile.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ssafy.mobile.feature.sample.data.local.db.SampleTodoDao
import com.ssafy.mobile.feature.sample.data.local.db.SampleTodoEntity

@Database(
    entities = [SampleTodoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun sampleTodoDao(): SampleTodoDao

    companion object {
        private const val DATABASE_NAME = "mobile.db"

        @Volatile
        private var instance: MobileDatabase? = null

        fun getInstance(context: Context): MobileDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context = context.applicationContext,
                        klass = MobileDatabase::class.java,
                        name = DATABASE_NAME,
                    ).build()
                    .also { instance = it }
            }
    }
}
