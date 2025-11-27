package com.baji.sdk.service

import android.util.Log
import com.legend.mywatch.sdk.mywatchsdklib.android.enm.BluetoothStatusEnum
import com.legend.mywatch.sdk.mywatchsdklib.android.event.ClockDialInfoEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.event.ConnectStatusEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.sdk.SDKCmdManager
import com.legend.mywatch.sdk.mywatchsdklib.android.event.DeviceFunctionEvent
import com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.ClockDialInfoBody as SdkClockDialInfoBody
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 表盘信息服务
 * 负责在设备连接成功后自动读取表盘信息并存储在内存中
 * 
 * @author Baji SDK Team
 * @since 1.0.0
 */
class ClockDialInfoService {
    
    private val TAG = "ClockDialInfoService"
    
    private var isRegistered = false
    private var currentClockDialInfo: SdkClockDialInfoBody? = null
    
    /**
     * 初始化服务
     */
    fun initialize() {
        if (!isRegistered) {
            EventBus.getDefault().register(this)
            isRegistered = true
            Log.d(TAG, "表盘信息服务已初始化，EventBus注册成功")
            Log.d(TAG, "当前EventBus注册状态: ${EventBus.getDefault().isRegistered(this)}")
        } else {
            Log.w(TAG, "表盘信息服务已经初始化，跳过重复注册")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        if (isRegistered) {
            EventBus.getDefault().unregister(this)
            isRegistered = false
            Log.d(TAG, "表盘信息服务已清理")
        }
        currentClockDialInfo = null
    }
    
    /**
     * 监听连接状态变化事件
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnectStatusEvent(event: ConnectStatusEvent) {
        when (event.status) {
            BluetoothStatusEnum.CONNECTED.value -> {
                Log.d(TAG, "设备连接成功，准备读取表盘信息")
                // 连接成功后，延迟一段时间后主动请求表盘信息
                // 不依赖 DeviceFunctionEvent，因为可能不会触发
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (currentClockDialInfo == null) {
                        Log.d(TAG, "连接成功后主动请求表盘信息（延迟3秒）")
                        readClockDialInfo()
                    } else {
                        Log.d(TAG, "表盘信息已存在，无需重新获取")
                    }
                }, 3000) // 延迟3秒，确保设备完全初始化
            }
            BluetoothStatusEnum.DISCONNECT.value -> {
                Log.d(TAG, "设备断开连接，清除表盘信息")
                currentClockDialInfo = null
            }
            BluetoothStatusEnum.CONNECT_FAILED.value -> {
                Log.d(TAG, "设备连接失败")
            }
        }
    }
    
    /**
     * 监听设备功能事件
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceFunctionEvent(event: DeviceFunctionEvent) {
        Log.d(TAG, "收到设备功能事件，开始读取表盘信息")
        // 收到设备功能事件后读取表盘信息
        readClockDialInfo()
    }
    
    /**
     * 监听表盘信息返回事件
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onClockDialInfoEvent(event: ClockDialInfoEvent) {
        Log.d(TAG, "=== 收到 ClockDialInfoEvent 事件 ===")
        Log.d(TAG, "事件对象: $event")
        Log.d(TAG, "事件body是否为null: ${event.body == null}")
        try {
            val errorInfo = event.errorInfo
            Log.d(TAG, "错误信息: $errorInfo")
        } catch (e: Exception) {
            Log.w(TAG, "获取错误信息失败: ${e.message}")
        }
        
        if (event.body != null) {
            Log.d(TAG, "表盘信息body不为null，开始保存")
            try {
                // 直接使用SDK中的ClockDialInfoBody
                currentClockDialInfo = event.body
                
                Log.d(TAG, "表盘信息已保存到内存")
                logClockDialInfo(currentClockDialInfo)
                
            } catch (e: Exception) {
                Log.e(TAG, "保存表盘信息失败: ${e.message}", e)
                e.printStackTrace()
            }
        } else {
            Log.w(TAG, "表盘信息body为null")
            try {
                val errorInfo = event.errorInfo
                if (errorInfo != null && errorInfo.isNotEmpty()) {
                    Log.w(TAG, "错误信息: $errorInfo")
                } else {
                    Log.w(TAG, "未提供错误信息")
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取错误信息失败: ${e.message}")
            }
        }
        Log.d(TAG, "=== ClockDialInfoEvent 事件处理完成 ===")
    }
    
    /**
     * 读取表盘信息
     */
    private fun readClockDialInfo() {
        try {
            Log.d(TAG, "=== 开始读取表盘信息 ===")
            Log.d(TAG, "EventBus是否已注册: ${isRegistered}")
            Log.d(TAG, "当前EventBus注册状态: ${EventBus.getDefault().isRegistered(this)}")
            
            Log.d(TAG, "调用 SDKCmdManager.getClockDialInfo()")
            val command = SDKCmdManager.getClockDialInfo()
            
            if (command != null) {
                Log.d(TAG, "命令对象不为null")
                Log.d(TAG, "命令数组长度: ${command.size}")
                if (command.isNotEmpty()) {
                    Log.d(TAG, "表盘信息读取命令已获取，命令数据: ${command.contentToString()}")
                    Log.d(TAG, "命令应该已经通过SDK内部机制发送到设备")
                } else {
                    Log.e(TAG, "命令数组为空")
                }
            } else {
                Log.e(TAG, "获取表盘信息命令失败，返回null")
            }
            Log.d(TAG, "=== 读取表盘信息命令处理完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "读取表盘信息失败: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 获取当前设备的表盘信息
     */
    fun getCurrentClockDialInfo(): SdkClockDialInfoBody? {
        return currentClockDialInfo
    }
    
    /**
     * 检查表盘信息是否存在
     * @return true表示表盘信息存在，false表示不存在
     */
    fun hasClockDialInfo(): Boolean {
        return currentClockDialInfo != null && currentClockDialInfo!!.width > 0 && currentClockDialInfo!!.height > 0
    }
    
    /**
     * 手动触发读取表盘信息
     */
    fun requestClockDialInfo() {
        Log.d(TAG, "手动请求读取表盘信息")
        readClockDialInfo()
    }
    
    /**
     * 清除表盘信息
     */
    fun clearClockDialInfo() {
        currentClockDialInfo = null
        Log.d(TAG, "表盘信息已清除")
    }
    
    /**
     * 打印表盘信息日志
     */
    private fun logClockDialInfo(clockDialInfo: SdkClockDialInfoBody?) {
        if (clockDialInfo == null) {
            return
        }
        
        try {
            Log.d(TAG, "=== 表盘信息 ===")
            Log.d(TAG, "屏幕尺寸: ${clockDialInfo.width}x${clockDialInfo.height}")
            Log.d(TAG, "屏幕类型: ${if (clockDialInfo.screenType == 0) "方屏" else "圆屏"}")
            Log.d(TAG, "算法: ${clockDialInfo.algorithm}")
            Log.d(TAG, "配置: ${clockDialInfo.config}")
            Log.d(TAG, "表盘数量: ${clockDialInfo.pictureNums}")
            Log.d(TAG, "表盘版本: ${clockDialInfo.watchThemeVersion}")
            Log.d(TAG, "===============")
        } catch (e: Exception) {
            Log.e(TAG, "打印表盘信息失败: ${e.message}", e)
        }
    }
}

