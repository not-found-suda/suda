package com.ssafy.mobile.feature.report.domain.model

data class ReportFilterState(
    val from: String? = null,
    val to: String? = null,
    val categoryId: Long? = null,
    val difficulty: String? = null,
    val status: String? = null,
    val minAttemptCount: Int? = null,
)
