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

/**
 * 视频转换服务
 * 提供视频转AVI、格式转换等功能
 */
class VideoConvertService(
    private val context: Context,
    private val config: SDKConfig
) {
    private val TAG = "VideoConvertService"
    private var convertCallback: VideoConvertCallback? = null
    
    /**
     * 设置转换回调
     */
    fun setConvertCallback(callback: VideoConvertCallback?) {
        this.convertCallback = callback
    }
    
    /**
     * 将视频转换为AVI格式
     * @param inputPath 输入视频路径
     * @param outputPath 输出AVI文件路径
     * @param params 转换参数
     */
    fun convertToAVI(
        inputPath: String,
        outputPath: String,
        params: VideoConvertParams
    ) {
        try {
            Log.d(TAG, "开始转换视频为AVI: $inputPath -> $outputPath")
            convertCallback?.onConvertStart()
            
            // 构建FFmpeg命令
            val command = buildFFmpegCommand(inputPath, outputPath, params)
            Log.d(TAG, "FFmpeg命令: $command")
            
            // 执行转换
            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    val outputFile = File(outputPath)
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "视频转换成功: $outputPath, 大小: ${outputFile.length()} bytes")
                        convertCallback?.onConvertSuccess(outputPath)
                    } else {
                        Log.e(TAG, "转换后的文件不存在或为空")
                        convertCallback?.onConvertFailed("转换后的文件不存在或为空")
                    }
                } else {
                    val output = session.output
                    Log.e(TAG, "视频转换失败: $output")
                    convertCallback?.onConvertFailed("转换失败: $output")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "视频转换异常: ${e.message}", e)
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
     */
    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        params: VideoConvertParams
    ): String {
        val ffmpegConfig = config.ffmpegConfig
        val quality = params.quality.takeIf { it > 0 } ?: ffmpegConfig.videoQuality
        val fps = params.fps.takeIf { it > 0 } ?: ffmpegConfig.defaultFps
        
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
        
        commandBuilder.append(" -c:v mjpeg")
        commandBuilder.append(" -q:v ").append(quality)
        commandBuilder.append(" -pix_fmt yuv420p")
        commandBuilder.append(" -c:a pcm_s16le")
        commandBuilder.append(" -ar ").append(ffmpegConfig.audioSampleRate)
        commandBuilder.append(" -ac 1")
        commandBuilder.append(" -f avi")
        commandBuilder.append(" \"").append(outputPath).append("\"")
        
        return commandBuilder.toString()
    }
    
    /**
     * 构建视频滤镜
     */
    private fun buildVideoFilter(params: VideoConvertParams): String {
        val filters = mutableListOf<String>()
        
        // 裁剪
        params.cropRegion?.let { crop ->
            filters.add("crop=${crop.width}:${crop.height}:${crop.x}:${crop.y}")
        }
        
        // 缩放
        filters.add("scale=${params.targetWidth}:${params.targetHeight}:force_original_aspect_ratio=decrease")
        filters.add("pad=${params.targetWidth}:${params.targetHeight}:(ow-iw)/2:(oh-ih)/2")
        
        // 帧率
        filters.add("fps=${params.fps}")
        
        return filters.joinToString(",")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        convertCallback = null
        Log.d(TAG, "视频转换服务资源已清理")
    }
}

