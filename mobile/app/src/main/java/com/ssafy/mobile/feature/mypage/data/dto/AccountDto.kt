package com.ssafy.mobile.feature.mypage.data.dto

import com.ssafy.mobile.feature.mypage.domain.model.AccountInfo
import com.ssafy.mobile.feature.mypage.domain.model.AccountUpdateResult

data class AccountInfoResponseDto(
    val userId: Long,
    val email: String,
    val name: String? = null,
    val active: Boolean,
    val role: String,
    val ttsSpeaker: String? = null,
) {
    fun toDomain(): AccountInfo =
        AccountInfo(
            userId = userId,
            email = email,
            name = name.orEmpty(),
            active = active,
            role = role,
            ttsSpeaker = ttsSpeaker,
        )
}

data class AccountUpdateRequestDto(
    val name: String,
)

data class AccountUpdateResponseDto(
    val userId: Long,
    val email: String,
    val name: String? = null,
    val active: Boolean,
    val role: String,
    val updatedAt: String? = null,
) {
    fun toDomain(): AccountUpdateResult =
        AccountUpdateResult(
            userId = userId,
            email = email,
            name = name.orEmpty(),
            active = active,
            role = role,
            updatedAt = updatedAt,
        )
}
