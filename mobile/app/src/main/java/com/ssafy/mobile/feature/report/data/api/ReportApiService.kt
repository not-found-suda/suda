package com.ssafy.mobile.feature.report.data.api

import com.ssafy.mobile.feature.report.data.dto.ReportSummaryResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ReportApiService {
    @GET("v1/children/{childId}/reports/summary")
    suspend fun getSummary(
        @Path("childId") childId: Long,
    ): Response<ReportSummaryResponseDto>
}
