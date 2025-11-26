package com.baji.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 文件信息
 */
@Parcelize
data class FileInfo(
    /**
     * 文件ID
     */
    val id: Long,
    
    /**
     * 文件路径
     */
    val path: String,
    
    /**
     * 文件大小（字节）
     */
    val size: Long,
    
    /**
     * 文件类型
     */
    val fileType: FileType
) : Parcelable {
    /**
     * 文件类型枚举
     */
    enum class FileType {
        IMAGE,
        VIDEO,
        AUDIO,
        OTHER
    }
}

