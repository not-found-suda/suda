package com.ssafy.mobile.feature.childprofile.domain.model

data class ChildProfile(
    val childId: Long,
    val name: String,
    val birthDate: String,
    val age: Int?,
    val active: Boolean = true,
)
