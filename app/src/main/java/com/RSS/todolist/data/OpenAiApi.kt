
package com.RSS.todolist.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// 定义标准的 OpenAI 接口格式
interface OpenAiApi {
    @POST("chat/completions") // 对应 OpenAI 兼容接口的路径
    fun chat(@Body request: ChatRequest): Call<ChatResponse>
}