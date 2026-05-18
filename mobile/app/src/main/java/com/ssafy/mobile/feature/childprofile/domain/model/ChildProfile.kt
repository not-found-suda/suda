package com.ssafy.mobile.feature.childprofile.domain.model

const val DEFAULT_CHILD_PROFILE_AVATAR_KEY = "purple_diamond"

data class ChildProfile(
    val childId: Long,
    val name: String,
    val birthDate: String,
    val age: Int?,
    val avatarKey: String = DEFAULT_CHILD_PROFILE_AVATAR_KEY,
    val active: Boolean = true,
)
