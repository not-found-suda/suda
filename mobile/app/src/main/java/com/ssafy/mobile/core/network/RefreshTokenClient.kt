package com.ssafy.mobile.core.network

import com.ssafy.mobile.core.network.dto.RefreshRequestDto
import com.ssafy.mobile.core.network.dto.RefreshResponseDto
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

interface RefreshTokenClient {
    suspend fun refresh(refreshToken: String): RefreshResponseDto?
}

class RetrofitRefreshTokenClient(
    private val noAuthRetrofit: Retrofit,
) : RefreshTokenClient {
    private interface RefreshApiService {
        @POST("v1/auth/refresh")
        suspend fun refresh(
            @Body request: RefreshRequestDto,
        ): Response<RefreshResponseDto>
    }

    private val apiService = noAuthRetrofit.create(RefreshApiService::class.java)

    override suspend fun refresh(refreshToken: String): RefreshResponseDto? =
        try {
            val response = apiService.refresh(RefreshRequestDto(refreshToken))
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            android.util.Log.e(
                "RefreshTokenClient",
                "Network error during refresh: ${e.message}",
                e,
            )
            null
        } catch (e: IllegalStateException) {
            android.util.Log.e(
                "RefreshTokenClient",
                "Invalid state during refresh: ${e.message}",
                e,
            )
            null
        } catch (e: com.google.gson.JsonSyntaxException) {
            android.util.Log.e(
                "RefreshTokenClient",
                "Response parsing failed: ${e.message}",
                e,
            )
            null
        }
}
