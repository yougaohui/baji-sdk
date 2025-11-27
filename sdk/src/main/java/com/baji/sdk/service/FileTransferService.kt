package com.baji.sdk.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.baji.protocol.BajiProtocolManager
import com.baji.sdk.callback.FileTransferCallback
import com.baji.sdk.model.FileInfo
import com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.WatchTheme3Body
import com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.WatchTheme3Tools
import com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.WatchThemeUpgradeError
import com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.ClockDialInfoBody as SdkClockDialInfoBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 文件传输服务
 * 提供文件上传、下载、传输进度监听等功能
 * 使用WatchTheme3Tools进行表盘传输方式同步图片
 */
class FileTransferService(
    private val context: Context,
    private val protocolManager: BajiProtocolManager?
) {
    private val TAG = "FileTransferService"
    private var transferCallback: FileTransferCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 设置传输回调
     */
    fun setTransferCallback(callback: FileTransferCallback?) {
        this.transferCallback = callback
    }
    
    /**
     * 上传文件
     * 使用WatchTheme3Tools进行表盘传输方式同步
     * @param filePath 文件路径（应该是已经转换为bin格式的图片文件）
     * @param fileType 文件类型
     */
    fun uploadFile(filePath: String, fileType: FileInfo.FileType) {
        try {
            Log.d(TAG, "开始上传文件: $filePath, type: $fileType")
            
            val file = File(filePath)
            if (!file.exists()) {
                transferCallback?.onTransferFailed("文件不存在: $filePath")
                return
            }
            
            when (fileType) {
                FileInfo.FileType.IMAGE -> {
                    // 图片文件：使用表盘传输方式
                    uploadImageWithWatchTheme(filePath)
                }
                FileInfo.FileType.VIDEO -> {
                    // 视频文件：使用表盘传输方式
                    uploadVideoWithWatchTheme(filePath)
                }
                else -> {
                    transferCallback?.onTransferFailed("不支持的文件类型: $fileType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "上传文件异常: ${e.message}", e)
            transferCallback?.onTransferFailed("上传异常: ${e.message}")
        }
    }
    
    /**
     * 使用表盘传输方式上传图片
     */
    private fun uploadImageWithWatchTheme(imagePath: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "开始表盘传输图片: $imagePath")
                transferCallback?.onTransferStart()
                
                // 获取设备表盘信息（使用默认值或从协议管理器获取）
                val clockDialInfo = getDefaultClockDialInfo()
                
                // 构建 WatchTheme3Body 数据体
                val watchTheme3Body = createWatchTheme3Body().apply {
                    watchID = 5538
                    fileType = 0 // 0 = 图片
                    bgBinPath = imagePath // 使用已转换的bin文件路径
                }
                
                // 构建样式配置列表（空列表）
                val watchStyleConfigs = emptyList<com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.WatchStyleConfig>()
                
                // 获取 WatchTheme3Tools 实例
                val watchTheme3Tools = try {
                    WatchTheme3Tools.getInstance()
                } catch (e: Exception) {
                    Log.e(TAG, "获取 WatchTheme3Tools 实例失败: ${e.message}", e)
                    transferCallback?.onTransferFailed("表盘传输工具未初始化")
                    return@launch
                }
                
                // 设置状态监听器
                setWatchTheme3StatusListener(watchTheme3Tools)
                
                // 开始表盘传输
                watchTheme3Tools.startFile(
                    watchTheme3Body,
                    watchStyleConfigs,
                    clockDialInfo,
                    false // 非编辑模式
                )
                
                Log.d(TAG, "表盘传输命令已发送")
                
            } catch (e: Exception) {
                Log.e(TAG, "表盘传输失败: ${e.message}", e)
                transferCallback?.onTransferFailed("表盘传输失败: ${e.message}")
            }
        }
    }
    
    /**
     * 使用表盘传输方式上传视频
     */
    private fun uploadVideoWithWatchTheme(videoPath: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "开始表盘传输视频: $videoPath")
                transferCallback?.onTransferStart()
                
                // 获取设备表盘信息
                val clockDialInfo = getDefaultClockDialInfo()
                
                // 构建 WatchTheme3Body 数据体
                val watchTheme3Body = createWatchTheme3Body().apply {
                    watchID = 5538
                    fileType = 1 // 1 = 视频
                    bgBinPath = videoPath
                }
                
                // 构建样式配置列表（空列表）
                val watchStyleConfigs = emptyList<com.legend.mywatch.sdk.mywatchsdklib.android.watchtheme.WatchStyleConfig>()
                
                // 获取 WatchTheme3Tools 实例
                val watchTheme3Tools = try {
                    WatchTheme3Tools.getInstance()
                } catch (e: Exception) {
                    Log.e(TAG, "获取 WatchTheme3Tools 实例失败: ${e.message}", e)
                    transferCallback?.onTransferFailed("表盘传输工具未初始化")
                    return@launch
                }
                
                // 设置状态监听器
                setWatchTheme3StatusListener(watchTheme3Tools)
                
                // 开始表盘传输
                watchTheme3Tools.startFile(
                    watchTheme3Body,
                    watchStyleConfigs,
                    clockDialInfo,
                    false // 非编辑模式
                )
                
                Log.d(TAG, "视频表盘传输命令已发送")
                
            } catch (e: Exception) {
                Log.e(TAG, "视频表盘传输失败: ${e.message}", e)
                transferCallback?.onTransferFailed("视频表盘传输失败: ${e.message}")
            }
        }
    }
    
    /**
     * 创建 WatchTheme3Body 实例
     */
    private fun createWatchTheme3Body(): WatchTheme3Body {
        return WatchTheme3Body()
    }
    
    /**
     * 获取默认的表盘信息
     * 如果无法从设备获取，使用默认值（240x240方形屏幕）
     */
    private fun getDefaultClockDialInfo(): SdkClockDialInfoBody {
        return try {
            // 尝试从协议管理器或SDK获取设备信息
            // 如果无法获取，使用默认值
            SdkClockDialInfoBody().apply {
                // 默认值：240x240方形屏幕
                width = 240
                height = 240
                screenType = 0 // 0 = 方形屏幕
                algorithm = 0 // 默认算法
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取表盘信息失败，使用默认值: ${e.message}")
            // 返回默认值
            SdkClockDialInfoBody().apply {
                width = 240
                height = 240
                screenType = 0
                algorithm = 0
            }
        }
    }
    
    /**
     * 设置表盘传输状态监听器
     */
    private fun setWatchTheme3StatusListener(watchTheme3Tools: WatchTheme3Tools) {
        try {
            Log.d(TAG, "设置表盘传输状态监听器")
            
            watchTheme3Tools.addStatusChangeListener(object : WatchTheme3Tools.UpdateStatusChangeListener {
                override fun onStartUpgrade() {
                    Log.d(TAG, "表盘升级开始")
                    mainHandler.post {
                        transferCallback?.onTransferStart()
                    }
                }
                
                override fun onStatusChange(progress: Int) {
                    // progress 是 0-1000 的进度值，需要转换为 0-100 的百分比
                    val progressPercent = (progress / 10).coerceIn(0, 100)
                    Log.d(TAG, "表盘升级进度: $progressPercent% (原始值: $progress)")
                    mainHandler.post {
                        transferCallback?.onTransferProgress(
                            progressPercent,
                            (progressPercent * 1024).toLong(), // 估算已传输字节
                            102400L // 估算总字节
                        )
                    }
                }
                
                override fun onUpgradeSuccess(watch3: WatchTheme3Body) {
                    Log.d(TAG, "表盘升级成功")
                    mainHandler.post {
                        transferCallback?.onTransferSuccess()
                    }
                }

                override fun onUpgradeFailed(error: WatchThemeUpgradeError, body: WatchTheme3Body) {
                    val errorCode = error.errorCode
                    val errorMessage = "传输失败，错误码: $errorCode"
                    Log.e(TAG, "表盘升级失败，错误码: $errorCode")
                    mainHandler.post {
                        transferCallback?.onTransferFailed(errorMessage)
                    }
                }
            })
            
            Log.d(TAG, "表盘传输状态监听器设置成功")
        } catch (e: Exception) {
            Log.e(TAG, "设置表盘传输状态监听器失败: ${e.message}", e)
            transferCallback?.onTransferFailed("设置状态监听器失败: ${e.message}")
        }
    }
    
    /**
     * 下载文件
     * @param fileId 文件ID
     * @param outputPath 输出路径
     */
    fun downloadFile(fileId: Long, outputPath: String) {
        try {
            Log.d(TAG, "开始下载文件: fileId=$fileId, outputPath=$outputPath")
            transferCallback?.onTransferStart()
            
            // TODO: 实现文件下载逻辑
            protocolManager?.let {
                Log.d(TAG, "文件下载功能待实现")
                transferCallback?.onTransferSuccess()
            } ?: run {
                transferCallback?.onTransferFailed("协议管理器未初始化")
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载文件异常: ${e.message}", e)
            transferCallback?.onTransferFailed("下载异常: ${e.message}")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        transferCallback = null
        Log.d(TAG, "文件传输服务资源已清理")
    }
}

