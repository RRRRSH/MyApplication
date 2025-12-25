package com.RSS.todolist.data

object SparkConfig {
    // 你的 OpenAI 格式 Key
    // 已清除硬编码 Key。请通过应用设置 (`SettingsScreen`) 或安全方式注入 API Key。
    // 例如：在运行设备上通过设置填写，或通过 CI/环境变量在构建时注入。
    const val API_KEY = ""

    // 模型 ID
    const val MODEL_QWEN = "xop3qwen1b7" // Qwen 1.7B
    const val MODEL_OCR = "xophunyuanocr" // 混元 OCR

    // MaaS 服务的统一 Base URL (OpenAI 兼容地址)
    // 注意：这里以讯飞 MaaS 的标准地址为例
    const val BASE_URL = "https://maas-api.cn-huabei-1.xf-yun.com/v1/"
}