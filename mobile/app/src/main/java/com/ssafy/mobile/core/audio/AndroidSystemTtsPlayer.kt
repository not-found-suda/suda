package com.ssafy.mobile.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 시스템 내장 TextToSpeech 엔진을 사용한 구현체.
 */
@Singleton
class AndroidSystemTtsPlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TtsPlayer {
        private var tts: TextToSpeech? = null
        private var isInitialized = false
        private var pendingRequest: PendingRequest? = null

        private data class PendingRequest(
            val text: String,
            val onComplete: () -> Unit,
            val onError: (String) -> Unit,
        )

        init {
            tts =
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.KOREAN
                        isInitialized = true
                        // 초기화 전 들어온 요청이 있다면 실행
                        pendingRequest?.let { request ->
                            speak(request.text, request.onComplete, request.onError)
                            pendingRequest = null
                        }
                    } else {
                        // 초기화 실패 시 대기 중인 요청에 에러 알림
                        pendingRequest?.let { request ->
                            request.onError("TTS 엔진 초기화에 실패했습니다.")
                            pendingRequest = null
                        }
                    }
                }
        }

        override fun speak(
            text: String,
            onComplete: () -> Unit,
            onError: (String) -> Unit,
        ) {
            if (!isInitialized) {
                pendingRequest = PendingRequest(text, onComplete, onError)
                return
            }

            val utteranceId = UUID.randomUUID().toString()

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // no-op
                    }

                    override fun onDone(utteranceId: String?) {
                        onComplete()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onError("TTS 재생 중 오류가 발생했습니다.")
                    }
                },
            )

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }

        override fun stop() {
            pendingRequest = null
            tts?.stop()
        }

        override fun release() {
            isInitialized = false
            pendingRequest = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
