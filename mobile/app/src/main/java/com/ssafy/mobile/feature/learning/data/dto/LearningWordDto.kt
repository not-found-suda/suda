package com.ssafy.mobile.feature.learning.data.dto

import com.ssafy.mobile.feature.learning.domain.model.LearningWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LearningWordDto(
    @SerialName("wordId")
    val wordId: Long,
    @SerialName("word")
    val word: String,
    @SerialName("displayText")
    val displayText: String? = null,
    @SerialName("imageUrl")
    val imageUrl: String? = null,
    @SerialName("audioUrl")
    val audioUrl: String? = null,
)

fun LearningWordDto.toDomain(): LearningWord =
    LearningWord(
        id = wordId,
        word = word,
        displayText = displayText,
        imageUrl = imageUrl,
        audioUrl = audioUrl,
    )
