package com.RSS.todolist.data

// ==================== OpenAI 兼容请求格式 ====================
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String, // "user" 或 "system"
    val content: Any  // 可能是 String (纯文本) 或 List<ContentPart> (带图片)
)

// 用于处理带图片的消息 (多模态/OCR)
data class ContentPart(
    val type: String, // "text" 或 "image_url"
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String   //这里填 data:image/jpeg;base64,{BASE64_CODE}
)

// ==================== OpenAI 兼容响应格式 ====================
data class ChatResponse(
    val id: String,
    val choices: List<Choice>
)

data class Choice(
    val message: MessageContent
)

data class MessageContent(
    val content: String
)