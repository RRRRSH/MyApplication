package com.RSS.todolist.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import android.graphics.BitmapFactory
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import com.RSS.todolist.R
import com.RSS.todolist.data.*
import com.RSS.todolist.utils.AiConfigStore
import com.RSS.todolist.utils.ImageUtils
import com.RSS.todolist.utils.TaskExtraction
import com.RSS.todolist.utils.TaskStore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    
    private var retryCount = 0
    private val MAX_RETRY = 5 // 增加重试次数

    // 🌟 新增：后台处理线程，专门干脏活累活
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            when (intent?.action) {
                ACTION_CLEAR_TASKS -> {
                    TaskStore.clearTasks(this@ScreenCaptureService)
                    clearTaskNotifications(notificationManager)
                    showTaskNotification()
                }
                ACTION_COMPLETE_TASK -> {
                    val index = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
                    if (index != -1) {
                        // 标记为已完成（数据层）
                        TaskStore.setTaskCompleted(this@ScreenCaptureService, index, true)

                        // 只取消该条任务通知（不清除其它任务通知以避免闪烁）
                        val notifId = NOTIFICATION_ID_START + index
                        try {
                            notificationManager.cancel(notifId)
                        } catch (e: Exception) {
                            Log.w("TodoList", "取消通知失败 id=$notifId", e)
                        }

                        // 更新主通知的计数/文本，但不要重建所有任务通知
                        val tasks = TaskStore.getTasks(this@ScreenCaptureService)
                        val activeCount = tasks.count { !it.isCompleted }
                        val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
                        val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
                        notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)
                    }
                }
                ACTION_REFRESH -> {
                    // 优先处理编辑索引：仅更新该索引通知
                    val editIndex = intent.getIntExtra(EXTRA_EDIT_TASK_INDEX, -1)
                    if (editIndex >= 0) {
                        // 若该任务已被标记完成则取消通知，否则重新发布该条通知
                        val tasks = TaskStore.getTasks(this@ScreenCaptureService)
                        if (editIndex < tasks.size) {
                            val t = tasks[editIndex]
                            val notifId = NOTIFICATION_ID_START + editIndex
                            if (t.isCompleted) {
                                try { notificationManager.cancel(notifId) } catch (e: Exception) { }
                            } else {
                                addSingleTaskNotification(editIndex)
                            }
                            val activeCount = tasks.count { !it.isCompleted }
                            val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
                            val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
                            notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)
                            return
                        }
                    }

                    // 如果携带了新任务索引，则只添加该条通知，避免清空重建所有通知造成闪烁
                    val newIndex = intent.getIntExtra(EXTRA_NEW_TASK_INDEX, -1)
                    if (newIndex >= 0) {
                        addSingleTaskNotification(newIndex)
                        // 更新主通知计数
                        val tasks = TaskStore.getTasks(this@ScreenCaptureService)
                        val activeCount = tasks.count { !it.isCompleted }
                        val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
                        val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
                        notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)
                    } else {
                        showTaskNotification()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_INIT = "com.RSS.todolist.ACTION_INIT"
        const val ACTION_CLEAR_TASKS = "com.RSS.todolist.ACTION_CLEAR_TASKS"
        const val ACTION_COMPLETE_TASK = "com.RSS.todolist.ACTION_COMPLETE_TASK"
        const val ACTION_REFRESH = "com.RSS.todolist.ACTION_REFRESH"
        const val EXTRA_TASK_INDEX = "extra_task_index"
        const val EXTRA_NEW_TASK_INDEX = "extra_new_task_index"
        const val EXTRA_EDIT_TASK_INDEX = "extra_edit_task_index"
        
        const val NOTIFICATION_ID_MAIN = 1
        const val NOTIFICATION_ID_START = 100
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w("TodoList", "MediaProjection 被系统强制停止")
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // 🌟 1. 启动后台线程
        backgroundThread = HandlerThread("ScreenCaptureThread")
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)

        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_CLEAR_TASKS)
            addAction(ACTION_COMPLETE_TASK)
            addAction(ACTION_REFRESH) 
        }
        registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        showTaskNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionReceiver)
        stopCapture()
        // 🌟 退出后台线程
        backgroundThread.quitSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_INIT) {
            showTaskNotification()
            return START_STICKY
        }

        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>("DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            Log.d("TodoList", "权限校验成功，准备截屏...")

            // 前台服务必须在主线程启动
            val notification = createMainNotification("正在处理截屏...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_MAIN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID_MAIN, notification)
            }

            // 获取屏幕参数
            val metrics = DisplayMetrics()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenDensity = metrics.densityDpi
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
            
            // 回调依然可以发回主线程，这不影响
            mediaProjection?.registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))

            // 🌟 2. 将截屏逻辑扔给后台线程执行
            backgroundHandler.post {
                startCapture()
            }
        } else {
            showTaskNotification()
        }
        return START_STICKY
    }
    
    // 🌟 此方法现在运行在后台线程
    private fun startCapture() {
        try {
            Log.d("TodoList", "后台线程：创建虚拟屏幕...")
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            retryCount = 0
            // 延时也在后台线程排队
            backgroundHandler.postDelayed({ captureAndAnalyze() }, 1000)
        } catch (e: Exception) {
            Log.e("TodoList", "创建虚拟屏幕失败", e)
            stopCapture()
        }
    }
    
    // 🌟 此方法现在运行在后台线程 (最耗时的部分)
    private fun captureAndAnalyze() {
        Log.d("TodoList", "后台线程：尝试获取图片...")
        val image = imageReader?.acquireLatestImage()
        
        if (image != null) {
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                // ⚠️ 极其耗时的 Bitmap 操作，以前就是这里卡死了主线程
                var bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                
                image.close()
                stopCapture() // 拿到图就可以关了

                // 👇👇👇 🌟 新增：保存图片用于调试 (DEBUG) 👇👇👇
                try {
                    // 图片会保存在：/data/data/com.RSS.todolist/cache/debug_screenshot.jpg
                    val file = java.io.File(cacheDir, "debug_screenshot.jpg")
                    val out = java.io.FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    out.flush()
                    out.close()
                    Log.w("TodoList", "📸 截屏已保存，请检查: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e("TodoList", "保存图片失败", e)
                }
                // 👆👆👆 🌟 新增结束 👆👆👆

                Log.d("TodoList", "图片转换完成，开始上传OCR...")
                updateStatusNotification("正在识别文字...")
                performOcr(bitmap)
            } catch (e: Exception) {
                Log.e("TodoList", "图片处理异常", e)
                image?.close()
                updateStatusNotification("图片处理失败")
            }
        } else {
            if (retryCount < MAX_RETRY) {
                retryCount++
                Log.w("TodoList", "ImageReader 还没准备好，重试 $retryCount...")
                backgroundHandler.postDelayed({ captureAndAnalyze() }, 500) // 缩短重试间隔
            } else {
                Log.e("TodoList", "多次重试失败")
                updateStatusNotification("无法获取屏幕画面")
                stopCapture()
            }
        }
    }

    private fun performOcr(bitmap: Bitmap) {
        val appConfig = AiConfigStore.getConfig(this)
        val ocrConfig = appConfig.ocr

        if (ocrConfig.apiKey.isBlank()) {
            updateStatusNotification("请设置 OCR API Key")
            return
        }

        // OCR 对小字边缘很敏感：默认质量 60 容易糊字。
        // 策略：先用较高质量跑一次；若结果“像摘要/过短/单行”，再用更高质量重试一次。
        performOcrAttempt(bitmap, attempt = 1)
    }

    private fun performOcrAttempt(bitmap: Bitmap, attempt: Int) {
        val appConfig = AiConfigStore.getConfig(this)
        val ocrConfig = appConfig.ocr
        val userPromptFromSettings = AiConfigStore.getOcrPrompt(this)

        // 强约束：要求输出被 <OCR>...</OCR> 包裹，便于我们提取正文并识别“摘要式输出”
        val strictSuffix = """

IMPORTANT:
- You are doing OCR. Output ONLY the raw text in the image.
- Do NOT describe, summarize, or explain.
- Do NOT translate.
- Preserve line breaks.
- Wrap the final result strictly between tags:
<OCR>
...
</OCR>
""".trimIndent()

        val prompt = if (attempt <= 1) {
            userPromptFromSettings.trim() + "\n\n" + strictSuffix
        } else {
            // 第二次重试：用更短更硬的提示词，避免模型“自作聪明”总结
            """
You are an OCR engine.
Return ONLY the text you can read from the image.
No extra words.
No summary.
No translation.
Preserve line breaks.

<OCR>
...text from image...
</OCR>
""".trimIndent()
        }

        val system = ChatMessage(
            role = "system",
            content = "You are a precise OCR engine. Output raw text only."
        )

        // 注意：Base64 编码很耗时，强制放后台线程，避免偶发卡顿
        backgroundHandler.post {
            val quality = if (attempt <= 1) 85 else 95
            val base64Img = ImageUtils.bitmapToBase64(bitmap, quality = quality)
            val contentPart = ContentPart(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,$base64Img"))
            val textPrompt = ContentPart(type = "text", text = prompt)
            val user = ChatMessage(role = "user", content = listOf(textPrompt, contentPart))
            val request = ChatRequest(model = ocrConfig.modelName, messages = listOf(system, user))

            AiNetwork.createService(ocrConfig).chat(request).enqueue(object : Callback<ChatResponse> {
                override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                    val raw = response.body()?.choices?.firstOrNull()?.message?.content
                    val extracted = extractOcrText(raw)

                    Log.w("TodoList", "OCR 原始返回内容: [$raw]")
                    Log.w("TodoList", "OCR 提取后内容: [$extracted]")
                    Log.w("TodoList", "OCR 文本长度: ${extracted.length} (attempt=$attempt, jpegQ=$quality)")

                    if (extracted.isBlank() || extracted.length <= 5) {
                        Log.e("TodoList", "OCR 结果太短或为空，视为识别失败")
                        updateStatusNotification("未识别到有效文字")
                        return
                    }

                    val looksIncomplete = extracted.lines().count { it.isNotBlank() } <= 1 && extracted.length < 180

                    // 识别到“摘要式输出/疑似不完整”则自动重试一次
                    if (attempt == 1 && (isLikelyOcrSummary(extracted) || looksIncomplete)) {
                        Log.w("TodoList", "OCR 看起来异常(摘要/不完整)，自动重试一次")
                        updateStatusNotification("OCR 结果异常，正在重试...")
                        performOcrAttempt(bitmap, attempt = 2)
                        return
                    }

                    performAnalysis(extracted)
                }

                override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                    Log.e("TodoList", "OCR 网络错误", t)
                    updateStatusNotification("网络错误: ${t.message}")
                }
            })
        }
    }

    private fun extractOcrText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val normalized = raw.replace("\r\n", "\n").trim()

        // 优先从 <OCR>...</OCR> 中提取
        val tagMatch = Regex("(?s)<OCR>\\s*(.*?)\\s*</OCR>").find(normalized)
        val inside = tagMatch?.groupValues?.getOrNull(1) ?: normalized

        // 去掉常见的包装引号
        val dequoted = inside.removeSurrounding("\"", "\"").trim()

        // 过滤极常见的“包装描述行”
        val dropLinePatterns = listOf(
            Regex("^here'?s\\s+a\\s+text\\s+message.*", RegexOption.IGNORE_CASE),
            Regex("^the\\s+time\\s+is\\s+.*", RegexOption.IGNORE_CASE),
            Regex("^the\\s+text\\s+message\\s+indicates.*", RegexOption.IGNORE_CASE)
        )
        val cleaned = dequoted.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull ""
            if (dropLinePatterns.any { it.matches(t) }) return@mapNotNull null
            t.trim('"')
        }.joinToString("\n").trim()

        return cleaned
    }

    private fun isLikelyOcrSummary(text: String): Boolean {
        val low = text.lowercase()
        // 典型“摘要口吻”关键词
        val triggers = listOf(
            "indicates that",
            "the text message",
            "this message",
            "suggests that",
            "here's a text message",
            "the time is"
        )
        if (triggers.any { low.contains(it) }) return true

        // 如果整体看起来像一句解释（缺少换行/多样字符），也倾向判为摘要
        val hasLineBreak = text.contains("\n")
        val hasDigits = text.any { it.isDigit() }
        val hasPunctuation = text.any { it in listOf(':', '：', ',', '，') }
        if (!hasLineBreak && hasDigits && !hasPunctuation && text.length < 180) return true

        return false
    }

    private fun performAnalysis(ocrText: String) {
        updateStatusNotification("正在智能分析...")
        val appConfig = AiConfigStore.getConfig(this)
        val anaConfig = appConfig.analysis

        if (anaConfig.apiKey.isBlank()) {
            updateStatusNotification("请设置分析模型 API Key")
            return
        }

        // 使用可配置的 prompt（可在设置页修改），并将 OCR 文本追加到模板末尾
        val template = AiConfigStore.getAnalysisPrompt(this)
        val prompt = buildString {
            append(template)
            append("\n\n待处理文字：\n")
            append(TaskExtraction.formatMultiMessageInput(ocrText))
        }

        val message = ChatMessage(role = "user", content = prompt)
        val request = ChatRequest(model = anaConfig.modelName, messages = listOf(message))

        AiNetwork.createService(anaConfig).chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content
                if (raw.isNullOrBlank()) {
                    updateStatusNotification("分析无结果")
                    return
                }

                val extracted = TaskExtraction.extractTasksFromModelOutput(raw)
                if (extracted.isEmpty()) {
                    showTaskNotification()
                    return
                }

                Log.d("TodoList", "AI 分析成功(多任务): ${extracted.size} 条")

                // Retrofit 回调通常在主线程；批量写入与通知生成放到后台线程，降低卡顿
                backgroundHandler.post {
                    val range = TaskStore.addTasks(this@ScreenCaptureService, extracted)
                    if (range == null) {
                        showTaskNotification()
                        return@post
                    }

                    // 逐条发布任务通知（增量，不清空其它通知）
                    range.forEach { addSingleTaskNotification(it) }

                    // 更新主通知计数
                    val tasks = TaskStore.getTasks(this@ScreenCaptureService)
                    val activeCount = tasks.count { !it.isCompleted }
                    val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
                    val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                updateStatusNotification("分析失败: ${t.message}")
            }
        })
    }

    private data class ParsedTask(
        val title: String,
        val time: String,
        val location: String,
        val key: String
    )

    private fun parseTaskMarkdown(rawText: String, fallbackTitle: String): ParsedTask {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val titleLine = lines.firstOrNull() ?: fallbackTitle
        val title = titleLine
            .replace(Regex("^##\\s*"), "")
            .replace("**", "")
            .trim()

        fun cleanValue(v: String): String {
            return v
                .replace("**", "")
                .replace("（", "(")
                .replace("）", ")")
                .trim()
        }

        fun extractAfterColon(line: String): String {
            val cleaned = line.removePrefix("-").trim()
            val idx = cleaned.lastIndexOf(':')
            val idxCn = cleaned.lastIndexOf('：')
            val cut = maxOf(idx, idxCn)
            return if (cut >= 0 && cut + 1 < cleaned.length) cleaned.substring(cut + 1).trim() else cleaned
        }

        var timeStr = ""
        var locationStr = ""
        var keyStr = ""
        var brandStr = ""

        val rest = if (lines.size > 1) lines.subList(1, lines.size) else emptyList()
        val brands = listOf("顺丰", "丰巢", "菜鸟", "京东", "EMS", "申通", "中通", "圆通", "安能")

        for (line in rest) {
            val l = line.removePrefix("-").trim()

            val foundBrand = brands.firstOrNull { l.contains(it, ignoreCase = true) }
            if (foundBrand != null && brandStr.isEmpty()) brandStr = foundBrand

            val low = l.lowercase()
            val hasTimeLabel = l.contains("时间") || l.contains("⏰")
            val hasLocationLabel = l.contains("地点") || l.contains("📍")
            val hasKeyLabel = l.contains("关键信息") || l.contains("🔑") || low.contains("key")

            when {
                hasTimeLabel && timeStr.isEmpty() -> timeStr = cleanValue(extractAfterColon(l))
                hasLocationLabel && locationStr.isEmpty() -> locationStr = cleanValue(extractAfterColon(l))
                hasKeyLabel && keyStr.isEmpty() -> keyStr = cleanValue(extractAfterColon(l))
            }
        }

        // 兜底：旧格式/非标签行
        if (timeStr.isEmpty() || locationStr.isEmpty() || keyStr.isEmpty()) {
            for (line in rest) {
                val l = line.trim()
                val low = l.lowercase()
                val isTime = Regex("\\d{1,2}[:：]\\d{2}").containsMatchIn(l) || l.contains("月") || low.contains("今天") || low.contains("明天") || low.contains("今晚") || low.contains("尽快")
                val looksLikeCode = Regex("[0-9]{2,}-[0-9A-Za-z-]{2,}|[0-9]{4,}").containsMatchIn(l) || Regex("^[0-9A-Za-z-]{4,}$").matches(l)

                if (timeStr.isEmpty() && isTime) timeStr = cleanValue(l)
                if (keyStr.isEmpty() && looksLikeCode) keyStr = cleanValue(l)
                if (locationStr.isEmpty() && !isTime && !looksLikeCode) locationStr = cleanValue(l)
            }
        }

        fun isPlaceholder(v: String): Boolean {
            if (v.isBlank()) return true
            val s = v.replace("**", "").trim()
            return s == "无" || s == "未提及" || s.contains("若无则留空") || s.contains("若文本未给出")
        }

        if (isPlaceholder(timeStr)) timeStr = ""
        if (isPlaceholder(locationStr)) locationStr = ""
        if (isPlaceholder(keyStr)) keyStr = ""

        // 品牌合并到地点（如果地点不包含品牌）
        if (brandStr.isNotEmpty()) {
            if (locationStr.isNotEmpty() && !brands.any { locationStr.contains(it, ignoreCase = true) }) {
                locationStr = brandStr + locationStr
            } else if (locationStr.isEmpty()) {
                locationStr = brandStr
            }
        }

        return ParsedTask(
            title = title,
            time = timeStr,
            location = locationStr,
            key = keyStr
        )
    }

    private fun stopCapture() {
        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("todo_service", "Screen Analysis", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateStatusNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createMainNotification(text)
        notificationManager.notify(NOTIFICATION_ID_MAIN, notification)
    }

    private fun clearTaskNotifications(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifs = manager.activeNotifications
            for (notif in activeNotifs) {
                if (notif.id >= NOTIFICATION_ID_START) {
                    manager.cancel(notif.id)
                }
            }
        } else {
            for (i in NOTIFICATION_ID_START..NOTIFICATION_ID_START + 100) {
                manager.cancel(i)
            }
        }
    }

    private fun showTaskNotification() {
        val tasks = TaskStore.getTasks(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        clearTaskNotifications(notificationManager)

        val activeCount = tasks.count { !it.isCompleted }
        val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
        val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
        
        // 🌟🌟🌟 核心修改：区分状态 🌟🌟🌟
        try {
            // 只有当服务正在进行“截屏”操作时，才加 mediaProjection 类型
            // 平时待机时（比如只是显示任务列表），不要加这个类型，否则 Android 14 会崩溃
            // 既然录屏已经结束，或者只是刷新列表，我们不需要再声明任何特殊类型
            try {
                startForeground(NOTIFICATION_ID_MAIN, mainNotification)
            } catch (e: Exception) {
                // 忽略异常，因为服务已经在运行了，甚至不需要再次 startForeground，直接 notify 就行
                // Log.e("TodoList", "Ignored foreground update error", e)
            }
        } catch (e: Exception) {
            Log.e("TodoList", "StartForeground Error", e)
            // 如果失败，尝试降级启动
            try {
                startForeground(NOTIFICATION_ID_MAIN, mainNotification)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)

        tasks.forEachIndexed { index, task ->
            val notificationId = NOTIFICATION_ID_START + index
            if (!task.isCompleted) {
                val completeIntent = Intent(ACTION_COMPLETE_TASK).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_TASK_INDEX, index)
                }
                val completePendingIntent = PendingIntent.getBroadcast(
                    this, index, completeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val rawText = task.text ?: ""
                val parsed = parseTaskMarkdown(rawText, fallbackTitle = "待办事项 ${index + 1}")
                val title = parsed.title
                val timeStr = parsed.time
                val locationStr = parsed.location
                val keyStr = parsed.key

                // 构建带标签的展开文本（顶部先显示纯标题行，便于展开时一眼看清）
                val contentBuilder = StringBuilder()
                // 顶部显示纯标题（通常为地点或第一行标题）
                contentBuilder.append(title)
                contentBuilder.append("\n\n")
                contentBuilder.append("⏰ 时间: ")
                contentBuilder.append(if (timeStr.isNotEmpty()) timeStr else "尽快")
                contentBuilder.append("\n")
                contentBuilder.append("📍 地点: ")
                contentBuilder.append(locationStr)
                contentBuilder.append("\n")
                contentBuilder.append("🔑 关键信息: ")
                contentBuilder.append(keyStr)

                val bigText = SpannableStringBuilder(contentBuilder.toString())
                if (keyStr.isNotEmpty()) {
                    val full = contentBuilder.toString()
                    val keyLabel = "🔑 关键信息: "
                    val keyStart = full.indexOf(keyLabel)
                    if (keyStart >= 0) {
                        val start = keyStart + keyLabel.length
                        val end = start + keyStr.length
                        bigText.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        bigText.setSpan(RelativeSizeSpan(1.4f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                // 通知：标题显示地点（若无则显示解析到的第一行标题），内容显示关键信息（若无则回退到标题）
                val displayTitle = if (locationStr.isNotBlank()) locationStr else title
                val displayContent = if (keyStr.isNotBlank()) keyStr else title

                val taskNotification = NotificationCompat.Builder(this, "todo_service")
                    .setContentTitle(displayTitle)
                    .setContentText(displayContent)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setLargeIcon(BitmapFactory.decodeResource(resources, com.RSS.todolist.R.drawable.gemini_generated_image))
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .addAction(android.R.drawable.checkbox_on_background, "完成", completePendingIntent)
                    .build()

                notificationManager.notify(notificationId, taskNotification)
            }
        }
    }

    // 仅为单个索引创建并发布通知（不清除其它任务通知）
    private fun addSingleTaskNotification(index: Int) {
        val tasks = TaskStore.getTasks(this)
        if (index < 0 || index >= tasks.size) return
        val task = tasks[index]
        if (task.isCompleted) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val completeIntent = Intent(ACTION_COMPLETE_TASK).apply {
            setPackage(packageName)
            putExtra(EXTRA_TASK_INDEX, index)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            this, index, completeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rawText = task.text ?: ""
        val parsed = parseTaskMarkdown(rawText, fallbackTitle = "待办事项 ${index + 1}")
        val title = parsed.title
        val timeStr = parsed.time
        val locationStr = parsed.location
        val keyStr = parsed.key

        val contentBuilder = StringBuilder()
        contentBuilder.append(title)
        contentBuilder.append("\n\n")
        contentBuilder.append("⏰ 时间: ")
        contentBuilder.append(if (timeStr.isNotEmpty()) timeStr else "尽快")
        contentBuilder.append("\n")
        contentBuilder.append("📍 地点: ")
        contentBuilder.append(locationStr)
        contentBuilder.append("\n")
        contentBuilder.append("🔑 关键信息: ")
        contentBuilder.append(keyStr)

        val bigText = SpannableStringBuilder(contentBuilder.toString())
        if (keyStr.isNotEmpty()) {
            val full = contentBuilder.toString()
            val keyLabel = "🔑 关键信息: "
            val keyStart = full.indexOf(keyLabel)
            if (keyStart >= 0) {
                val start = keyStart + keyLabel.length
                val end = start + keyStr.length
                bigText.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                bigText.setSpan(RelativeSizeSpan(1.4f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val displayTitle = if (locationStr.isNotBlank()) locationStr else title
        val displayContent = if (keyStr.isNotBlank()) keyStr else title

        val taskNotification = NotificationCompat.Builder(this, "todo_service")
            .setContentTitle(displayTitle)
            .setContentText(displayContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(BitmapFactory.decodeResource(resources, com.RSS.todolist.R.drawable.gemini_generated_image))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.checkbox_on_background, "完成", completePendingIntent)
            .build()

        val notificationId = NOTIFICATION_ID_START + index
        notificationManager.notify(notificationId, taskNotification)
    }

    private fun createMainNotification(text: String, showClearButton: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, "todo_service")
            .setContentTitle("TodoList 助手")
            .setContentText(text)
            // smallIcon 使用圆形启动图（显示在状态栏）
            .setSmallIcon(R.mipmap.ic_launcher_round)
            // largeIcon 在展开通知中显示为彩色图片（使用你放入的 drawable）
            .setLargeIcon(BitmapFactory.decodeResource(resources, com.RSS.todolist.R.drawable.gemini_generated_image))
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (showClearButton) {
            val clearIntent = Intent(ACTION_CLEAR_TASKS).apply { setPackage(packageName) }
            val clearPendingIntent = PendingIntent.getBroadcast(this, 0, clearIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(android.R.drawable.ic_menu_delete, "清空所有", clearPendingIntent)
        }
        // 添加截屏触发按钮，点击会启动一个透明 Activity 请求截屏授权并把结果发给 Service
        val captureIntent = Intent(this, com.RSS.todolist.CaptureStarterActivity::class.java)
        val capturePending = PendingIntent.getActivity(this, 999, captureIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.addAction(android.R.drawable.ic_menu_camera, "截屏识别", capturePending)
        return builder.build()
    }
}