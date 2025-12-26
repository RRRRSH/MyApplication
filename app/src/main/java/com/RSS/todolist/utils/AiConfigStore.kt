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
    
    // 默认配置 (已替换为调试默认值，发布前请确认并移除敏感信息)
    private const val DEFAULT_BASE_URL = "https://maas-api.cn-huabei-1.xf-yun.com/v1"
    private const val DEFAULT_OCR_MODEL = "xophunyuanocr"
    private const val DEFAULT_ANALYSIS_MODEL = "xop3qwen1b7"
    // 原始默认提示词（用于推理模型提取任务）
    private val DEFAULT_ANALYSIS_PROMPT = """
        你是一个任务提取机器。你的唯一工作是从杂乱的 OCR 文字中提取一条【核心待办】。
        不管原文是中文还是英文，请严格遵守以下步骤：
        1. 🗑️ **丢弃垃圾信息**：无视所有“状态栏时间”、“应用标题”、“人名”、“电量”等。
        2. 🎯 **定位核心**：找到原文中提到的【将来要做的事】和【具体执行时间】。
        3. 🇨🇳 **输出中文**：如果原文是英文，请翻译成简练的中文。
        4. 📝 **固定格式**：输出必须是“[时间] [事件]”。
        """.trimIndent()
    // 调试默认 API Key / App ID（仅用于本地调试）
    private const val DEBUG_DEFAULT_API_KEY = "sk-wcbEvCTGAMTDwYAQ41Aa1e9f571e434dA96d81C3FeA77a67"
    private const val DEBUG_DEFAULT_APP_ID = "9f677afd"

    fun getConfig(context: Context): AppAiConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // 读取 OCR 配置
        val ocrConfig = AiModelConfig(
            baseUrl = prefs.getString("ocr_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            apiKey = prefs.getString("ocr_api_key", DEBUG_DEFAULT_API_KEY) ?: DEBUG_DEFAULT_API_KEY,
            modelName = prefs.getString("ocr_model_name", DEFAULT_OCR_MODEL) ?: DEFAULT_OCR_MODEL,
            appId = prefs.getString("ocr_app_id", DEBUG_DEFAULT_APP_ID)
        )

        // 读取 分析 配置
        val analysisConfig = AiModelConfig(
            baseUrl = prefs.getString("ana_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            apiKey = prefs.getString("ana_api_key", DEBUG_DEFAULT_API_KEY) ?: DEBUG_DEFAULT_API_KEY,
            modelName = prefs.getString("ana_model_name", DEFAULT_ANALYSIS_MODEL) ?: DEFAULT_ANALYSIS_MODEL,
            appId = prefs.getString("ana_app_id", DEBUG_DEFAULT_APP_ID)
        )

        return AppAiConfig(ocrConfig, analysisConfig)
    }

    // Prompt 读取/保存（分析模型用）
    fun getAnalysisPrompt(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString("ana_prompt", DEFAULT_ANALYSIS_PROMPT) ?: DEFAULT_ANALYSIS_PROMPT
    }

    fun saveAnalysisPrompt(context: Context, prompt: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString("ana_prompt", prompt)
        }
    }

    // OCR prompt 默认与存取
    private const val DEFAULT_OCR_PROMPT = "请直接提取图片中的所有文字，不要进行描述，不要翻译，直接输出识别到的内容。"

    fun getOcrPrompt(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString("ocr_prompt", DEFAULT_OCR_PROMPT) ?: DEFAULT_OCR_PROMPT
    }

    fun saveOcrPrompt(context: Context, prompt: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString("ocr_prompt", prompt)
        }
    }

    // 返回内置原始默认提示词（不受用户已保存值影响）
    fun getDefaultAnalysisPrompt(): String = DEFAULT_ANALYSIS_PROMPT
    fun getDefaultOcrPrompt(): String = DEFAULT_OCR_PROMPT

    // 检测当前是否在使用内置的调试默认 Key（仅用于在 UI 上提示）
    fun isUsingDebugDefaults(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ocrKey = prefs.getString("ocr_api_key", DEBUG_DEFAULT_API_KEY) ?: DEBUG_DEFAULT_API_KEY
        val anaKey = prefs.getString("ana_api_key", DEBUG_DEFAULT_API_KEY) ?: DEBUG_DEFAULT_API_KEY
        return ocrKey == DEBUG_DEFAULT_API_KEY || anaKey == DEBUG_DEFAULT_API_KEY
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