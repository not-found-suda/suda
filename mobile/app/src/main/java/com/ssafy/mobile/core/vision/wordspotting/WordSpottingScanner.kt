package com.ssafy.mobile.core.vision.wordspotting

import com.ssafy.mobile.core.vision.feature.LandmarkFeatureFrame

interface WordSpottingScanner {
    fun scan(frames: List<LandmarkFeatureFrame>): WordSpottingResult

    fun close()
}

data class WordSpottingResult(
    val isAvailable: Boolean,
    val candidates: List<WordSpottingCandidate> = emptyList(),
) {
    val glosses: List<String> = candidates.map { candidate -> candidate.gloss }
    val text: String = glosses.joinToString(separator = " ")
    val confidence: Float =
        candidates
            .map { candidate -> candidate.score }
            .average()
            .takeIf { value -> !value.isNaN() }
            ?.toFloat()
            ?: 0f
}

data class WordSpottingCandidate(
    val startFrameIndex: Int,
    val endFrameIndex: Int,
    val gloss: String,
    val score: Float,
    val margin: Float,
    val secondGloss: String?,
    val secondScore: Float?,
)

object NoOpWordSpottingScanner : WordSpottingScanner {
    override fun scan(frames: List<LandmarkFeatureFrame>): WordSpottingResult =
        WordSpottingResult(isAvailable = false)

    override fun close() = Unit
}
