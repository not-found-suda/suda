package com.ssafy.mobile.feature.report.domain.repository

import com.ssafy.mobile.feature.report.domain.model.ReportSummary

interface ReportRepository {
    suspend fun getSummary(childId: Long): Result<ReportSummary>
}
