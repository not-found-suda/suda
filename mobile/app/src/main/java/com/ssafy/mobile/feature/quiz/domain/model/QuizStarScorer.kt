package com.ssafy.mobile.feature.quiz.domain.model

object QuizStarScorer {
    fun score(
        targetWord: String,
        sttText: String,
    ): Int {
        val target = targetWord.normalizedForScoring()
        val spoken = sttText.normalizedForScoring()

        return when {
            target.isBlank() || spoken.isBlank() -> ONE_STAR
            spoken == target || sttText.containsTargetToken(target) -> THREE_STARS
            target.length >= MIN_CONTAINED_TARGET_LENGTH && spoken.contains(target) -> THREE_STARS
            target.length < MIN_PARTIAL_TARGET_LENGTH -> ONE_STAR
            isCloseEnough(target, spoken) -> TWO_STARS
            else -> ONE_STAR
        }
    }

    private fun isCloseEnough(
        target: String,
        spoken: String,
    ): Boolean {
        val editDistanceLimit =
            maxOf(
                MIN_EDIT_DISTANCE_LIMIT,
                target.length / EDIT_DISTANCE_DIVISOR,
            )
        if (target.editDistanceTo(spoken) <= editDistanceLimit) return true

        val overlapCount = target.toSet().count { it in spoken.toSet() }
        val overlapLimit = maxOf(MIN_OVERLAP_COUNT, target.length / OVERLAP_DIVISOR)
        return overlapCount >= overlapLimit
    }

    private fun String.normalizedForScoring(): String =
        trim()
            .lowercase()
            .filterNot { it.isWhitespace() || it in IGNORED_CHARACTERS }

    private fun String.containsTargetToken(target: String): Boolean =
        trim()
            .splitToSequence(WHITESPACE_REGEX)
            .map { it.normalizedForScoring() }
            .any { it == target }

    private fun String.editDistanceTo(other: String): Int {
        val previous = IntArray(other.length + 1) { it }
        val current = IntArray(other.length + 1)

        for (i in indices) {
            current[0] = i + 1
            for (j in other.indices) {
                val cost = if (this[i] == other[j]) 0 else 1
                current[j + 1] =
                    minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + cost,
                    )
            }
            current.copyInto(previous)
        }

        return previous[other.length]
    }

    private const val ONE_STAR = 1
    private const val TWO_STARS = 2
    private const val THREE_STARS = 3
    private const val MIN_CONTAINED_TARGET_LENGTH = 2
    private const val MIN_PARTIAL_TARGET_LENGTH = 2
    private const val MIN_EDIT_DISTANCE_LIMIT = 1
    private const val EDIT_DISTANCE_DIVISOR = 3
    private const val MIN_OVERLAP_COUNT = 1
    private const val OVERLAP_DIVISOR = 2
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val IGNORED_CHARACTERS = setOf('.', ',', '!', '?', '~')
}
