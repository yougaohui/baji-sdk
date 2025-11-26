package com.baji.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 图片转换参数
 */
@Parcelize
data class ImageConvertParams(
    /**
     * 目标宽度
     */
    val targetWidth: Int,
    
    /**
     * 目标高度
     */
    val targetHeight: Int,
    
    /**
     * 图片质量（0-100）
     */
    val quality: Int = 90,
    
    /**
     * 输出格式
     */
    val outputFormat: ImageFormat = ImageFormat.BIN,
    
    /**
     * 转换算法类型（用于杰理SDK）
     */
    val algorithm: Int = 0
) : Parcelable {
    /**
     * 图片格式
     */
    enum class ImageFormat {
        PNG,
        JPEG,
        BMP,
        BIN  // 设备专用格式
    }
}

