package com.baji.sdk.callback

import com.baji.sdk.model.DeviceInfo

/**
 * 蓝牙连接状态回调
 */
interface ConnectionCallback {
    /**
     * 设备连接成功
     */
    fun onConnected(deviceInfo: DeviceInfo)
    
    /**
     * 设备断开连接
     */
    fun onDisconnected(deviceInfo: DeviceInfo)
    
    /**
     * 连接失败
     */
    fun onConnectionFailed(error: String)
    
    /**
     * 设备扫描结果
     */
    fun onDeviceFound(deviceInfo: DeviceInfo)
}

