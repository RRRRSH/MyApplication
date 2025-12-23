package com.RSS.todolist.utils // <--- 这一行非常重要，必须对应你的文件夹路径

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    /**
     * 将 Bitmap 图片压缩并转换为 Base64 字符串
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 60): String {
        val outputStream = ByteArrayOutputStream()
        
        // 1. 压缩图片为 JPEG 格式
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        // 2. 转为字节数组
        val bytes = outputStream.toByteArray()
        
        // 3. 转为 Base64 字符串 (无换行符模式)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}