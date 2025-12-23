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
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.RSS.todolist.R
import com.RSS.todolist.data.*
import com.RSS.todolist.utils.ImageUtils
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
    private val MAX_RETRY = 3

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
                        TaskStore.setTaskCompleted(this@ScreenCaptureService, index, true)
                        showTaskNotification() 
                    }
                }
                ACTION_REFRESH -> {
                    showTaskNotification()
                }
            }
        }
    }

    companion object {
        const val ACTION_INIT = "com.RSS.todolist.ACTION_INIT" // 🌟 新增：初始化动作
        const val ACTION_CLEAR_TASKS = "com.RSS.todolist.ACTION_CLEAR_TASKS"
        const val ACTION_COMPLETE_TASK = "com.RSS.todolist.ACTION_COMPLETE_TASK"
        const val ACTION_REFRESH = "com.RSS.todolist.ACTION_REFRESH"
        const val EXTRA_TASK_INDEX = "extra_task_index"
        
        const val NOTIFICATION_ID_MAIN = 1
        const val NOTIFICATION_ID_START = 100
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d("TodoList", "MediaProjection 被系统停止")
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_CLEAR_TASKS)
            addAction(ACTION_COMPLETE_TASK)
            addAction(ACTION_REFRESH) 
        }
        registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        // 这里的 showTaskNotification 会调用 startForeground，保证服务不死
        showTaskNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // 🌟 情况1：如果是应用启动时的“初始化”信号
        if (action == ACTION_INIT) {
            Log.d("TodoList", "服务初始化启动 (不截屏)")
            showTaskNotification() // 只要显示通知栏就行了
            return START_STICKY // 关键：让服务粘性存活
        }

        // 🌟 情况2：如果是真正的截屏请求
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>("DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            Log.d("TodoList", "收到权限数据，准备截屏...")
            
            // 为了保险，再次强制更新前台状态
            val notification = createMainNotification("正在处理截屏...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_MAIN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID_MAIN, notification)
            }

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
            mediaProjection?.registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))
            startCapture()
        } else {
            // 其他情况（比如服务意外重启），至少保证通知栏显示出来
            showTaskNotification()
        }
        return START_STICKY
    }
    
    // ... (以下所有方法与之前完全一致，直接保留即可) ...
    private fun startCapture() {
        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay("ScreenCapture", screenWidth, screenHeight, screenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null)
            retryCount = 0
            Handler(Looper.getMainLooper()).postDelayed({ captureAndAnalyze() }, 1000)
        } catch (e: Exception) { stopCapture() }
    }
    private fun captureAndAnalyze() {
        val image = imageReader?.acquireLatestImage()
        if (image != null) {
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth
                var bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                image.close()
                stopCapture()
                updateStatusNotification("正在分析...")
                performOcr(bitmap)
            } catch (e: Exception) { image?.close(); updateStatusNotification("失败") }
        } else { if (retryCount++ < MAX_RETRY) Handler(Looper.getMainLooper()).postDelayed({ captureAndAnalyze() }, 1000) else stopCapture() }
    }
    private fun performOcr(bitmap: Bitmap) {
        val base64Img = ImageUtils.bitmapToBase64(bitmap)
        val contentPart = ContentPart(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,$base64Img"))

        // 🌟 核心修改：增加一个文本 Prompt，强制它进行 OCR
        val textPrompt = ContentPart(type = "text", text = "请直接提取图片中的所有文字，不要进行描述，不要翻译，直接输出识别到的内容。")

        // 把图片和提示词一起发过去
        val message = ChatMessage(role = "user", content = listOf(textPrompt, contentPart))

        val request = ChatRequest(model = SparkConfig.MODEL_OCR, messages = listOf(message))

        RetrofitClient.api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val text = response.body()?.choices?.firstOrNull()?.message?.content
                if (!text.isNullOrEmpty()) {
                    Log.d("TodoList", "OCR 成功: $text")
                    performAnalysis(text)
                } else {
                    Log.e("TodoList", "OCR 结果为空")
                    updateStatusNotification("文字识别失败")
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                Log.e("TodoList", "OCR 网络请求失败", t)
                updateStatusNotification("网络错误")
            }
        })
    }
    private fun performAnalysis(ocrText: String) {
        updateStatusNotification("正在提炼核心任务...")

        // 🌟 针对性优化的 Prompt
        val prompt = """
            你是一个任务提取机器。你的唯一工作是从杂乱的 OCR 文字中提取一条【核心待办】。
            
            不管原文是中文还是英文，请严格遵守以下步骤：
            1. 🗑️ **丢弃垃圾信息**：无视所有“状态栏时间”（如 11:15 AM）、“应用标题”（如 Texting with...）、“人名”、“电量”等。
            2. 🎯 **定位核心**：找到原文中提到的【将来要做的事】和【具体执行时间】以及【具体地点】。
            3. 🇨🇳 **输出中文**：如果原文是英文，请翻译成简练的中文。
            4. 📝 **固定格式**：输出必须是“[时间] [事件] [地点]”。
            
            ---
            学习案例：
            输入："11:15 AM Texting with 123\n11:15 AM I need go to dinner at 20:00"
            输出："20:00 去吃晚餐"
            
            输入："< Back Message\nJohn: Meeting tomorrow 9am"
            输出："明天上午9点 开会"
            
            输入："备忘录\n1. 买咖啡"
            输出："买咖啡"
            ---
            
            待处理文字：
            $ocrText
        """.trimIndent()

        val message = ChatMessage(role = "user", content = prompt)
        val request = ChatRequest(model = SparkConfig.MODEL_QWEN, messages = listOf(message))

        RetrofitClient.api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                var task = response.body()?.choices?.firstOrNull()?.message?.content

                if (!task.isNullOrEmpty()) {
                    // 🧹 二次清洗：有时候模型比较啰嗦，可能会带上 "输出：" 这种前缀
                    task = task.replace("输出：", "")
                        .replace("Output:", "")
                        .replace("Task:", "")
                        .replace("\"", "") // 去掉引号
                        .trim()

                    if (task != "无任务") {
                        Log.d("TodoList", "AI 提炼成功: $task")
                        TaskStore.addTask(this@ScreenCaptureService, task)
                        showTaskNotification()
                    } else {
                        showTaskNotification()
                    }
                } else {
                    showTaskNotification()
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                Log.e("TodoList", "AI 分析失败", t)
                updateStatusNotification("分析失败")
            }
        })
    }
    private fun stopCapture() { try { mediaProjection?.unregisterCallback(mediaProjectionCallback); virtualDisplay?.release(); imageReader?.close(); mediaProjection?.stop() } catch (e: Exception) {} }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("todo_service", "Screen Analysis", NotificationManager.IMPORTANCE_DEFAULT)) } }
    private fun updateStatusNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createMainNotification(text)
        notificationManager.notify(NOTIFICATION_ID_MAIN, notification)
    }
    private fun clearTaskNotifications(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifs = manager.activeNotifications
            for (notif in activeNotifs) { if (notif.id >= NOTIFICATION_ID_START) manager.cancel(notif.id) }
        } else { for (i in NOTIFICATION_ID_START..NOTIFICATION_ID_START + 100) manager.cancel(i) }
    }
    private fun showTaskNotification() {
        val tasks = TaskStore.getTasks(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        clearTaskNotifications(notificationManager)
        val activeCount = tasks.count { !it.isCompleted }
        val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
        val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_MAIN, mainNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID_MAIN, mainNotification)
        }
        notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)
        tasks.forEachIndexed { index, task ->
            val notificationId = NOTIFICATION_ID_START + index
            if (!task.isCompleted) {
                val completeIntent = Intent(ACTION_COMPLETE_TASK).apply { setPackage(packageName); putExtra(EXTRA_TASK_INDEX, index) }
                val completePendingIntent = PendingIntent.getBroadcast(this, index, completeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val taskNotification = NotificationCompat.Builder(this, "todo_service")
                    .setContentTitle("待办事项 ${index + 1}")
                    .setContentText(task.text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(task.text))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true).setAutoCancel(false)
                    .addAction(android.R.drawable.checkbox_on_background, "完成", completePendingIntent).build()
                notificationManager.notify(notificationId, taskNotification)
            }
        }
    }
    private fun createMainNotification(text: String, showClearButton: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, "todo_service")
            .setContentTitle("TodoList 助手")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true).setOnlyAlertOnce(true)
        if (showClearButton) {
            val clearIntent = Intent(ACTION_CLEAR_TASKS).apply { setPackage(packageName) }
            val clearPendingIntent = PendingIntent.getBroadcast(this, 0, clearIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(android.R.drawable.ic_menu_delete, "清空所有", clearPendingIntent)
        }
        return builder.build()
    }
}