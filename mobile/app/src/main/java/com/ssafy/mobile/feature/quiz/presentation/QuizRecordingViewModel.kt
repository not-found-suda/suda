package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.ViewModel
import com.ssafy.mobile.core.audio.AndroidAudioRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class QuizRecordingViewModel
    @Inject
    constructor(
        val recorder: AndroidAudioRecorder,
    ) : ViewModel() {
        override fun onCleared() {
            recorder.release()
            super.onCleared()
        }
    }
