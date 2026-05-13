package com.ssafy.mobile.feature.report.domain.repository

import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgressPage
import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionDetail
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionPage
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage

interface ReportRepository {
    suspend fun getSummary(
        childId: Long,
        filter: ReportFilterState = ReportFilterState(),
    ): Result<ReportSummary>

    suspend fun getWeakWords(
        childId: Long,
        page: Int,
        size: Int,
        filter: ReportFilterState = ReportFilterState(),
    ): Result<ReportWeakWordPage>

    suspend fun getCategoryProgress(
        childId: Long,
        filter: ReportFilterState = ReportFilterState(),
    ): Result<ReportCategoryProgressPage>

    suspend fun getQuizSessions(
        childId: Long,
        page: Int,
        size: Int,
        filter: ReportFilterState = ReportFilterState(),
    ): Result<ReportQuizSessionPage>

    suspend fun getQuizSessionDetail(
        childId: Long,
        sessionId: Long,
    ): Result<ReportQuizSessionDetail>
}
