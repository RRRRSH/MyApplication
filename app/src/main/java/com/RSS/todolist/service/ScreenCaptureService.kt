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

    // ... (变量声明部分保持不变)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var retryCount = 0
    private val MAX_RETRY = 3

    // 广播接收器
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            when (intent?.action) {
                ACTION_CLEAR_TASKS -> {
                    TaskStore.clearTasks(this@ScreenCaptureService)
                    notificationManager.cancelAll()
                    showTaskNotification()
                }
                // 🌟 改动：这里处理“完成”动作
                ACTION_COMPLETE_TASK -> {
                    val index = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
                    if (index != -1) {
                        // 1. 在数据库中标记为“已完成” (保留记录)
                        TaskStore.setTaskCompleted(this@ScreenCaptureService, index, true)
                        
                        // 2. 刷新通知栏 (已完成的任务会自动从通知栏消失)
                        showTaskNotification()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CLEAR_TASKS = "com.RSS.todolist.ACTION_CLEAR_TASKS"
        const val ACTION_COMPLETE_TASK = "com.RSS.todolist.ACTION_COMPLETE_TASK" // 改名了
        const val EXTRA_TASK_INDEX = "extra_task_index"
        
        const val NOTIFICATION_ID_MAIN = 1
        const val NOTIFICATION_ID_START = 100
    }

    // ... (MediaProjectionCallback, onCreate, onBind, onDestroy 等保持不变)
    // ⚠️ 记得注册广播时使用 ACTION_COMPLETE_TASK
    
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopCapture()
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_CLEAR_TASKS)
            addAction(ACTION_COMPLETE_TASK) // 👈 注册新动作
        }
        registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        showTaskNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionReceiver)
    }

    // ... (onStartCommand, startCapture, captureAndAnalyze, performOcr, performAnalysis 等核心逻辑完全不变)
    // ... (请保留之前的逻辑，这里为了节省篇幅只列出修改过的 showTaskNotification)
    
    // 👇 只需要把之前的代码逻辑复制过来即可
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>("DATA")
        if (resultCode == Activity.RESULT_OK && resultData != null) {
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
        }
        return START_NOT_STICKY
    }
    
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
        val message = ChatMessage(role = "user", content = listOf(contentPart))
        val request = ChatRequest(model = SparkConfig.MODEL_OCR, messages = listOf(message))
        RetrofitClient.api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val text = response.body()?.choices?.firstOrNull()?.message?.content
                if (!text.isNullOrEmpty()) performAnalysis(text) else updateStatusNotification("OCR失败")
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) { updateStatusNotification("网络错误") }
        })
    }
    private fun performAnalysis(ocrText: String) {
        updateStatusNotification("正在生成任务...")
        val prompt = "你是一个日程助理。根据OCR文字提取待办事项。忽略UI。关注约定/备忘。输出格式：[时间]+[事件]。无任务则输出'无任务'。\nOCR内容：\n$ocrText"
        val message = ChatMessage(role = "user", content = prompt)
        val request = ChatRequest(model = SparkConfig.MODEL_QWEN, messages = listOf(message))
        RetrofitClient.api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val task = response.body()?.choices?.firstOrNull()?.message?.content
                if (!task.isNullOrEmpty() && task != "无任务") {
                    TaskStore.addTask(this@ScreenCaptureService, task)
                    showTaskNotification()
                } else showTaskNotification()
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) { updateStatusNotification("失败") }
        })
    }
    private fun stopCapture() { try { mediaProjection?.unregisterCallback(mediaProjectionCallback); virtualDisplay?.release(); imageReader?.close(); mediaProjection?.stop() } catch (e: Exception) {} }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("todo_service", "Screen Analysis", NotificationManager.IMPORTANCE_DEFAULT)) } }
    
    // --- ⬇️ 重点修改区域 ⬇️ ---

    private fun updateStatusNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createMainNotification(text)
        notificationManager.notify(NOTIFICATION_ID_MAIN, notification)
    }

    private fun showTaskNotification() {
        val tasks = TaskStore.getTasks(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 只有未完成的任务才计入“当前待办”数量
        val activeCount = tasks.count { !it.isCompleted }
        val mainText = if (activeCount == 0) "暂无待办任务" else "你有 $activeCount 个待办事项"
        
        val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_MAIN, mainNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID_MAIN, mainNotification)
        }
        notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)

        // 2. 遍历所有任务
        tasks.forEachIndexed { index, task ->
            val notificationId = NOTIFICATION_ID_START + index

            // 🌟 核心逻辑：
            // 如果任务是“未完成” -> 显示通知
            // 如果任务是“已完成” -> 取消通知
            if (!task.isCompleted) {
                // 创建“完成”按钮 Intent
                val completeIntent = Intent(ACTION_COMPLETE_TASK).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_TASK_INDEX, index)
                }
                val completePendingIntent = PendingIntent.getBroadcast(
                    this, index, completeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val taskNotification = NotificationCompat.Builder(this, "todo_service")
                    .setContentTitle("待办事项 ${index + 1}")
                    .setContentText(task.text) // 注意：这里用 task.text
                    .setStyle(NotificationCompat.BigTextStyle().bigText(task.text))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    // 按钮文字改为“完成”
                    .addAction(android.R.drawable.checkbox_on_background, "完成", completePendingIntent)
                    .build()

                notificationManager.notify(notificationId, taskNotification)
            } else {
                // 如果任务已完成，确保它的通知被移除
                notificationManager.cancel(notificationId)
            }
        }
    }

    private fun createMainNotification(text: String, showClearButton: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, "todo_service")
            .setContentTitle("TodoList 助手")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (showClearButton) {
            val clearIntent = Intent(ACTION_CLEAR_TASKS).apply { setPackage(packageName) }
            val clearPendingIntent = PendingIntent.getBroadcast(this, 0, clearIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(android.R.drawable.ic_menu_delete, "清空记录", clearPendingIntent)
        }
        return builder.build()
    }
}