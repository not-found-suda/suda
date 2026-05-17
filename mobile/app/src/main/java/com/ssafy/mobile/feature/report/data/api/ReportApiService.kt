package com.ssafy.mobile.feature.report.data.api

import com.ssafy.mobile.feature.report.data.dto.ReportCategoryProgressListResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportCommunicationSummaryResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportQuizSessionDetailResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportQuizSessionListResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportSummaryResponseDto
import com.ssafy.mobile.feature.report.data.dto.ReportWeakWordListResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface ReportApiService {
    @GET("v1/children/{childId}/reports/summary")
    suspend fun getSummary(
        @Path("childId") childId: Long,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): Response<ReportSummaryResponseDto>

    @GET("v1/children/{childId}/reports/weak-words")
    suspend fun getWeakWords(
        @Path("childId") childId: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): Response<ReportWeakWordListResponseDto>

    @GET("v1/children/{childId}/reports/categories")
    suspend fun getCategoryProgress(
        @Path("childId") childId: Long,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): Response<ReportCategoryProgressListResponseDto>

    @GET("v1/children/{childId}/reports/communication-summary")
    suspend fun getCommunicationSummary(
        @Path("childId") childId: Long,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): Response<ReportCommunicationSummaryResponseDto>

    @GET("v1/children/{childId}/reports/sessions")
    suspend fun getQuizSessions(
        @Path("childId") childId: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): Response<ReportQuizSessionListResponseDto>

    @GET("v1/children/{childId}/reports/sessions/{sessionId}")
    suspend fun getQuizSessionDetail(
        @Path("childId") childId: Long,
        @Path("sessionId") sessionId: Long,
    ): Response<ReportQuizSessionDetailResponseDto>
}
