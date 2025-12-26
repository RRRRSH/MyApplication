package com.RSS.todolist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.RSS.todolist.service.ScreenCaptureService

/**
 * 一个透明的 Activity，只用于请求 MediaProjection 授权并将结果传给 Service。
 * 启动后会弹出系统截屏授权对话，授权结果会通过启动 Service 传递并立即退出。
 */
class CaptureStarterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpManager.createScreenCaptureIntent()

        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // 将授权结果发送给前台 Service，Service 会继续截屏与分析流程
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("DATA", result.data)
                }
                startForegroundService(serviceIntent)
                // 退到后台并结束 Activity
                moveTaskToBack(true)
                finish()
            } else {
                // 用户取消或拒绝，直接退出
                finish()
            }
        }

        // 立即发起授权请求
        launcher.launch(intent)
    }
}
