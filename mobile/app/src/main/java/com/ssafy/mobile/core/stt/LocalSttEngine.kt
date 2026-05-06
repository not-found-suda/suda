package com.ssafy.mobile.core.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android 시스템 SpeechRecognizer를 사용한 로컬 STT 구현체.
 */
@Singleton
class LocalSttEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SttEngine {
        private var speechRecognizer: SpeechRecognizer? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private val sessionIdGenerator = AtomicInteger(INITIAL_SESSION_ID)

        private val _events =
            MutableSharedFlow<SttEvent>(
                extraBufferCapacity = 16,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val events: Flow<SttEvent> = _events.asSharedFlow()

        override fun nextSessionId(): Int = sessionIdGenerator.incrementAndGet()

        private val recognizerIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_KOREAN)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    MINIMUM_LENGTH_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    COMPLETE_SILENCE_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    POSSIBLY_COMPLETE_SILENCE_MS,
                )
            }

        @Suppress("TooGenericExceptionCaught")
        override fun startListening(sessionId: Int) {
            mainHandler.post {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    emitErrorAndStop(
                        sessionId = sessionId,
                        type = SttErrorType.RecognizerUnavailable,
                        message = ERROR_RECOGNIZER_UNAVAILABLE,
                    )
                    return@post
                }

                try {
                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    }
                    speechRecognizer?.setRecognitionListener(createRecognitionListener(sessionId))
                    speechRecognizer?.startListening(recognizerIntent)
                } catch (e: SecurityException) {
                    Log.w(TAG, "로컬 STT 시작 중 마이크 권한 없음", e)
                    emitErrorAndStop(
                        sessionId = sessionId,
                        type = SttErrorType.PermissionRequired,
                        message = ERROR_PERMISSION_REQUIRED,
                    )
                } catch (e: RuntimeException) {
                    Log.w(TAG, "로컬 STT 시작 실패", e)
                    emitErrorAndStop(
                        sessionId = sessionId,
                        type = SttErrorType.StartFailed,
                        message = ERROR_START_FAILED,
                    )
                }
            }
        }

        override fun stopListening() {
            mainHandler.post {
                speechRecognizer?.stopListening()
            }
        }

        override fun release() {
            mainHandler.post {
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }

        private fun emitErrorAndStop(
            sessionId: Int,
            type: SttErrorType,
            message: String,
        ) {
            _events.tryEmit(SttEvent.Error(sessionId, type, message))
            _events.tryEmit(SttEvent.Stopped(sessionId))
        }

        private fun createRecognitionListener(sessionId: Int) =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _events.tryEmit(SttEvent.Started(sessionId))
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    _events.tryEmit(SttEvent.VolumeChanged(sessionId, rmsdB))
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _events.tryEmit(SttEvent.EndOfSpeech(sessionId))
                }

                override fun onError(error: Int) {
                    val errorInfo =
                        when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                SttErrorInfo(
                                    type = SttErrorType.PermissionRequired,
                                    message = ERROR_PERMISSION_REQUIRED,
                                )
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            ->
                                SttErrorInfo(
                                    type = SttErrorType.SpeechTimeout,
                                    message = ERROR_SPEECH_TIMEOUT,
                                )
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                SttErrorInfo(
                                    type = SttErrorType.NoMatch,
                                    message = ERROR_NO_MATCH,
                                )
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                SttErrorInfo(
                                    type = SttErrorType.RecognizerBusy,
                                    message = ERROR_RECOGNIZER_BUSY,
                                )
                            SpeechRecognizer.ERROR_AUDIO ->
                                SttErrorInfo(
                                    type = SttErrorType.Audio,
                                    message = ERROR_AUDIO,
                                )
                            SpeechRecognizer.ERROR_NETWORK ->
                                SttErrorInfo(
                                    type = SttErrorType.Network,
                                    message = ERROR_NETWORK,
                                )
                            else ->
                                SttErrorInfo(
                                    type = SttErrorType.Unknown,
                                    message = "$ERROR_UNKNOWN_PREFIX$error$ERROR_UNKNOWN_SUFFIX",
                                )
                        }
                    emitErrorAndStop(sessionId, errorInfo.type, errorInfo.message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches.isNullOrEmpty()) {
                        emitErrorAndStop(sessionId, SttErrorType.NoMatch, ERROR_NO_MATCH)
                    } else {
                        _events.tryEmit(SttEvent.Results(sessionId, matches.first()))
                        _events.tryEmit(SttEvent.Stopped(sessionId))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION,
                        )
                    if (!matches.isNullOrEmpty()) {
                        _events.tryEmit(SttEvent.PartialResults(sessionId, matches.first()))
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            }

        private data class SttErrorInfo(
            val type: SttErrorType,
            val message: String,
        )

        companion object {
            private const val TAG = "LocalSttEngine"
            private const val INITIAL_SESSION_ID = 0
            private const val LANGUAGE_KOREAN = "ko-KR"
            private const val MAX_RESULTS = 3
            private const val MINIMUM_LENGTH_MS = 800L
            private const val COMPLETE_SILENCE_MS = 700L
            private const val POSSIBLY_COMPLETE_SILENCE_MS = 500L
            private const val ERROR_RECOGNIZER_UNAVAILABLE = "이 기기에서 음성 인식을 사용할 수 없습니다"
            private const val ERROR_PERMISSION_REQUIRED = "마이크 권한이 필요합니다"
            private const val ERROR_START_FAILED = "음성 인식을 시작하지 못했습니다"
            private const val ERROR_SPEECH_TIMEOUT = "입력이 없어 인식을 종료합니다"
            private const val ERROR_NO_MATCH = "음성을 인식하지 못했습니다"
            private const val ERROR_RECOGNIZER_BUSY = "음성 인식 엔진이 사용 중입니다. 잠시 후 다시 시도해 주세요"
            private const val ERROR_AUDIO = "오디오 기록 오류"
            private const val ERROR_NETWORK = "네트워크 연결 확인이 필요합니다"
            private const val ERROR_UNKNOWN_PREFIX = "음성 인식 오류 (code: "
            private const val ERROR_UNKNOWN_SUFFIX = ")"
        }
    }
