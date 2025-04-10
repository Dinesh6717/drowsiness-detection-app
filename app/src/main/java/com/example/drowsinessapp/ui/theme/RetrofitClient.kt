package com.example.drowsinessapp.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.135.40:5000/") // Update with your local IP & Flask port
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: DrowsinessApi = retrofit.create(DrowsinessApi::class.java)
}
