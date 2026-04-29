package com.ssafy.mobile.core.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 안드로이드 시스템 SpeechRecognizer를 사용한 STT 구현체.
 */
@Singleton
class LocalSttEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SttEngine {
        private var speechRecognizer: SpeechRecognizer? = null

        private val _events =
            MutableSharedFlow<SttEvent>(
                extraBufferCapacity = 16,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val events: Flow<SttEvent> = _events.asSharedFlow()

        private val recognizerIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

        override fun startListening() {
            // SpeechRecognizer는 반드시 메인 스레드에서 동작해야 함
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (speechRecognizer == null) {
                    speechRecognizer =
                        SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(createRecognitionListener())
                        }
                }
                speechRecognizer?.startListening(recognizerIntent)
            }
        }

        override fun stopListening() {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                speechRecognizer?.stopListening()
            }
        }

        override fun release() {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }

        private fun createRecognitionListener() =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _events.tryEmit(SttEvent.Started)
                }

                override fun onBeginningOfSpeech() {
                    // 음성 시작 시점 처리 필요 시 구현
                }

                override fun onRmsChanged(rmsdB: Float) {
                    _events.tryEmit(SttEvent.VolumeChanged(rmsdB))
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // 버퍼 수신 시 처리 필요 시 구현
                }

                override fun onEndOfSpeech() {
                    // 음성 종료 시점 처리 필요 시 구현
                }

                override fun onError(error: Int) {
                    val message =
                        when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "오디오 기록 오류"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다"
                            SpeechRecognizer.ERROR_NETWORK -> "네트워크 연결 확인이 필요합니다"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 응답 시간 초과"
                            SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식하지 못했습니다"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "엔진이 사용 중입니다. 잠시 후 다시 시도해 주세요"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "입력이 없어 인식을 종료합니다"
                            else -> "음성 인식 오류 (code: $error)"
                        }
                    _events.tryEmit(SttEvent.Error(message))
                    _events.tryEmit(SttEvent.Stopped)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _events.tryEmit(SttEvent.Results(matches[0]))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION,
                        )
                    if (!matches.isNullOrEmpty()) {
                        _events.tryEmit(SttEvent.PartialResults(matches[0]))
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) {
                    // 추가 이벤트 처리 필요 시 구현
                }
            }
    }
