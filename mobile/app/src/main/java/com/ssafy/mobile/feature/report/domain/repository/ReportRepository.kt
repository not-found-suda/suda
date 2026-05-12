package com.ssafy.mobile.feature.report.domain.repository

import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgressPage
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage

interface ReportRepository {
    suspend fun getSummary(childId: Long): Result<ReportSummary>

    suspend fun getWeakWords(
        childId: Long,
        page: Int,
        size: Int,
    ): Result<ReportWeakWordPage>

    suspend fun getCategoryProgress(
        childId: Long,
        from: String? = null,
        to: String? = null,
    ): Result<ReportCategoryProgressPage>
}
