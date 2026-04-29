package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class SignLabelMapTest {
    @Test
    fun parsesArrayLabelMap() {
        val labelMap = SignLabelMap.parse("""["엄마", "밥", "먹다"]""")

        assertEquals("엄마", labelMap.glossFor(0))
        assertEquals("밥", labelMap.glossFor(1))
        assertEquals("먹다", labelMap.glossFor(2))
    }

    @Test
    fun parsesLabelsPropertyMap() {
        val labelMap = SignLabelMap.parse("""{"labels":["엄마","밥"]}""")

        assertEquals("엄마", labelMap.glossFor(0))
        assertEquals("밥", labelMap.glossFor(1))
    }

    @Test
    fun parsesIndexedObjectMap() {
        val labelMap = SignLabelMap.parse("""{"0":"엄마","7":"좋아"}""")

        assertEquals("엄마", labelMap.glossFor(0))
        assertEquals("좋아", labelMap.glossFor(7))
    }

    @Test
    fun returnsUnknownWhenIndexIsMissing() {
        val labelMap = SignLabelMap.parse("""["엄마"]""")

        assertEquals(SignModelContract.UNKNOWN_GLOSS, labelMap.glossFor(1))
    }
}
