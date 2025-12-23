package com.RSS.todolist.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 简单的 Token 拦截器
    private class TokenInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val request = original.newBuilder()
                // 直接使用 Bearer Token
                .header("Authorization", "Bearer ${SparkConfig.API_KEY}")
                .header("Content-Type", "application/json")
                .method(original.method, original.body)
                .build()
            return chain.proceed(request)
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(TokenInterceptor()) // 挂载拦截器
        .connectTimeout(60, TimeUnit.SECONDS) // OCR 处理可能较慢，给60秒
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(SparkConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: SparkApi = retrofit.create(SparkApi::class.java)
}