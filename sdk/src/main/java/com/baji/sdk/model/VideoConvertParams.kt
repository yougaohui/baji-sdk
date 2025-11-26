package com.baji.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 视频转换参数
 */
@Parcelize
data class VideoConvertParams(
    /**
     * 目标宽度
     */
    val targetWidth: Int,
    
    /**
     * 目标高度
     */
    val targetHeight: Int,
    
    /**
     * 帧率
     */
    val fps: Int = 5,
    
    /**
     * 视频质量（1-31，数值越小质量越高）
     */
    val quality: Int = 3,
    
    /**
     * 开始时间（秒）
     */
    val startTime: Float = 0f,
    
    /**
     * 持续时间（秒），0表示到结尾
     */
    val duration: Float = 0f,
    
    /**
     * 裁剪区域（可选）
     */
    val cropRegion: CropRegion? = null
) : Parcelable {
    /**
     * 裁剪区域
     */
    @Parcelize
    data class CropRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : Parcelable
}

