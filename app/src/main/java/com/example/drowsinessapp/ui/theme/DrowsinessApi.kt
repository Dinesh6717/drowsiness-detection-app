package com.example.drowsinessapp.network

import com.example.drowsinessapp.model.EARRequest
import com.example.drowsinessapp.model.DrowsinessResponse
import com.example.drowsinessapp.model.ImageRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface DrowsinessApi {
    @POST("/check_drowsiness")
    fun checkDrowsiness(@Body request: EARRequest): Call<DrowsinessResponse>
    @POST("/detect_drowsiness")
    fun detectDrowsinessFromImage(@Body request: ImageRequest): Call<DrowsinessResponse>

}
