package com.ssafy.mobile.feature.report.data.api

import com.ssafy.mobile.feature.report.data.dto.ReportCategoryProgressListResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportSummaryResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportWeakWordListResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ReportApiService {
    @GET("v1/children/{childId}/reports/summary")
    suspend fun getSummary(
        @Path("childId") childId: Long,
    ): Response<ReportSummaryResponseDto>

    @GET("v1/children/{childId}/reports/weak-words")
    suspend fun getWeakWords(
        @Path("childId") childId: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<ReportWeakWordListResponseDto>

    @GET("v1/children/{childId}/reports/categories")
    suspend fun getCategoryProgress(
        @Path("childId") childId: Long,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Response<ReportCategoryProgressListResponseDto>
}
