package com.ssafy.mobile.feature.conversation.data.remote.model

import com.google.gson.annotations.SerializedName

data class CommsSessionCreateRequest(
    @SerializedName("childProfileId")
    val childProfileId: Long,
)

data class CommsSessionResponseDto(
    @SerializedName("sessionId")
    val sessionId: Long,
    @SerializedName("userId")
    val userId: Long,
    @SerializedName("childProfileId")
    val childProfileId: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("startedAt")
    val startedAt: String,
    @SerializedName("endedAt")
    val endedAt: String?,
    @SerializedName("messageCount")
    val messageCount: Int,
    @SerializedName("expiresAt")
    val expiresAt: String,
)
