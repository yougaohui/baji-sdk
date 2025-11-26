package com.baji.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 表盘信息
 */
@Parcelize
data class WatchFaceInfo(
    /**
     * 表盘ID
     */
    val id: Long,
    
    /**
     * 表盘名称
     */
    val name: String,
    
    /**
     * 表盘预览图URL
     */
    val previewUrl: String? = null,
    
    /**
     * 表盘描述
     */
    val description: String? = null,
    
    /**
     * 表盘宽度
     */
    val width: Int,
    
    /**
     * 表盘高度
     */
    val height: Int,
    
    /**
     * 屏幕类型（0=方屏，1=圆屏）
     */
    val screenType: Int = 0
) : Parcelable

