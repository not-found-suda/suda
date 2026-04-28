package com.ssafy.mobile.feature.conversation.di

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A파트의 실제 엔진이 준비되기 전까지 UI와 통신 로직을 테스트하기 위한 가짜 엔진입니다.
 * 1~2초 간격으로 시나리오에 따른 수어 인식 이벤트를 방출합니다.
 */
private const val MODEL_LOADING_DELAY_MS = 1000L
private const val PREDICTION_DELAY_INITIAL_MS = 2000L
private const val PREDICTION_DELAY_NEXT_MS = 1500L
private const val NO_HANDS_DELAY_MS = 3000L
private const val CONFIDENCE_HIGH = 0.98f
private const val CONFIDENCE_MEDIUM = 0.95f

class FakeSignRecognitionEngine
    @Inject
    constructor() : SignRecognitionEngine {
        override val events: Flow<SignRecognitionEvent> =
            flow {
                emit(SignRecognitionEvent.ModelLoading)
                delay(MODEL_LOADING_DELAY_MS)

                while (true) {
                    delay(PREDICTION_DELAY_INITIAL_MS)
                    emit(
                        SignRecognitionEvent.Prediction(
                            "안녕",
                            CONFIDENCE_HIGH,
                            System.currentTimeMillis(),
                        ),
                    )
                    delay(PREDICTION_DELAY_NEXT_MS)
                    emit(
                        SignRecognitionEvent.Prediction(
                            "하세요",
                            CONFIDENCE_MEDIUM,
                            System.currentTimeMillis(),
                        ),
                    )
                    delay(NO_HANDS_DELAY_MS)
                    emit(SignRecognitionEvent.NoHandsDetected)
                }
            }

        override fun start() {
            // Fake 엔진 시작 시뮬레이션
        }

        override fun stop() {
            // Fake 엔진 중지 시뮬레이션
        }
    }
