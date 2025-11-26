package com.baji.sdk.service

import android.content.Context
import android.util.Log
import com.baji.protocol.BajiProtocolManager
import com.baji.sdk.callback.ConnectionCallback
import com.baji.sdk.model.DeviceInfo
import com.legend.mywatch.sdk.mywatchsdklib.android.event.ConnectStatusEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.sdk.WatchSDK
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * 蓝牙连接服务
 * 提供设备扫描、连接、断开等功能
 */
class BluetoothService(
    private val context: Context,
    private val protocolManager: BajiProtocolManager?
) {
    private val TAG = "BluetoothService"
    
    private var connectionCallback: ConnectionCallback? = null
    private var isScanning = false
    private var connectedDevice: DeviceInfo? = null
    
    init {
        // 注册EventBus监听
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }
    
    /**
     * 设置连接回调
     */
    fun setConnectionCallback(callback: ConnectionCallback?) {
        this.connectionCallback = callback
    }
    
    /**
     * 开始扫描设备
     */
    fun startScan() {
        if (isScanning) {
            Log.w(TAG, "扫描已在进行中")
            return
        }
        
        try {
            // 使用WatchSDK的扫描功能
            WatchSDK.startScan()
            isScanning = true
            Log.d(TAG, "开始扫描蓝牙设备")
        } catch (e: Exception) {
            Log.e(TAG, "开始扫描失败: ${e.message}", e)
            connectionCallback?.onConnectionFailed("扫描失败: ${e.message}")
        }
    }
    
    /**
     * 停止扫描设备
     */
    fun stopScan() {
        if (!isScanning) {
            return
        }
        
        try {
            WatchSDK.stopScan()
            isScanning = false
            Log.d(TAG, "停止扫描蓝牙设备")
        } catch (e: Exception) {
            Log.e(TAG, "停止扫描失败: ${e.message}", e)
        }
    }
    
    /**
     * 连接设备
     */
    fun connectDevice(macAddress: String) {
        try {
            Log.d(TAG, "开始连接设备: $macAddress")
            WatchSDK.connectDevice(macAddress)
        } catch (e: Exception) {
            Log.e(TAG, "连接设备失败: ${e.message}", e)
            connectionCallback?.onConnectionFailed("连接失败: ${e.message}")
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnectDevice() {
        try {
            Log.d(TAG, "断开设备连接")
            WatchSDK.disconnectDevice()
            connectedDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "断开连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前连接的设备
     */
    fun getConnectedDevice(): DeviceInfo? {
        return connectedDevice
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return connectedDevice != null
    }
    
    /**
     * 处理连接状态事件
     */
    @Subscribe
    fun onConnectStatusEvent(event: ConnectStatusEvent) {
        Log.d(TAG, "收到连接状态事件: connected=${event.isConnected}, mac=${event.macAddress}")
        
        if (event.isConnected) {
            val deviceInfo = DeviceInfo(
                name = com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.getBluetoothDeviceName() ?: "Unknown",
                macAddress = event.macAddress ?: "",
                isConnected = true
            )
            connectedDevice = deviceInfo
            connectionCallback?.onConnected(deviceInfo)
        } else {
            connectedDevice?.let { device ->
                val disconnectedDevice = device.copy(isConnected = false)
                connectionCallback?.onDisconnected(disconnectedDevice)
                connectedDevice = null
            }
        }
    }
    
    /**
     * 处理设备发现事件（需要从扫描结果中获取）
     */
    fun onDeviceFound(deviceInfo: DeviceInfo) {
        connectionCallback?.onDeviceFound(deviceInfo)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopScan()
        disconnectDevice()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        connectionCallback = null
        Log.d(TAG, "蓝牙服务资源已清理")
    }
}

