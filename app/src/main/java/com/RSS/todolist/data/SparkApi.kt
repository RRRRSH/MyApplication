package com.RSS.todolist.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SparkApi {
    // 统一的 Chat 接口 (无论是 OCR 还是 推理，都走这个)
    @POST("chat/completions")
    fun chat(@Body request: ChatRequest): Call<ChatResponse>
}