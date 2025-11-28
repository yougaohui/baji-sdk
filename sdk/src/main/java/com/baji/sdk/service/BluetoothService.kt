package com.baji.sdk.service

import android.content.Context
import android.util.Log
import com.baji.protocol.BajiProtocolManager
import com.baji.sdk.callback.ConnectionCallback
import com.baji.sdk.model.DeviceInfo
import com.legend.mywatch.sdk.mywatchsdklib.android.event.AckEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.event.BaseEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.event.ConnectStatusEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.event.EventManager
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
    
    // 解绑相关状态
    private var isUnbinding = false
    private var unbindMsgWhat = -1
    private var unbindCallback: ((success: Boolean, error: String?) -> Unit)? = null
    private val unbindTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var unbindTimeoutRunnable: Runnable? = null
    
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
        
        // 如果正在解绑过程中设备断开连接，执行本地解绑
        if (isUnbinding && !event.isConnected) {
            Log.d(TAG, "解绑过程中设备断开，执行本地解绑")
            performLocalUnbind(null)
            return
        }
        
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
     * 寻找设备
     * 让已连接的设备发出提示（响铃或震动）
     */
    fun findDevice() {
        try {
            if (!isConnected()) {
                Log.w(TAG, "设备未连接，无法寻找设备")
                throw IllegalStateException("设备未连接")
            }
            
            Log.d(TAG, "开始寻找设备")
            SDKCmdManager.findWatch()
        } catch (e: Exception) {
            Log.e(TAG, "寻找设备失败: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 恢复出厂设置
     * 重置设备到出厂状态
     */
    fun factoryReset() {
        try {
            if (!isConnected()) {
                Log.w(TAG, "设备未连接，无法恢复出厂设置")
                throw IllegalStateException("设备未连接")
            }
            
            Log.d(TAG, "开始恢复出厂设置")
            SDKCmdManager.resetWatch()
        } catch (e: Exception) {
            Log.e(TAG, "恢复出厂设置失败: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 解绑设备
     * 解绑当前设备，清除本地存储的设备信息
     * 
     * @param callback 解绑结果回调，可为null
     */
    fun unbindDevice(callback: ((success: Boolean, error: String?) -> Unit)? = null) {
        try {
            if (isUnbinding) {
                Log.w(TAG, "解绑操作正在进行中，请勿重复调用")
                callback?.invoke(false, "解绑操作正在进行中")
                return
            }
            
            Log.d(TAG, "开始解绑设备")
            isUnbinding = true
            unbindCallback = callback
            
            // 检查设备是否已连接
            if (!isConnected()) {
                // 设备未连接，直接执行本地解绑
                performLocalUnbind(callback)
                return
            }
            
            // 设备已连接，发送解绑指令
            try {
                // 清除本地存储
                com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.clearSaveKeyValues()
                
                // 发送解绑指令
                val unbindCommand = SDKCmdManager.unbindWatch()
                
                // 获取解绑指令的msgWhat值
                unbindMsgWhat = getMsgWhatFromUnbindCommand(unbindCommand)
                
                // 设置超时处理
                setupUnbindTimeout()
                
            } catch (e: Exception) {
                // 发送指令失败，执行本地解绑
                Log.e(TAG, "发送解绑指令失败: ${e.message}", e)
                performLocalUnbind(callback)
            }
            
        } catch (e: Exception) {
            isUnbinding = false
            Log.e(TAG, "解绑设备失败: ${e.message}", e)
            callback?.invoke(false, e.message)
        }
    }
    
    /**
     * 执行本地解绑（设备未连接时）
     */
    private fun performLocalUnbind(callback: ((success: Boolean, error: String?) -> Unit)?) {
        try {
            // 1. 尝试断开设备连接（如果可能）
            try {
                SDKCmdManager.unbindWatch()
            } catch (e: Exception) {
                // 忽略断开连接失败的错误
                Log.d(TAG, "断开连接失败（可忽略）: ${e.message}")
            }
            
            // 2. 清除本地存储的设备信息
            clearDeviceBindingInfo()
            
            // 3. 更新连接状态
            connectedDevice = null
            
            // 4. 完成解绑
            isUnbinding = false
            Log.d(TAG, "本地解绑成功")
            callback?.invoke(true, null)
            
        } catch (e: Exception) {
            isUnbinding = false
            Log.e(TAG, "本地解绑失败: ${e.message}", e)
            callback?.invoke(false, e.message)
        }
    }
    
    /**
     * 从解绑指令中获取msgWhat值
     */
    private fun getMsgWhatFromUnbindCommand(command: ByteArray?): Int {
        if (command == null || command.size < 5) {
            return 0
        }
        
        val commandCode = command[3].toInt() and 0xFF
        val commandKey = if (command.size >= 6) {
            command[5].toInt() and 0xFF
        } else {
            command[4].toInt() and 0xFF
        }
        
        // 使用EventManager获取msgWhat
        return EventManager.getMsgWhat(commandCode, commandKey)
    }
    
    /**
     * 设置解绑超时处理
     */
    private fun setupUnbindTimeout() {
        unbindTimeoutRunnable = Runnable {
            if (isUnbinding) {
                // 超时后自动强制解绑
                Log.w(TAG, "解绑操作超时，执行强制解绑")
                forceUnbindDevice()
            }
        }
        unbindTimeoutHandler.postDelayed(unbindTimeoutRunnable!!, 10000) // 10秒超时
    }
    
    /**
     * 强制解绑设备（超时后自动执行）
     */
    private fun forceUnbindDevice() {
        if (!isUnbinding) {
            return
        }
        
        isUnbinding = false
        
        // 清除超时处理
        unbindTimeoutRunnable?.let {
            unbindTimeoutHandler.removeCallbacks(it)
        }
        unbindTimeoutRunnable = null
        
        try {
            // 1. 断开设备连接
            SDKCmdManager.disconnectWatch()
            
            // 2. 清除本地存储的设备信息
            clearDeviceBindingInfo()
            
            // 3. 更新连接状态
            connectedDevice = null
            
            Log.d(TAG, "强制解绑成功")
            val callback = unbindCallback
            unbindCallback = null
            callback?.invoke(true, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "强制解绑失败: ${e.message}", e)
            val callback = unbindCallback
            unbindCallback = null
            callback?.invoke(false, e.message)
        }
    }
    
    /**
     * 清除设备绑定信息
     */
    private fun clearDeviceBindingInfo() {
        try {
            // 清除蓝牙地址
            com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.setBluetoothAddress("")
            
            // 清除蓝牙名称
            com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.setBluetoothDeviceName("")
            
            // 清除所有存储的值
            com.legend.mywatch.sdk.mywatchsdklib.android.sp.WatchSDKSPUtils.clearSaveKeyValues()
            
            Log.d(TAG, "设备绑定信息已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除设备绑定信息失败: ${e.message}", e)
        }
    }
    
    /**
     * 处理ACK事件
     */
    @Subscribe
    fun onAckEvent(event: BaseEvent) {
        if (event is AckEvent) {
            val ackEvent = event
            // 检查是否是解绑操作的ACK
            if (isUnbinding && ackEvent.msgWhat == unbindMsgWhat) {
                onUnbindAckReceived(ackEvent.isSuccess)
            }
        }
    }
    
    /**
     * 收到解绑ACK确认
     */
    private fun onUnbindAckReceived(success: Boolean) {
        if (!isUnbinding) {
            return
        }
        
        isUnbinding = false
        
        // 清除超时处理
        unbindTimeoutRunnable?.let {
            unbindTimeoutHandler.removeCallbacks(it)
        }
        unbindTimeoutRunnable = null
        
        val callback = unbindCallback
        unbindCallback = null
        
        if (success) {
            try {
                // 1. 断开设备连接
                SDKCmdManager.disconnectWatch()
                
                // 2. 清除本地存储的设备信息
                clearDeviceBindingInfo()
                
                // 3. 更新连接状态
                connectedDevice = null
                
                Log.d(TAG, "解绑成功（收到ACK确认）")
                callback?.invoke(true, null)
                
            } catch (e: Exception) {
                Log.e(TAG, "解绑后处理失败: ${e.message}", e)
                callback?.invoke(false, e.message)
            }
        } else {
            Log.w(TAG, "解绑失败（设备返回失败ACK）")
            callback?.invoke(false, "设备返回失败ACK")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnectDevice()
        
        // 清理解绑相关资源
        unbindTimeoutRunnable?.let {
            unbindTimeoutHandler.removeCallbacks(it)
        }
        isUnbinding = false
        unbindTimeoutRunnable = null
        unbindCallback = null
        
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        connectionCallback = null
        Log.d(TAG, "蓝牙服务资源已清理")
    }
}

