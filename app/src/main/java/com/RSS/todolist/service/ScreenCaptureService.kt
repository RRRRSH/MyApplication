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
                    Log.d("TodoList", "点击了清空所有")
                    TaskStore.clearTasks(this@ScreenCaptureService)
                    notificationManager.cancelAll()
                    showTaskNotification()
                }
                ACTION_DELETE_TASK -> {
                    val index = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
                    Log.d("TodoList", "点击了完成任务: Index $index")
                    if (index != -1) {
                        TaskStore.removeTask(this@ScreenCaptureService, index)
                        // 清除所有是为了处理序号变化 (比如删了第1个，第2个要变成第1个)
                        notificationManager.cancelAll()
                        showTaskNotification()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CLEAR_TASKS = "com.RSS.todolist.ACTION_CLEAR_TASKS"
        const val ACTION_DELETE_TASK = "com.RSS.todolist.ACTION_DELETE_TASK"
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
            addAction(ACTION_DELETE_TASK)
        }
        // 注册广播
        registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        showTaskNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>("DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            Log.d("TodoList", "收到权限，准备截屏...")

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
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            retryCount = 0
            Handler(Looper.getMainLooper()).postDelayed({ captureAndAnalyze() }, 1000)
        } catch (e: Exception) {
            Log.e("TodoList", "创建虚拟屏幕失败", e)
            stopCapture()
        }
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
                updateStatusNotification("正在分析图片...")
                performOcr(bitmap)
            } catch (e: Exception) {
                image?.close()
                updateStatusNotification("图片处理失败")
            }
        } else {
            if (retryCount < MAX_RETRY) {
                retryCount++
                Handler(Looper.getMainLooper()).postDelayed({ captureAndAnalyze() }, 1000)
            } else {
                updateStatusNotification("无法获取屏幕画面")
                stopCapture()
            }
        }
    }

    private fun performOcr(bitmap: Bitmap) {
        val base64Img = ImageUtils.bitmapToBase64(bitmap)
        val contentPart = ContentPart(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,$base64Img"))
        val message = ChatMessage(role = "user", content = listOf(contentPart))
        val request = ChatRequest(model = SparkConfig.MODEL_OCR, messages = listOf(message))

        RetrofitClient.api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val text = response.body()?.choices?.firstOrNull()?.message?.content
                if (!text.isNullOrEmpty()) {
                    performAnalysis(text)
                } else {
                    updateStatusNotification("OCR 识别失败")
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                updateStatusNotification("网络错误: ${t.message}")
            }
        })
    }

    private fun performAnalysis(ocrText: String) {
        updateStatusNotification("正在生成任务...")

        val prompt = """
            你是一个敏锐的个人日程助理。请根据OCR文字提取【待办事项】或【计划】。
            规则：
            1. 忽略UI元素。
            2. 不要描述“用户正在做什么”。
            3. ✅ 重点关注：聊天中的约定、备忘录、弹窗提示。
            4. ✅ 输出格式：[时间] + [事件] (无时间则只写事件)。
            5. ❌ 如果完全无关，请输出 "无任务"。
            
            OCR文字：
            $ocrText
        """.trimIndent()

        val message = ChatMessage(role = "user", content = prompt)
        val request = ChatRequest(model = SparkConfig.MODEL_QWEN, messages = listOf(message))

        RetrofitClient.api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val task = response.body()?.choices?.firstOrNull()?.message?.content

                if (!task.isNullOrEmpty() && task != "无任务") {
                    Log.d("TodoList", "新任务: $task")
                    TaskStore.addTask(this@ScreenCaptureService, task)
                    showTaskNotification()
                } else {
                    showTaskNotification()
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                updateStatusNotification("分析失败: ${t.message}")
            }
        })
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

    private fun showTaskNotification() {
        val tasks = TaskStore.getTasks(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 主通知
        val mainText = if (tasks.isEmpty()) "TodoList 助手已就绪" else "当前有 ${tasks.size} 个待办事项"
        val mainNotification = createMainNotification(mainText, showClearButton = tasks.isNotEmpty())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_MAIN, mainNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID_MAIN, mainNotification)
        }
        notificationManager.notify(NOTIFICATION_ID_MAIN, mainNotification)

        // 2. 子通知
        tasks.forEachIndexed { index, task ->
            val notificationId = NOTIFICATION_ID_START + index

            // ⚠️ 修复关键点：加上 setPackage(packageName) 变成显式 Intent
            val deleteIntent = Intent(ACTION_DELETE_TASK).apply {
                setPackage(packageName) // <--- 就是这一行！
                putExtra(EXTRA_TASK_INDEX, index)
            }

            val deletePendingIntent = PendingIntent.getBroadcast(
                this,
                index, // 使用 index 区分不同的 PendingIntent
                deleteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val taskNotification = NotificationCompat.Builder(this, "todo_service")
                .setContentTitle("待办事项 ${index + 1}")
                .setContentText(task)
                .setStyle(NotificationCompat.BigTextStyle().bigText(task))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "完成 / 删除", deletePendingIntent)
                .build()

            notificationManager.notify(notificationId, taskNotification)
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
            // ⚠️ 修复关键点：加上 setPackage(packageName)
            val clearIntent = Intent(ACTION_CLEAR_TASKS).apply {
                setPackage(packageName) // <--- 就是这一行！
            }
            val clearPendingIntent = PendingIntent.getBroadcast(
                this, 0, clearIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_delete, "清空所有任务", clearPendingIntent)
        }

        return builder.build()
    }
}