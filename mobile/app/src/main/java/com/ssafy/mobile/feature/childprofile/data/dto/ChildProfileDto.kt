package com.ssafy.mobile.feature.childprofile.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile

data class ChildProfileResponseDto(
    @SerializedName("childId") val childId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("birthDate") val birthDate: String,
    @SerializedName("age") val age: Int?,
    @SerializedName("active") val active: Boolean,
) {
    fun toDomain(): ChildProfile =
        ChildProfile(
            childId = childId,
            name = name,
            birthDate = birthDate,
            age = age,
            active = active,
        )
}

data class ChildProfileCreateRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("birthDate") val birthDate: String,
)
