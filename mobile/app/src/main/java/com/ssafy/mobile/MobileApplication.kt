package com.ssafy.mobile

import android.app.Application
import com.ssafy.mobile.feature.learning.data.repository.LearningQuizAnswerSubmissionQueueSyncer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobileApplication : Application() {
    @Inject
    lateinit var learningQuizAnswerSubmissionQueueSyncer: LearningQuizAnswerSubmissionQueueSyncer

    override fun onCreate() {
        super.onCreate()
        learningQuizAnswerSubmissionQueueSyncer.start()
    }
}
