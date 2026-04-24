package com.ssafy.mobile.feature.sample.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface SampleApiService {
    @GET("todos/{id}")
    suspend fun getSampleTodo(
        @Path("id") id: Int,
    ): SampleTodoResponse
}
