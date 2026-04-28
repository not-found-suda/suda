package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeSignInferenceAdapterTest {
    @Test
    fun returnsSequentialFakePredictions() {
        val adapter = FakeSignInferenceAdapter()

        val result1 = adapter.predict(createSequence())
        val result2 = adapter.predict(createSequence())

        assertEquals("엄마", result1.gloss)
        assertEquals("밥", result2.gloss)
    }

    @Test
    fun rejectsInvalidSequenceSize() {
        assertThrows(IllegalArgumentException::class.java) {
            // 현재 구현상 size 체크 로직이 주석처리되어 있을 수 있으나,
            // 원본 로직의 의도를 존중하여 테스트는 유지하거나
            // 필요시 구현부의 주석을 해제해야 합니다.
            FakeSignInferenceAdapter().predict(FloatArray(1))
        }
    }

    @Test
    fun rejectsPredictionAfterClose() {
        val adapter = FakeSignInferenceAdapter()

        adapter.close()

        assertThrows(IllegalStateException::class.java) {
            adapter.predict(createSequence())
        }
    }

    private fun createSequence(): FloatArray =
        FloatArray(FakeSignInferenceAdapter.EXPECTED_SEQUENCE_INPUT_SIZE)

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
