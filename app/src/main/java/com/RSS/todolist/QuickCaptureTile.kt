package com.RSS.todolist

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

/**
 * Quick Settings Tile：用户可在下拉快速设置中添加此磁贴，点击即可启动截屏授权流程。
 */
class QuickCaptureTile : TileService() {
    override fun onClick() {
        super.onClick()
        // 说明：targetSdk=36 下，后台/锁屏场景对“从磁贴拉起 Activity”限制更严格。
        // 做法：先解锁再执行，并尽量使用 startActivityAndCollapse(PendingIntent)。
        unlockAndRun {
            try {
                val intent = Intent(this, CaptureStarterActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // 避免 PendingIntent 复用导致行为不一致
                    putExtra("_ts", System.currentTimeMillis())
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                val pi = PendingIntent.getActivity(this, requestCode, intent, flags)

                try {
                    startActivityAndCollapse(pi)
                    Toast.makeText(this, "正在启动截屏流程...", Toast.LENGTH_SHORT).show()
                    Log.d("QuickCaptureTile", "已通过 startActivityAndCollapse(PendingIntent) 启动 CaptureStarterActivity")
                } catch (e: Exception) {
                    Log.w("QuickCaptureTile", "startActivityAndCollapse(PendingIntent) 受限，尝试 PendingIntent.send(带 options)", e)
                    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ActivityOptions.makeBasic().apply {
                            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        }.toBundle()
                    } else {
                        null
                    }
                    pi.send(this, 0, null, null, null, null, options)
                    Toast.makeText(this, "正在启动截屏流程...", Toast.LENGTH_SHORT).show()
                    Log.d("QuickCaptureTile", "已通过 PendingIntent.send(options) 启动 CaptureStarterActivity")
                }
            } catch (e: Exception) {
                Log.e("QuickCaptureTile", "启动截屏 Activity 失败", e)
                Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d("QuickCaptureTile", "Tile added")
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d("QuickCaptureTile", "Tile start listening")
    }
}
