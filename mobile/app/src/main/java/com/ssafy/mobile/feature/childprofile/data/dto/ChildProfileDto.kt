package com.ssafy.mobile.feature.childprofile.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.childprofile.domain.model.DEFAULT_CHILD_PROFILE_AVATAR_KEY

data class ChildProfileResponseDto(
    @SerializedName("childId") val childId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("birthDate") val birthDate: String,
    @SerializedName("age") val age: Int?,
    @SerializedName("avatarKey") val avatarKey: String? = null,
    @SerializedName("active") val active: Boolean,
) {
    fun toDomain(): ChildProfile =
        ChildProfile(
            childId = childId,
            name = name,
            birthDate = birthDate,
            age = age,
            avatarKey = avatarKey ?: DEFAULT_CHILD_PROFILE_AVATAR_KEY,
            active = active,
        )
}

data class ChildProfileListResponseDto(
    @SerializedName("children")
    val children: List<ChildProfileResponseDto>,
)

data class ChildProfileCreateRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("birthDate") val birthDate: String,
    @SerializedName("avatarKey") val avatarKey: String,
)

data class ChildProfileUpdateRequestDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("birthDate") val birthDate: String? = null,
    @SerializedName("avatarKey") val avatarKey: String? = null,
)
