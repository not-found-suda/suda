package com.ssafy.mobile.feature.conversation.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSignSentenceGeneratorTest {
    private val generator = LocalSignSentenceGenerator()

    @Test
    fun `병원 가다를 존댓말 평서문으로 생성한다`() {
        val result =
            generator.generate(
                glosses = listOf("병원", "가다"),
                sentenceType = "평서문",
            )

        assertEquals("병원에 가요.", result)
    }

    @Test
    fun `병원 가다를 존댓말 의문문으로 생성한다`() {
        val result =
            generator.generate(
                glosses = listOf("병원", "가다"),
                sentenceType = "의문문",
            )

        assertEquals("병원에 가나요?", result)
    }

    @Test
    fun `장난감 조심을 안내 문장으로 생성한다`() {
        val result =
            generator.generate(
                glosses = listOf("장난감", "조심"),
                sentenceType = "평서문",
            )

        assertEquals("장난감을 조심하세요.", result)
    }

    @Test
    fun `기차 가다를 자연스러운 이동 문장으로 생성한다`() {
        val result =
            generator.generate(
                glosses = listOf("기차", "가다"),
                sentenceType = "의문문",
            )

        assertEquals("기차를 타러 가나요?", result)
    }

    @Test
    fun `none과 연속 중복 gloss를 제거하고 생성한다`() {
        val result =
            generator.generate(
                glosses = listOf("none", "병원", "병원", "가다"),
                sentenceType = "의문문",
            )

        assertEquals("병원에 가나요?", result)
    }

    @Test
    fun `보통체 문장도 생성한다`() {
        val result =
            generator.generate(
                glosses = listOf("병원", "가다"),
                sentenceType = "의문문",
                speechStyle = LocalSignSentenceGenerator.SpeechStyle.Plain,
            )

        assertEquals("병원에 가?", result)
    }
}
