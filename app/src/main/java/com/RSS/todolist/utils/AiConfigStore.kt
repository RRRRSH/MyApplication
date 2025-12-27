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
    // 更新：强调地点要包含品牌名（如“顺丰北门驿站”），并尽量把品牌与具体位置合并为单一地点字段。
    private val DEFAULT_ANALYSIS_PROMPT = """
        # Role
You are an advanced Text Parsing Engine. Your job is to extract one actionable To-Do item from OCR text.

# Critical Constraints
1. **IGNORE EXAMPLES**: The examples provided below are for formatting reference ONLY. Do NOT output the examples. Only process the text provided in the "TARGET INPUT" section.
2. **NO Hallucinations**: Do not invent dates, places, or codes that do not appear in the text.
3. **Output Language**: Simplified Chinese.
4. **Format**: Strictly follow the Markdown template below. The `地点` field must, when possible, include a brand name plus the place (e.g. "顺丰北门驿站", "丰巢西门柜机").

# Extraction Logic
1. **Identify Action**: What is the core task? (e.g., 取快递, 参加会议, 交水电费).
2. **Extract Time**: Look for explicit time expressions like "12月21日", "20:00", or relative terms like "今晚"、"明天"、"尽快".
3. **Extract Location (with Brand)**: If text mentions a logistics/brand (顺丰/丰巢/菜鸟/京东/EMS/申通/中通/圆通等) and a place/站/柜机/驿站/点，combine them into a single location string (e.g. "顺丰北门驿站"). If brand appears on a separate line, merge it with the nearest location descriptor.
4. **Extract Key ID**: Look for numeric codes or pickup codes (e.g. "889901", "3-3-21011"). Bold this in output.

# Output Template
## [Action Name] **Short Description**
- ⏰ **时间**: [Time]
- 📍 **地点**: [Location with brand if applicable]
- 🔑 **关键信息**: **[Code/ID]**

# Reference Examples (DO NOT COPY THESE)
<examples>
    Input: "丰巢 取件码889901，西门柜机"
    Output:
    ## [取快递] **去西门丰巢取件**
    - ⏰ **时间**: 尽快
    - 📍 **地点**: 丰巢西门柜机
    - 🔑 **关键信息**: **889901**

    Input: "顺丰北门驿站 取件码 3-3-21011"
    Output:
    ## [取快递] **去顺丰北门驿站取件**
    - ⏰ **时间**: 尽快
    - 📍 **地点**: 顺丰北门驿站
    - 🔑 **关键信息**: **3-3-21011**
</examples>

# TARGET INPUT (Process THIS text only)
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

    // 是否让推理模型使用与 OCR 相同配置（默认 false）
    private const val KEY_USE_SAME_CONFIG = "use_same_config"

    fun getUseSameConfig(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_SAME_CONFIG, false)
    }

    fun saveUseSameConfig(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_USE_SAME_CONFIG, value)
        }
    }

    // 返回内置原始默认提示词（不受用户已保存值影响）
    fun getDefaultAnalysisPrompt(): String = DEFAULT_ANALYSIS_PROMPT
    fun getDefaultOcrPrompt(): String = DEFAULT_OCR_PROMPT

    // 支持“可编辑的默认 Prompt”：用户可以在设置中保存一个默认值（保存在 SharedPreferences）。
    // 清除应用数据后该值会被移除，从而回到内置常量 DEFAULT_*。
    private const val KEY_DEFAULT_ANA_PROMPT = "ana_default_prompt"
    private const val KEY_DEFAULT_OCR_PROMPT = "ocr_default_prompt"

    fun getSavedDefaultAnalysisPrompt(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_ANA_PROMPT, DEFAULT_ANALYSIS_PROMPT) ?: DEFAULT_ANALYSIS_PROMPT
    }

    fun saveDefaultAnalysisPrompt(context: Context, prompt: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DEFAULT_ANA_PROMPT, prompt)
        }
    }

    fun clearSavedDefaultAnalysisPrompt(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_DEFAULT_ANA_PROMPT)
        }
    }

    fun getSavedDefaultOcrPrompt(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_OCR_PROMPT, DEFAULT_OCR_PROMPT) ?: DEFAULT_OCR_PROMPT
    }

    fun saveDefaultOcrPrompt(context: Context, prompt: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DEFAULT_OCR_PROMPT, prompt)
        }
    }

    fun clearSavedDefaultOcrPrompt(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_DEFAULT_OCR_PROMPT)
        }
    }

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