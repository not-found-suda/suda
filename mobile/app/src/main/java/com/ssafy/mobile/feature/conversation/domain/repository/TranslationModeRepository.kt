package com.ssafy.mobile.feature.conversation.domain.repository

import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import kotlinx.coroutines.flow.Flow

interface TranslationModeRepository {
    val translationMode: Flow<TranslationMode>

    suspend fun saveTranslationMode(mode: TranslationMode)
}
