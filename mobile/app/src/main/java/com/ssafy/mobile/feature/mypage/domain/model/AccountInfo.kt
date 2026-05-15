package com.ssafy.mobile.feature.mypage.domain.model

data class AccountInfo(
    val userId: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val role: String,
    val ttsSpeaker: String?,
)

data class AccountUpdateResult(
    val userId: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val role: String,
    val updatedAt: String?,
)
