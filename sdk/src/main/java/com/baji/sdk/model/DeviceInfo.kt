package com.baji.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 设备信息
 */
@Parcelize
data class DeviceInfo(
    /**
     * 设备名称
     */
    val name: String,
    
    /**
     * 设备MAC地址
     */
    val macAddress: String,
    
    /**
     * 设备版本
     */
    val version: String? = null,
    
    /**
     * 设备型号
     */
    val model: String? = null,
    
    /**
     * 连接状态
     */
    val isConnected: Boolean = false,
    
    /**
     * 信号强度（RSSI，单位：dBm）
     */
    val rssi: Int? = null
) : Parcelable

