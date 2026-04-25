package com.ssafy.mobile.core.network

import com.ssafy.mobile.core.model.SignTranslateResult
import com.ssafy.mobile.core.model.VoiceTranslateResult

interface TranslateRepository {
    suspend fun translateSign(glosses: List<String>): SignTranslateResult

    suspend fun translateVoice(rawText: String): VoiceTranslateResult
}
