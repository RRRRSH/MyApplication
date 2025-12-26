package com.RSS.todolist

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

/**
 * Quick Settings Tile：用户可在下拉快速设置中添加此磁贴，点击即可启动截屏授权流程。
 */
class QuickCaptureTile : TileService() {
    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(this, CaptureStarterActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // TileService.startActivityAndCollapse may be restricted on some devices/ROMs.
            // Use a PendingIntent to launch the Activity instead.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(this, 0, intent, flags)
            pi.send()
            Toast.makeText(this, "正在启动截屏流程...", Toast.LENGTH_SHORT).show()
            Log.d("QuickCaptureTile", "已通过 PendingIntent 启动 CaptureStarterActivity")
        } catch (e: Exception) {
            Log.e("QuickCaptureTile", "启动截屏 Activity 失败", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
