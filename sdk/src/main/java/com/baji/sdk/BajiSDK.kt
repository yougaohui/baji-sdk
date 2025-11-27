package com.baji.sdk

import android.content.Context
import android.util.Log
import com.baji.protocol.BajiProtocolManager
import com.baji.protocol.BroadcastSender
import com.baji.protocol.service.SDKEventListener
import com.baji.sdk.service.*
import org.greenrobot.eventbus.EventBus
import xfkj.fitpro.activity.ota.manager.OTAInitializer

/**
 * 电子吧唧SDK主入口
 * 提供统一的API接口供第三方应用集成
 * 
 * @author Baji SDK Team
 * @since 1.0.0
 */
class BajiSDK private constructor() {
    
    companion object {
        private const val TAG = "BajiSDK"
        
        @Volatile
        private var instance: BajiSDK? = null
        
        /**
         * 获取SDK单例实例
         */
        @JvmStatic
        fun getInstance(): BajiSDK {
            return instance ?: synchronized(this) {
                instance ?: BajiSDK().also { instance = it }
            }
        }
    }
    
    private var isInitialized = false
    private var context: Context? = null
    private var config: SDKConfig? = null
    
    // 功能服务
    private var bluetoothService: BluetoothService? = null
    private var otaService: OTAService? = null
    private var videoConvertService: VideoConvertService? = null
    private var imageConvertService: ImageConvertService? = null
    private var watchFaceService: WatchFaceService? = null
    private var fileTransferService: FileTransferService? = null
    private var clockDialInfoService: ClockDialInfoService? = null
    
    // 协议管理器
    private var protocolManager: BajiProtocolManager? = null
    
    /**
     * 初始化SDK
     * 
     * @param context 应用上下文
     * @param config SDK配置
     * @param broadcastSender 广播发送器（用于蓝牙协议通信）
     * @return 初始化是否成功
     */
    fun initialize(
        context: Context,
        config: SDKConfig,
        broadcastSender: BroadcastSender
    ): Boolean {
        if (isInitialized) {
            Log.w(TAG, "SDK已经初始化，无需重复初始化")
            return true
        }
        
        return try {
            this.context = context.applicationContext
            this.config = config
            
            // 初始化日志
            if (config.enableLog) {
                Log.d(TAG, "开始初始化电子吧唧SDK...")
            }
            
            // 初始化OTA模块
            if (config.enableOTA) {
                val otaSuccess = OTAInitializer.initialize(context.applicationContext as android.app.Application)
                if (!otaSuccess) {
                    Log.e(TAG, "OTA模块初始化失败")
                }
            }
            
            // 初始化协议管理器
            protocolManager = BajiProtocolManager()
            protocolManager?.initialize(
                context = context,
                broadcastSender = broadcastSender,
                connectionCallback = null,
                packetSendCallback = null,
                packetReceiveCallback = null,
                sdkEventListener = object : SDKEventListener {
                    override fun onSDKEvent(sdkEvent: com.legend.mywatch.sdk.mywatchsdklib.android.event.BaseEvent?) {
                        // 直接将SDK原始事件发布到EventBus
                        if (sdkEvent != null) {
                            if (config.enableLog) {
                                Log.d(TAG, "=== SDKEventListener 收到SDK原始事件 ===")
                                Log.d(TAG, "事件类型: ${sdkEvent.javaClass.name}")
                                Log.d(TAG, "事件简单名称: ${sdkEvent.javaClass.simpleName}")
                                
                                // 如果是 ClockDialInfoEvent，打印详细信息
                                if (sdkEvent is com.legend.mywatch.sdk.mywatchsdklib.android.event.ClockDialInfoEvent) {
                                    Log.d(TAG, "--- ClockDialInfoEvent 详细信息 ---")
                                    Log.d(TAG, "body是否为null: ${sdkEvent.body == null}")
                                    if (sdkEvent.body != null) {
                                        val body = sdkEvent.body!!
                                        try {
                                            Log.d(TAG, "body.width: ${body.width}")
                                            Log.d(TAG, "body.height: ${body.height}")
                                            Log.d(TAG, "body.screenType: ${body.screenType}")
                                            Log.d(TAG, "body.algorithm: ${body.algorithm}")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "读取body字段失败: ${e.message}", e)
                                        }
                                    }
                                    try {
                                        val errorInfo = sdkEvent.errorInfo
                                        Log.d(TAG, "errorInfo: $errorInfo")
                                    } catch (e: Exception) {
                                        Log.d(TAG, "errorInfo获取失败: ${e.message}")
                                    }
                                    Log.d(TAG, "--- ClockDialInfoEvent 详细信息结束 ---")
                                }
                            }
                            
                            // 发布到EventBus
                            EventBus.getDefault().post(sdkEvent)
                            
                            if (config.enableLog) {
                                Log.d(TAG, "SDK原始事件已发布到EventBus: ${sdkEvent.javaClass.simpleName}")
                                Log.d(TAG, "=== SDKEventListener 事件处理完成 ===")
                            }
                        } else {
                            if (config.enableLog) {
                                Log.w(TAG, "SDKEventListener 收到null事件")
                            }
                        }
                    }
                }
            )
            
            // 初始化各个服务
            bluetoothService = BluetoothService(context, protocolManager)
            otaService = OTAService(context, config)
            clockDialInfoService = ClockDialInfoService()
            clockDialInfoService?.initialize()
            videoConvertService = VideoConvertService(context, config, clockDialInfoService)
            imageConvertService = ImageConvertService(context, config)
            watchFaceService = WatchFaceService(context, config)
            fileTransferService = FileTransferService(context, protocolManager, clockDialInfoService)
            
            isInitialized = true
            
            if (config.enableLog) {
                Log.d(TAG, "电子吧唧SDK初始化成功")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "SDK初始化失败: ${e.message}", e)
            isInitialized = false
            false
        }
    }
    
    /**
     * 检查SDK是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
    
    /**
     * 获取蓝牙连接服务
     */
    fun getBluetoothService(): BluetoothService {
        checkInitialized()
        return bluetoothService ?: throw IllegalStateException("蓝牙服务未初始化")
    }
    
    /**
     * 获取OTA升级服务
     */
    fun getOTAService(): OTAService {
        checkInitialized()
        return otaService ?: throw IllegalStateException("OTA服务未初始化")
    }
    
    /**
     * 获取视频转换服务
     */
    fun getVideoConvertService(): VideoConvertService {
        checkInitialized()
        return videoConvertService ?: throw IllegalStateException("视频转换服务未初始化")
    }
    
    /**
     * 获取图片转换服务
     */
    fun getImageConvertService(): ImageConvertService {
        checkInitialized()
        return imageConvertService ?: throw IllegalStateException("图片转换服务未初始化")
    }
    
    /**
     * 获取表盘管理服务
     */
    fun getWatchFaceService(): WatchFaceService {
        checkInitialized()
        return watchFaceService ?: throw IllegalStateException("表盘管理服务未初始化")
    }
    
    /**
     * 获取文件传输服务
     */
    fun getFileTransferService(): FileTransferService {
        checkInitialized()
        return fileTransferService ?: throw IllegalStateException("文件传输服务未初始化")
    }
    
    /**
     * 获取表盘信息服务
     */
    fun getClockDialInfoService(): ClockDialInfoService {
        checkInitialized()
        return clockDialInfoService ?: throw IllegalStateException("表盘信息服务未初始化")
    }
    
    /**
     * 获取协议管理器（内部使用）
     */
    internal fun getProtocolManager(): BajiProtocolManager? {
        return protocolManager
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        if (!isInitialized) {
            return
        }
        
        bluetoothService?.cleanup()
        otaService?.cleanup()
        videoConvertService?.cleanup()
        imageConvertService?.cleanup()
        watchFaceService?.cleanup()
        fileTransferService?.cleanup()
        clockDialInfoService?.cleanup()
        
        protocolManager?.cleanup()
        protocolManager = null
        
        context = null
        config = null
        isInitialized = false
        
        Log.d(TAG, "SDK资源已清理")
    }
    
    /**
     * 检查是否已初始化
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("SDK未初始化，请先调用initialize()方法")
        }
    }
}

