package com.ssafy.mobile.feature.learning.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingLearningQuizAnswerSubmissionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun pendingLearningQuizAnswerSubmissionDao(): PendingLearningQuizAnswerSubmissionDao

    companion object {
        private const val DATABASE_NAME = "learning.db"

        @Volatile
        private var instance: LearningDatabase? = null

        fun getInstance(context: Context): LearningDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context = context.applicationContext,
                        klass = LearningDatabase::class.java,
                        name = DATABASE_NAME,
                    ).build()
                    .also { instance = it }
            }
    }
}
