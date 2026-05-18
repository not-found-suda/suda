package com.ssafy.mobile.feature.conversation.domain.model

import javax.inject.Inject

@Suppress("TooManyFunctions")
class LocalSignSentenceGenerator
    @Inject
    constructor() {
        @Suppress("ReturnCount")
        fun generate(
            glosses: List<String>,
            sentenceType: String? = null,
            speechStyle: SpeechStyle = SpeechStyle.Polite,
        ): String {
            val knownPattern =
                generateKnownPatternOrNull(
                    glosses = glosses,
                    sentenceType = sentenceType,
                    speechStyle = speechStyle,
                )
            if (knownPattern != null) {
                return knownPattern
            }

            val words = glosses.normalize()
            if (words.isEmpty()) return ""

            val mood = SentenceMood.from(sentenceType)
            return renderKnownPattern(words, mood, speechStyle)
                ?: renderFallback(words, mood)
        }

        fun generateKnownPatternOrNull(
            glosses: List<String>,
            sentenceType: String? = null,
            speechStyle: SpeechStyle = SpeechStyle.Polite,
        ): String? {
            val words = glosses.normalize()
            if (words.isEmpty()) return null

            val mood = SentenceMood.from(sentenceType)
            return renderKnownPattern(words, mood, speechStyle)
        }

        @Suppress("MagicNumber", "ReturnCount")
        private fun renderKnownPattern(
            words: List<String>,
            mood: SentenceMood,
            speechStyle: SpeechStyle,
        ): String? {
            if (words.size >= 3 && words.last() == "주다") {
                return renderGivePattern(words, mood, speechStyle)
            }

            if (words.size >= 2) {
                val noun = words[words.lastIndex - 1]
                val predicate = words.last()
                renderBinaryPattern(noun, predicate, mood, speechStyle)?.let { return it }
            }

            return renderSinglePattern(words.last(), mood, speechStyle)
        }

        private fun renderGivePattern(
            words: List<String>,
            mood: SentenceMood,
            speechStyle: SpeechStyle,
        ): String? {
            val recipient = words.first()
            val thing = words[words.lastIndex - 1]
            if (recipient !in recipientNouns || thing !in objectNouns) return null

            val ending = "주다".endingText(mood, speechStyle)
            return "$recipient$RECIPIENT_PARTICLE ${thing.withObjectParticle()} $ending"
                .withSentenceMark(mood)
        }

        private fun renderBinaryPattern(
            noun: String,
            predicate: String,
            mood: SentenceMood,
            speechStyle: SpeechStyle,
        ): String? {
            val ending = predicate.endingText(mood, speechStyle)
            return when (predicate) {
                "가다" ->
                    when (noun) {
                        "병원" ->
                            "$noun$DESTINATION_PARTICLE $ending"
                        "기차" ->
                            "기차를 타러 $ending"
                        else ->
                            "${noun.withSubjectParticle()} $ending"
                    }
                "걷다",
                "웃다",
                ->
                    "${noun.withSubjectParticle()} $ending"
                "돕다",
                "주다",
                ->
                    "${noun.withObjectParticle()} $ending"
                "놀다" ->
                    if (noun == "장난감") {
                        "장난감으로 $ending"
                    } else {
                        "${noun.withSubjectParticle()} $ending"
                    }
                "조심" ->
                    if (mood == SentenceMood.Question) {
                        "${noun.withObjectParticle()} 조심할까요"
                    } else {
                        "${noun.withObjectParticle()} ${speechStyle.cautionEnding}"
                    }
                "아프다",
                "좋다",
                "싫다",
                "무섭다",
                ->
                    "${noun.withSubjectParticle()} $ending"
                else -> null
            }?.withSentenceMark(mood)
        }

        private fun renderSinglePattern(
            gloss: String,
            mood: SentenceMood,
            speechStyle: SpeechStyle,
        ): String? =
            when (gloss) {
                "감사" ->
                    if (mood == SentenceMood.Question) {
                        "고마워요"
                    } else {
                        "감사합니다"
                    }
                "감기" ->
                    if (mood == SentenceMood.Question) {
                        "감기에 걸렸나요"
                    } else {
                        "감기에 걸렸어요"
                    }
                "조심" ->
                    if (mood == SentenceMood.Question) {
                        "조심할까요"
                    } else {
                        speechStyle.cautionEnding
                    }
                else -> gloss.endingTextOrNull(mood, speechStyle)
            }?.withSentenceMark(mood)

        private fun renderFallback(
            words: List<String>,
            mood: SentenceMood,
        ): String =
            words
                .joinToString(separator = " ")
                .withSentenceMark(mood)

        enum class SpeechStyle(
            val cautionEnding: String,
        ) {
            Polite(cautionEnding = "조심하세요"),
            Plain(cautionEnding = "조심해"),
        }

        private enum class SentenceMood {
            Statement,
            Question,
            ;

            companion object {
                fun from(sentenceType: String?): SentenceMood =
                    if (
                        sentenceType?.contains(QUESTION_KEYWORD) == true ||
                        sentenceType.equals(QUESTION_TYPE, ignoreCase = true)
                    ) {
                        Question
                    } else {
                        Statement
                    }
            }
        }

        private data class PredicateEnding(
            val politeStatement: String,
            val politeQuestion: String,
            val plainStatement: String,
            val plainQuestion: String,
        )

        private fun String.endingText(
            mood: SentenceMood,
            speechStyle: SpeechStyle,
        ): String = endingTextOrNull(mood, speechStyle) ?: this

        private fun String.endingTextOrNull(
            mood: SentenceMood,
            speechStyle: SpeechStyle,
        ): String? {
            val ending = predicateEndings[this] ?: return null
            return when (speechStyle) {
                SpeechStyle.Polite ->
                    if (mood == SentenceMood.Question) {
                        ending.politeQuestion
                    } else {
                        ending.politeStatement
                    }
                SpeechStyle.Plain ->
                    if (mood == SentenceMood.Question) {
                        ending.plainQuestion
                    } else {
                        ending.plainStatement
                    }
            }
        }

        private fun String.withSentenceMark(mood: SentenceMood): String {
            val trimmed = trim()
            if (trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")) {
                return trimmed
            }
            return if (mood == SentenceMood.Question) "$trimmed?" else "$trimmed."
        }

        private fun List<String>.normalize(): List<String> =
            map { gloss -> gloss.trim() }
                .filter { gloss -> gloss.isNotBlank() && gloss != NONE_GLOSS }
                .fold(emptyList()) { result, gloss ->
                    if (result.lastOrNull() == gloss) result else result + gloss
                }

        private fun String.withSubjectParticle(): String =
            this + if (hasFinalConsonant()) "이" else "가"

        private fun String.withObjectParticle(): String =
            this + if (hasFinalConsonant()) "을" else "를"

        private fun String.hasFinalConsonant(): Boolean {
            val last = lastOrNull() ?: return false
            val offset = last.code - HANGUL_SYLLABLE_START
            return offset in 0 until HANGUL_SYLLABLE_COUNT &&
                offset % HANGUL_FINAL_COUNT != 0
        }

        private companion object {
            const val NONE_GLOSS = "none"
            const val DESTINATION_PARTICLE = "에"
            const val RECIPIENT_PARTICLE = "에게"
            const val QUESTION_KEYWORD = "의문"
            const val QUESTION_TYPE = "question"
            const val HANGUL_SYLLABLE_START = 0xAC00
            const val HANGUL_SYLLABLE_COUNT = 11172
            const val HANGUL_FINAL_COUNT = 28

            val recipientNouns = setOf("아기", "엄마")
            val objectNouns = setOf("밥", "장난감", "손", "기차", "병원", "감기")

            val predicateEndings =
                mapOf(
                    "걷다" to endings("걸어요", "걸어요", "걸어", "걸어"),
                    "가다" to endings("가요", "가나요", "가", "가"),
                    "주다" to endings("줘요", "주나요", "줘", "줘"),
                    "돕다" to endings("도와요", "도와요", "도와", "도와"),
                    "배고프다" to endings("배고파요", "배고파요", "배고파", "배고파"),
                    "좋다" to endings("좋아요", "좋아요", "좋아", "좋아"),
                    "싫다" to endings("싫어요", "싫어요", "싫어", "싫어"),
                    "놀다" to endings("놀아요", "놀아요", "놀아", "놀아"),
                    "목마르다" to endings("목말라요", "목말라요", "목말라", "목말라"),
                    "무섭다" to endings("무서워요", "무서워요", "무서워", "무서워"),
                    "아프다" to endings("아파요", "아파요", "아파", "아파"),
                    "웃다" to endings("웃어요", "웃어요", "웃어", "웃어"),
                )

            fun endings(
                politeStatement: String,
                politeQuestion: String,
                plainStatement: String,
                plainQuestion: String,
            ): PredicateEnding =
                PredicateEnding(
                    politeStatement = politeStatement,
                    politeQuestion = politeQuestion,
                    plainStatement = plainStatement,
                    plainQuestion = plainQuestion,
                )
        }
    }
