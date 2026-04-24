package com.ssafy.mobile.feature.sample.domain.model

data class SampleTodo(
    val id: Int,
    val userId: Int,
    val title: String,
    val completed: Boolean,
)
