package com.baji.sdk.service

import android.content.Context
import android.util.Log
import com.baji.protocol.BajiProtocolManager
import com.baji.sdk.callback.ConnectionCallback
import com.baji.sdk.model.DeviceInfo
import com.legend.mywatch.sdk.mywatchsdklib.android.event.ConnectStatusEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.sdk.SDKCmdManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * 蓝牙连接服务
 * 提供设备连接、断开等功能
 */
class BluetoothService(
    private val context: Context,
    private val protocolManager: BajiProtocolManager?
) {
    private val TAG = "BluetoothService"
    
    private var connectionCallback: ConnectionCallback? = null
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
     * 连接设备
     */
    fun connectDevice(macAddress: String) {
        try {
            Log.d(TAG, "开始连接设备: $macAddress")
            SDKCmdManager.connectWatch(macAddress)
            
            // 延迟检查连接状态，确保ConnectStatusEvent事件有时间触发
            // 如果事件没有触发，则主动检查连接状态
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                checkAndUpdateConnectionStatus()
            }, 2000) // 2秒后检查连接状态
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
            SDKCmdManager.disconnectWatch()
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
     * 优先使用SDK的实际连接状态，如果SDK状态不可用，则使用缓存的连接状态
     */
    fun isConnected(): Boolean {
        return try {
            // 优先使用SDK的实际连接状态
            SDKCmdManager.isConnected()
        } catch (e: Exception) {
            Log.w(TAG, "检查连接状态失败，使用缓存状态: ${e.message}")
            // 如果SDK检查失败，使用缓存的连接状态
            connectedDevice != null
        }
    }
    
    /**
     * 主动检查并更新连接状态
     * 用于在连接后验证实际连接状态
     */
    fun checkAndUpdateConnectionStatus() {
        try {
            val isActuallyConnected = SDKCmdManager.isConnected()
            val cachedDevice = connectedDevice
            
            if (isActuallyConnected && cachedDevice == null) {
                // SDK显示已连接，但缓存中没有设备信息，尝试获取设备信息
                val deviceName = com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.getBluetoothDeviceName() ?: "Unknown"
                val macAddress = com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.getBluetoothAddress() ?: ""
                
                if (macAddress.isNotBlank()) {
                    val deviceInfo = DeviceInfo(
                        name = deviceName,
                        macAddress = macAddress,
                        isConnected = true
                    )
                    connectedDevice = deviceInfo
                    connectionCallback?.onConnected(deviceInfo)
                    Log.d(TAG, "检测到设备已连接，更新连接状态: $deviceName ($macAddress)")
                }
            } else if (!isActuallyConnected && cachedDevice != null) {
                // SDK显示未连接，但缓存中有设备信息，更新为断开状态
                val disconnectedDevice = cachedDevice.copy(isConnected = false)
                connectionCallback?.onDisconnected(disconnectedDevice)
                connectedDevice = null
                Log.d(TAG, "检测到设备已断开，更新连接状态")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查连接状态失败: ${e.message}", e)
        }
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
     * 处理设备发现事件
     */
    fun onDeviceFound(deviceInfo: DeviceInfo) {
        connectionCallback?.onDeviceFound(deviceInfo)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnectDevice()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        connectionCallback = null
        Log.d(TAG, "蓝牙服务资源已清理")
    }
}

