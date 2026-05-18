package com.ssafy.mobile.core.vision.inference

class FakeSignInferenceAdapter : SignInferenceAdapter {
    private var isClosed = false
    private var currentIndex = 0
    private val fakeGlosses = listOf("엄마", "밥", "먹다", "좋아", "안녕")

    override fun predict(sequence: FloatArray): SignInferenceResult {
        check(!isClosed) { "이미 종료된 fake 수어 추론 어댑터입니다." }
        require(sequence.size == EXPECTED_SEQUENCE_INPUT_SIZE) {
            "Sequence 입력 크기는 $EXPECTED_SEQUENCE_INPUT_SIZE 이어야 합니다."
        }

        val gloss = fakeGlosses[currentIndex]
        currentIndex = (currentIndex + 1) % fakeGlosses.size

        return SignInferenceResult(
            gloss = gloss,
            confidence = DEFAULT_CONFIDENCE,
        )
    }

    override fun close() {
        isClosed = true
    }

    companion object {
        const val DEFAULT_CONFIDENCE = 0.95f
        const val EXPECTED_SEQUENCE_INPUT_SIZE =
            SignModelContract.SEQUENCE_LENGTH * SignModelContract.FEATURE_DIMENSION
    }
}
