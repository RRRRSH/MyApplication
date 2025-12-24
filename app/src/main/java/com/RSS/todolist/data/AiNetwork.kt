package com.RSS.todolist.data

import com.RSS.todolist.utils.AiModelConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AiNetwork {

    // 🌟 这里的参数改成了 AiModelConfig
    fun createService(config: AiModelConfig): OpenAiApi {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // 两个模型可能响应慢，设长一点
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .header("Content-Type", "application/json")

                if (!config.appId.isNullOrBlank()) {
                    requestBuilder.header("X-App-ID", config.appId)
                }

                chain.proceed(requestBuilder.build())
            }

        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        builder.addInterceptor(logging)

        val finalBaseUrl = if (config.baseUrl.endsWith("/")) config.baseUrl else "${config.baseUrl}/"

        val retrofit = Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OpenAiApi::class.java)
    }
}