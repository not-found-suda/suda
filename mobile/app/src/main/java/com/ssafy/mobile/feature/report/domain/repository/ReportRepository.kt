package com.ssafy.mobile.feature.report.domain.repository

import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage

interface ReportRepository {
    suspend fun getSummary(childId: Long): Result<ReportSummary>

    suspend fun getWeakWords(
        childId: Long,
        page: Int,
        size: Int,
    ): Result<ReportWeakWordPage>
}
