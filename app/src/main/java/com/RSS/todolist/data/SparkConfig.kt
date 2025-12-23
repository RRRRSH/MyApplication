package com.RSS.todolist.data

object SparkConfig {
    // 你的 OpenAI 格式 Key
    const val API_KEY = "sk-wcbEvCTGAMTDwYAQ41Aa1e9f571e434dA96d81C3FeA77a67"

    // 模型 ID
    const val MODEL_QWEN = "xop3qwen1b7" // Qwen 1.7B
    const val MODEL_OCR = "xophunyuanocr" // 混元 OCR

    // MaaS 服务的统一 Base URL (OpenAI 兼容地址)
    // 注意：这里以讯飞 MaaS 的标准地址为例
    const val BASE_URL = "https://maas-api.cn-huabei-1.xf-yun.com/v1/"
}