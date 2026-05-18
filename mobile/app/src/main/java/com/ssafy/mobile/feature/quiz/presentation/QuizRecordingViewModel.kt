package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.ViewModel
import com.ssafy.mobile.core.audio.AndroidAudioRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class QuizRecordingViewModel
    @Inject
    constructor(
        private val audioRecorder: AndroidAudioRecorder,
    ) : ViewModel() {
        private val _status = MutableStateFlow(QuizRecordingStatus.Idle)
        val status: StateFlow<QuizRecordingStatus> = _status.asStateFlow()

        private val _recordedAudio = MutableStateFlow<QuizRecordedAudio?>(null)
        val recordedAudio: StateFlow<QuizRecordedAudio?> = _recordedAudio.asStateFlow()

        private var nextAudioEventId = INITIAL_AUDIO_EVENT_ID

        fun startListening() {
            deletePendingRecordedAudio()
            _recordedAudio.value = null
            val isStarted = audioRecorder.start(QUIZ_AUDIO_FILE_NAME)
            _status.value =
                if (isStarted) {
                    QuizRecordingStatus.Recording
                } else {
                    QuizRecordingStatus.PermissionError
                }
        }

        fun stopListening() {
            if (_status.value != QuizRecordingStatus.Recording) return

            _status.value = QuizRecordingStatus.Processing
            val audioFile = audioRecorder.stop()
            if (audioFile == null) {
                _status.value = QuizRecordingStatus.NoSpeech
            } else {
                _recordedAudio.value =
                    QuizRecordedAudio(
                        file = audioFile,
                        mimeType = QUIZ_AUDIO_MIME_TYPE,
                        eventId = nextAudioEventId++,
                    )
                _status.value = QuizRecordingStatus.Completed
            }
        }

        fun reset() {
            deletePendingRecordedAudio()
            audioRecorder.stop()?.delete()
            _recordedAudio.value = null
            _status.value = QuizRecordingStatus.Idle
        }

        fun consumeRecordedAudio() {
            _recordedAudio.value = null
        }

        override fun onCleared() {
            reset()
            super.onCleared()
        }

        private fun deletePendingRecordedAudio() {
            _recordedAudio.value?.file?.delete()
        }

        companion object {
            private const val INITIAL_AUDIO_EVENT_ID = 1L
            private const val QUIZ_AUDIO_FILE_NAME = "quiz_answer_audio"
            private const val QUIZ_AUDIO_MIME_TYPE = "audio/wav"
        }
    }

data class QuizRecordedAudio(
    val file: File,
    val mimeType: String,
    val eventId: Long,
)
