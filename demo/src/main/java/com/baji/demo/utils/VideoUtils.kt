package com.baji.demo.utils

import android.content.Context
import android.util.TypedValue

object VideoUtils {
    /**
     * dp转px
     */
    fun dp2px(context: Context, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 将秒数转换为时间字符串 (MM:SS 或 HH:MM:SS)
     */
    fun convertSecondsToTime(seconds: Long): String {
        if (seconds <= 0) {
            return "00:00"
        }
        
        val hour = (seconds / 3600).toInt()
        val minute = ((seconds % 3600) / 60).toInt()
        val second = (seconds % 60).toInt()
        
        return if (hour > 0) {
            if (hour > 99) {
                "99:59:59"
            } else {
                String.format("%02d:%02d:%02d", hour, minute, second)
            }
        } else {
            String.format("%02d:%02d", minute, second)
        }
    }

    /**
     * 从文件路径中获取文件名（不含扩展名）
     */
    fun getFileName(path: String?): String {
        if (path.isNullOrEmpty()) {
            return ""
        }
        val parts = path.split(".")
        return if (parts.isNotEmpty()) {
            parts[0]
        } else {
            path
        }
    }
}
