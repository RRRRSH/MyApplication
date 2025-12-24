package com.RSS.todolist.utils

import android.content.Context
import androidx.core.content.edit

// 单个模型的配置结构
data class AiModelConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val appId: String? = null
)

// 整个 App 的配置结构 (包含两个模型)
data class AppAiConfig(
    val ocr: AiModelConfig,      // 负责看图
    val analysis: AiModelConfig  // 负责思考
)

object AiConfigStore {
    private const val PREF_NAME = "ai_config_pref"
    
    // 默认配置 (示例)
    private const val DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"
    private const val DEFAULT_OCR_MODEL = "qwen-vl-plus"
    private const val DEFAULT_ANALYSIS_MODEL = "qwen-plus"

    fun getConfig(context: Context): AppAiConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // 读取 OCR 配置
        val ocrConfig = AiModelConfig(
            baseUrl = prefs.getString("ocr_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            apiKey = prefs.getString("ocr_api_key", "") ?: "",
            modelName = prefs.getString("ocr_model_name", DEFAULT_OCR_MODEL) ?: DEFAULT_OCR_MODEL,
            appId = prefs.getString("ocr_app_id", null)
        )

        // 读取 分析 配置
        val analysisConfig = AiModelConfig(
            baseUrl = prefs.getString("ana_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            apiKey = prefs.getString("ana_api_key", "") ?: "",
            modelName = prefs.getString("ana_model_name", DEFAULT_ANALYSIS_MODEL) ?: DEFAULT_ANALYSIS_MODEL,
            appId = prefs.getString("ana_app_id", null)
        )

        return AppAiConfig(ocrConfig, analysisConfig)
    }

    fun saveConfig(context: Context, config: AppAiConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            // 保存 OCR
            putString("ocr_base_url", config.ocr.baseUrl)
            putString("ocr_api_key", config.ocr.apiKey)
            putString("ocr_model_name", config.ocr.modelName)
            if (config.ocr.appId.isNullOrBlank()) remove("ocr_app_id") else putString("ocr_app_id", config.ocr.appId)

            // 保存 分析
            putString("ana_base_url", config.analysis.baseUrl)
            putString("ana_api_key", config.analysis.apiKey)
            putString("ana_model_name", config.analysis.modelName)
            if (config.analysis.appId.isNullOrBlank()) remove("ana_app_id") else putString("ana_app_id", config.analysis.appId)
        }
    }
}