package com.baji.sdk.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.baji.sdk.SDKConfig
import com.baji.sdk.callback.VideoConvertCallback
import com.baji.sdk.model.VideoConvertParams
import java.io.File
import java.io.RandomAccessFile

/**
 * 视频转换服务
 * 提供视频转AVI、格式转换等功能
 */
class VideoConvertService(
    private val context: Context,
    private val config: SDKConfig,
    private val clockDialInfoService: ClockDialInfoService?
) {
    private val TAG = "VideoConvertService"
    private var convertCallback: VideoConvertCallback? = null
    
    // 视频质量重试相关（每次转换独立的重试状态）
    private data class RetryState(
        var retryCount: Int = 0,
        var currentQuality: Int = 10  // 默认质量值，与主项目一致
    )
    
    private val retryStateMap = mutableMapOf<String, RetryState>()  // key: outputPath
    private val MAX_RETRY_COUNT = 3  // 最大重试次数
    private val MAX_QUALITY = 31  // 最大质量值（最低质量）
    private val BUFFER_SIZE_LIMIT = 20 * 1024  // 缓冲区大小限制：20KB
    
    /**
     * 设置转换回调
     */
    fun setConvertCallback(callback: VideoConvertCallback?) {
        this.convertCallback = callback
    }
    
    /**
     * 将视频转换为AVI格式
     * 支持自动质量重试机制：如果转换后的AVI文件缓冲区大小超过20KB，会自动降低质量重试
     * @param inputPath 输入视频路径
     * @param outputPath 输出AVI文件路径
     * @param params 转换参数
     */
    fun convertToAVI(
        inputPath: String,
        outputPath: String,
        params: VideoConvertParams
    ) {
        convertToAVIInternal(inputPath, outputPath, params)
    }
    
    /**
     * 内部转换方法，支持重试机制
     */
    private fun convertToAVIInternal(
        inputPath: String,
        outputPath: String,
        params: VideoConvertParams,
        isRetry: Boolean = false
    ) {
        try {
            // 获取或创建重试状态
            val retryState = retryStateMap.getOrPut(outputPath) { RetryState() }
            
            // 如果是重试，重置重试状态
            if (!isRetry) {
                retryState.retryCount = 0
                retryState.currentQuality = params.quality.takeIf { it > 0 } ?: 10
            }
            
            Log.d(TAG, "开始转换视频为AVI: $inputPath -> $outputPath")
            if (isRetry) {
                Log.d(TAG, "重试转换，当前质量: ${retryState.currentQuality}, 重试次数: ${retryState.retryCount}/$MAX_RETRY_COUNT")
            }
            
            // 从表盘信息获取视频尺寸（优先使用表盘信息，确保视频尺寸匹配设备屏幕）
            val videoSize = getVideoSizeFromClockDialInfo()
            val updatedParams = params.copy(
                targetWidth = videoSize.first,
                targetHeight = videoSize.second,
                quality = retryState.currentQuality  // 使用当前质量值（可能是重试后的值）
            )
            Log.d(TAG, "使用表盘信息尺寸进行转换: ${videoSize.first}x${videoSize.second}, 质量: ${retryState.currentQuality}")
            
            convertCallback?.onConvertStart()
            
            // 构建FFmpeg命令（使用更新后的参数）
            val command = buildFFmpegCommand(inputPath, outputPath, updatedParams)
            Log.d(TAG, "FFmpeg命令: $command")
            
            // 执行转换
            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    val outputFile = File(outputPath)
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "视频转换成功: $outputPath, 大小: ${outputFile.length()} bytes")
                        
                        // 检查AVI文件的缓冲区大小
                        val bufferSize = getAviStreamBufferSize(outputPath)
                        Log.d(TAG, "检测到的dwSuggestedBufferSize: $bufferSize bytes (限制: ${BUFFER_SIZE_LIMIT} bytes = ${BUFFER_SIZE_LIMIT / 1024}KB)")
                        
                        if (bufferSize > 0 && bufferSize >= BUFFER_SIZE_LIMIT) {
                            // 缓冲区大小超过限制，需要降低质量重试
                            if (retryState.retryCount < MAX_RETRY_COUNT && retryState.currentQuality < MAX_QUALITY) {
                                // 可以继续重试：降低质量（增加q:v值）
                                retryState.retryCount++
                                
                                // 每次重试增加质量值（降低质量），逐步增加增量
                                val qualityIncrement = minOf(
                                    5 + (retryState.retryCount - 1) * 2,
                                    MAX_QUALITY - retryState.currentQuality
                                )
                                retryState.currentQuality = minOf(MAX_QUALITY, retryState.currentQuality + qualityIncrement)
                                
                                Log.w(TAG, "缓冲区大小超过${BUFFER_SIZE_LIMIT / 1024}KB (${bufferSize / 1024}KB)，降低质量重试。当前质量: ${retryState.currentQuality}, 重试次数: ${retryState.retryCount}/$MAX_RETRY_COUNT")
                                
                                // 删除当前文件，重新转换
                                if (outputFile.delete()) {
                                    Log.d(TAG, "已删除文件，准备重新转换")
                                }
                                
                                // 重新执行转换
                                convertToAVIInternal(inputPath, outputPath, params, isRetry = true)
                                return@executeAsync
                            } else {
                                // 达到最大重试次数或质量上限，提示失败
                                Log.e(TAG, "达到最大重试次数($MAX_RETRY_COUNT)或质量上限($MAX_QUALITY)，缓冲区大小仍超过${BUFFER_SIZE_LIMIT / 1024}KB (${bufferSize / 1024}KB)")
                                retryStateMap.remove(outputPath)  // 清理重试状态
                                convertCallback?.onConvertFailed("视频文件缓冲区过大 (${bufferSize / 1024}KB)，已达到最大重试次数")
                                return@executeAsync
                            }
                        }
                        
                        // 缓冲区大小符合要求，转换成功
                        retryStateMap.remove(outputPath)  // 清理重试状态
                        convertCallback?.onConvertSuccess(outputPath)
                    } else {
                        Log.e(TAG, "转换后的文件不存在或为空")
                        retryStateMap.remove(outputPath)  // 清理重试状态
                        convertCallback?.onConvertFailed("转换后的文件不存在或为空")
                    }
                } else {
                    val output = session.output
                    Log.e(TAG, "视频转换失败: $output")
                    retryStateMap.remove(outputPath)  // 清理重试状态
                    convertCallback?.onConvertFailed("转换失败: $output")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "视频转换异常: ${e.message}", e)
            retryStateMap.remove(outputPath)  // 清理重试状态
            convertCallback?.onConvertFailed("转换异常: ${e.message}")
        }
    }
    
    /**
     * 将AVI转换为MP4
     */
    fun convertAVIToMP4(aviPath: String, mp4Path: String) {
        try {
            Log.d(TAG, "开始将AVI转换为MP4: $aviPath -> $mp4Path")
            convertCallback?.onConvertStart()
            
            val command = "-y -i \"$aviPath\" -c:v libx264 -preset fast -crf 23 -pix_fmt yuv420p -c:a aac -b:a 64k -f mp4 \"$mp4Path\""
            
            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    val outputFile = File(mp4Path)
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "AVI转MP4成功: $mp4Path")
                        convertCallback?.onConvertSuccess(mp4Path)
                    } else {
                        convertCallback?.onConvertFailed("转换后的文件不存在或为空")
                    }
                } else {
                    val output = session.output
                    Log.e(TAG, "AVI转MP4失败: $output")
                    convertCallback?.onConvertFailed("转换失败: $output")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AVI转MP4异常: ${e.message}", e)
            convertCallback?.onConvertFailed("转换异常: ${e.message}")
        }
    }
    
    /**
     * 将AVI转换为GIF
     */
    fun convertAVIToGIF(aviPath: String, gifPath: String) {
        try {
            Log.d(TAG, "开始将AVI转换为GIF: $aviPath -> $gifPath")
            convertCallback?.onConvertStart()
            
            // 生成GIF（前3秒，fps=2，缩放为200宽度）
            val command = "-y -i \"$aviPath\" -t 3 -vf \"fps=2,scale=200:-1\" -loop 0 \"$gifPath\""
            
            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    val outputFile = File(gifPath)
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "AVI转GIF成功: $gifPath")
                        convertCallback?.onConvertSuccess(gifPath)
                    } else {
                        convertCallback?.onConvertFailed("转换后的文件不存在或为空")
                    }
                } else {
                    val output = session.output
                    Log.e(TAG, "AVI转GIF失败: $output")
                    convertCallback?.onConvertFailed("转换失败: $output")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AVI转GIF异常: ${e.message}", e)
            convertCallback?.onConvertFailed("转换异常: ${e.message}")
        }
    }
    
    /**
     * 构建FFmpeg命令
     * 参考主项目的实现，使用完整的编码参数
     */
    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        params: VideoConvertParams
    ): String {
        val ffmpegConfig = config.ffmpegConfig
        val quality = params.quality.takeIf { it > 0 } ?: 10  // 默认质量值，与主项目一致
        val fps = params.fps.takeIf { it > 0 } ?: 5  // 默认5fps，与主项目一致
        
        // 构建视频滤镜
        val videoFilter = buildVideoFilter(params)
        
        // 构建完整命令
        val startTime = params.startTime
        val duration = params.duration
        
        val commandBuilder = StringBuilder()
        commandBuilder.append("-y")
        
        if (startTime > 0) {
            commandBuilder.append(" -ss ").append(startTime)
        }
        
        commandBuilder.append(" -i \"").append(inputPath).append("\"")
        
        if (duration > 0) {
            commandBuilder.append(" -t ").append(duration)
        }
        
        if (videoFilter.isNotEmpty()) {
            commandBuilder.append(" -vf \"").append(videoFilter).append("\"")
        }
        
        // 视频编码参数（参考主项目）
        commandBuilder.append(" -r ").append(fps)  // 帧率
        commandBuilder.append(" -c:v mjpeg")  // MJPEG编码
        commandBuilder.append(" -vtag mjpg")  // 视频标签
        commandBuilder.append(" -pix_fmt yuvj420p")  // JPEG色彩空间，与主项目一致
        commandBuilder.append(" -q:v ").append(quality)  // 质量值
        
        // 编码器参数（参考主项目）
        commandBuilder.append(" -coder 1")  // 使用编码器1
        commandBuilder.append(" -flags +loop+global_header")  // 循环标志和全局头
        commandBuilder.append(" -pred 1")  // 预测模式
        commandBuilder.append(" -qmin 10")  // 最小质量值
        commandBuilder.append(" -qmax 20")  // 最大质量值
        commandBuilder.append(" -vsync cfr")  // 恒定帧率
        commandBuilder.append(" -video_track_timescale ").append(fps)  // 视频轨道时间刻度
        commandBuilder.append(" -packetsize 4096")  // 数据包大小
        
        // 音频编码参数（参考主项目）
        commandBuilder.append(" -c:a pcm_s16le")  // PCM 16位小端音频
        commandBuilder.append(" -ar 16000")  // 音频采样率16kHz，与主项目一致
        commandBuilder.append(" -ac 1")  // 单声道
        commandBuilder.append(" -f avi")  // AVI格式
        commandBuilder.append(" \"").append(outputPath).append("\"")
        
        return commandBuilder.toString()
    }
    
    /**
     * 构建视频滤镜
     * 参考主项目的实现，使用智能裁剪策略消除黑边
     */
    private fun buildVideoFilter(params: VideoConvertParams): String {
        val fps = params.fps.takeIf { it > 0 } ?: 5
        
        // 获取屏幕类型（用于决定裁剪策略）
        val screenType = try {
            clockDialInfoService?.getCurrentClockDialInfo()?.screenType ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "获取屏幕类型失败，使用默认值: ${e.message}")
            0
        }
        
        // 检查是否有用户选择的裁剪区域
        if (params.cropRegion != null) {
            // 使用用户选择的裁剪区域
            val crop = params.cropRegion!!
            // 先裁剪用户选择的区域，然后缩放到目标尺寸
            return "crop=${crop.width}:${crop.height}:${crop.x}:${crop.y}," +
                   "scale=${params.targetWidth}:${params.targetHeight}:force_original_aspect_ratio=decrease," +
                   "pad=${params.targetWidth}:${params.targetHeight}:(ow-iw)/2:(oh-ih)/2," +
                   "fps=$fps"
        } else {
            // 使用默认的智能裁剪策略（居中裁剪，消除黑边）
            // 先缩放到能完全填充目标尺寸的最小尺寸，再裁剪到目标尺寸
            // 这样确保视频内容铺满整个屏幕，无黑边
            return "scale=${params.targetWidth}:${params.targetHeight}:force_original_aspect_ratio=increase," +
                   "crop=${params.targetWidth}:${params.targetHeight}:(iw-${params.targetWidth})/2:(ih-${params.targetHeight})/2," +
                   "fps=$fps"
        }
    }
    
    /**
     * 根据表盘信息获取视频尺寸
     * 用于视频转换，从表盘信息中获取实际的宽高值
     */
    private fun getVideoSizeFromClockDialInfo(): Pair<Int, Int> {
        return try {
            val clockDialInfo = clockDialInfoService?.getCurrentClockDialInfo()
            if (clockDialInfo != null) {
                val width = clockDialInfo.width.toInt()
                val height = clockDialInfo.height.toInt()
                
                Log.d(TAG, "=== 视频分辨率设置 ===")
                Log.d(TAG, "从表盘信息获取分辨率: ${width}x${height}")
                Log.d(TAG, "屏幕类型: ${if (clockDialInfo.screenType == 0) "方屏" else "圆屏"}")
                
                if (width > 0 && height > 0) {
                    Pair(width, height)
                } else {
                    Log.w(TAG, "表盘信息尺寸无效，使用默认分辨率")
                    Pair(320, 384)
                }
            } else {
                Log.w(TAG, "表盘信息不存在，使用默认分辨率")
                Pair(320, 384)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取表盘信息失败，使用默认分辨率: ${e.message}", e)
            Pair(320, 384)
        }
    }
    
    /**
     * 根据表盘信息获取帧尺寸
     * 用于生成视频帧缩略图，从表盘信息中获取实际的宽高值
     */
    private fun getFrameSizeFromClockDialInfo(): Pair<Int, Int> {
        return try {
            val clockDialInfo = clockDialInfoService?.getCurrentClockDialInfo()
            if (clockDialInfo != null) {
                val width = clockDialInfo.width.toInt()
                val height = clockDialInfo.height.toInt()
                
                Log.d(TAG, "=== 帧尺寸设置 ===")
                Log.d(TAG, "从表盘信息获取帧尺寸: ${width}x${height}")
                
                if (width > 0 && height > 0) {
                    Pair(width, height)
                } else {
                    Log.w(TAG, "表盘信息尺寸无效，使用默认帧尺寸")
                    Pair(320, 384)
                }
            } else {
                Log.w(TAG, "表盘信息不存在，使用默认帧尺寸")
                Pair(320, 384)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取表盘信息失败，使用默认帧尺寸: ${e.message}", e)
            Pair(320, 384)
        }
    }
    
    /**
     * 读取AVI文件中strh块的dwSuggestedBufferSize值
     * 
     * AVI文件格式说明：
     * - RIFF块包含多个子块
     * - strh块（Stream Header Chunk）包含流的头信息
     * - 在AVIStreamHeader结构体中，dwSuggestedBufferSize的偏移是0x24 (36字节)
     * 
     * strh块结构（AVIStreamHeader）：
     * ==============================
     * 偏移  大小  字段名
     * 0x00  4     fccType          - 流类型 ("vids"=视频, "auds"=音频)
     * 0x04  4     fccHandler       - 编码器标识
     * 0x08  4     dwFlags          - 标志
     * 0x0C  2     wPriority        - 优先级
     * 0x0E  2     wLanguage        - 语言
     * 0x10  4     dwInitialFrames  - 初始帧数
     * 0x14  4     dwScale          - 时间刻度
     * 0x18  4     dwRate           - 数据速率
     * 0x1C  4     dwStart          - 开始时间
     * 0x20  4     dwLength         - 数据长度
     * 0x24  4     dwSuggestedBufferSize ⭐ 建议的缓冲区大小（我们要读取的字段）
     * 0x28  4     dwQuality        - 质量
     * 0x2C  4     dwSampleSize     - 样本大小
     * 
     * 所以 dwSuggestedBufferSize 在 strh 块开始后的偏移 0x24 (36字节) 处
     * 但根据实际测试，有些编码器可能将其放在 0x20 (32字节) 处
     * 
     * @param filePath AVI文件路径
     * @return dwSuggestedBufferSize值（字节），如果读取失败返回-1
     */
    private fun getAviStreamBufferSize(filePath: String): Int {
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(filePath, "r")
            val fileSize = raf.length()
            Log.d(TAG, "=== 开始检测AVI文件缓冲区大小 ===")
            Log.d(TAG, "文件路径: $filePath")
            Log.d(TAG, "文件大小: $fileSize bytes")
            
            // 读取文件头，查找strh块（Stream Header Chunk）
            // strh块通常在文件的前2KB内，读取4096字节以确保找到
            val header = ByteArray(4096)
            val bytesRead = raf.read(header)
            
            if (bytesRead < 100) {
                Log.w(TAG, "文件头太短，无法读取dwSuggestedBufferSize")
                return -1
            }
            
            // 检查是否是AVI文件
            val isAvi = (header[0] == 'R'.toByte() && header[1] == 'I'.toByte() && 
                        header[2] == 'F'.toByte() && header[3] == 'F'.toByte()) &&
                       (header[8] == 'A'.toByte() && header[9] == 'V'.toByte() && 
                        header[10] == 'I'.toByte() && header[11] == ' '.toByte())
            Log.d(TAG, "是否为AVI文件: $isAvi")
            
            // 查找所有"strh"标识（可能有多个流：视频流和音频流）
            // 每个流都有自己的strh块，我们需要找到最大的dwSuggestedBufferSize值
            var maxBufferSize = -1
            var strhCount = 0
            
            for (i in 0 until (bytesRead - 4)) {
                // 查找 "strh" 四个字节的标识符
                if (header[i] == 's'.toByte() && header[i + 1] == 't'.toByte() && 
                    header[i + 2] == 'r'.toByte() && header[i + 3] == 'h'.toByte()) {
                    strhCount++
                    // 找到strh块，记录偏移位置
                    val strhOffset = i
                    Log.d(TAG, "找到strh块 #$strhCount，文件偏移: 0x${strhOffset.toString(16)} ($strhOffset)")
                    
                    // strh块结构：
                    // "strh" (4字节) + size (4字节) + AVIStreamHeader结构体
                    // 所以数据部分从 strhOffset + 8 开始
                    val dataStart = strhOffset + 8
                    
                    // 在AVIStreamHeader结构体中，dwSuggestedBufferSize的偏移是0x24
                    // 所以实际文件偏移 = strhOffset + 8 + 0x24 = strhOffset + 0x2C (44字节)
                    // 但也要尝试其他可能的位置（有些编码器可能有不同的布局）
                    val possibleOffsets = intArrayOf(0x2C, 0x28, 0x24, 0x20)  // 从最可能的位置开始
                    
                    for (offset in possibleOffsets) {
                        val bufferSizeOffset = strhOffset + offset
                        
                        try {
                            val bufferSize: Int
                            if (bufferSizeOffset + 4 <= bytesRead) {
                                // 从已读取的header中读取
                                bufferSize = (header[bufferSizeOffset].toInt() and 0xFF) or
                                           ((header[bufferSizeOffset + 1].toInt() and 0xFF) shl 8) or
                                           ((header[bufferSizeOffset + 2].toInt() and 0xFF) shl 16) or
                                           ((header[bufferSizeOffset + 3].toInt() and 0xFF) shl 24)
                            } else {
                                // 如果偏移超出已读取的范围，需要重新读取
                                raf.seek(bufferSizeOffset.toLong())
                                val sizeBytes = ByteArray(4)
                                val readBytes = raf.read(sizeBytes)
                                if (readBytes != 4) {
                                    continue
                                }
                                bufferSize = (sizeBytes[0].toInt() and 0xFF) or
                                           ((sizeBytes[1].toInt() and 0xFF) shl 8) or
                                           ((sizeBytes[2].toInt() and 0xFF) shl 16) or
                                           ((sizeBytes[3].toInt() and 0xFF) shl 24)
                            }
                            
                            Log.d(TAG, "  strh偏移 +0x${offset.toString(16)} (绝对偏移 0x${bufferSizeOffset.toString(16)}) 处的值: $bufferSize bytes (${if (bufferSize > 0) "${bufferSize / 1024} KB" else "0"})")
                            
                            // 验证：dwSuggestedBufferSize应该在合理范围内（通常几KB到几百KB）
                            // 对于360x360@5fps的MJPEG视频，应该在10KB-500KB之间
                            if (bufferSize >= 1024 && bufferSize < 10 * 1024 * 1024) {
                                Log.d(TAG, "  ✓ 找到有效的dwSuggestedBufferSize: $bufferSize bytes (${bufferSize / 1024} KB)")
                                
                                // 取最大值（通常视频流的缓冲区更大）
                                if (bufferSize > maxBufferSize) {
                                    maxBufferSize = bufferSize
                                }
                                // 找到有效值后，可以继续尝试其他位置，但优先使用这个
                            } else if (bufferSize > 0 && bufferSize < 50 * 1024 * 1024) {
                                // 值在范围内但可能不太合理，记录但不使用
                                Log.d(TAG, "  ? 读取到值但可能不是dwSuggestedBufferSize: $bufferSize bytes")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "  读取偏移 +0x${offset.toString(16)} 时出错: ${e.message}")
                        }
                    }
                }
            }
            
            if (strhCount == 0) {
                Log.w(TAG, "未找到任何strh块！")
            } else {
                Log.d(TAG, "共找到 $strhCount 个strh块")
            }
            
            if (maxBufferSize == -1) {
                Log.w(TAG, "未找到有效的dwSuggestedBufferSize值")
            } else {
                Log.d(TAG, "最终检测到的最大dwSuggestedBufferSize: $maxBufferSize bytes (${maxBufferSize / 1024} KB)")
            }
            
            return maxBufferSize
            
        } catch (e: Exception) {
            Log.e(TAG, "读取AVI文件dwSuggestedBufferSize失败", e)
            return -1
        } finally {
            try {
                raf?.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭文件失败", e)
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        convertCallback = null
        retryStateMap.clear()
        Log.d(TAG, "视频转换服务资源已清理")
    }
}

