package com.baji.sdk

/**
 * SDK配置类
 * 
 * @author Baji SDK Team
 * @since 1.0.0
 */
data class SDKConfig(
    /**
     * API基础URL
     */
    val apiBaseUrl: String = "https://tomato.gulaike.com",
    
    /**
     * Token配置
     */
    val token: String = "Bearer 6fcb7f58475b4e5aad8f0f1cadce235e",
    
    /**
     * 是否启用日志
     */
    val enableLog: Boolean = true,
    
    /**
     * 是否启用OTA功能
     */
    val enableOTA: Boolean = true,
    
    /**
     * FFmpeg配置
     */
    val ffmpegConfig: FFmpegConfig = FFmpegConfig(),
    
    /**
     * 图片转换配置
     */
    val imageConvertConfig: ImageConvertConfig = ImageConvertConfig()
) {
    /**
     * FFmpeg配置
     */
    data class FFmpegConfig(
        /**
         * 视频质量（1-31，数值越小质量越高）
         */
        val videoQuality: Int = 3,
        
        /**
         * 默认帧率
         */
        val defaultFps: Int = 5,
        
        /**
         * 默认音频采样率
         */
        val audioSampleRate: Int = 8000
    )
    
    /**
     * 图片转换配置
     */
    data class ImageConvertConfig(
        /**
         * 默认图片质量（0-100）
         */
        val defaultQuality: Int = 90,
        
        /**
         * 是否启用缓存
         */
        val enableCache: Boolean = true
    )
    
    /**
     * 构建器模式
     */
    class Builder {
        private var apiBaseUrl: String = "https://tomato.gulaike.com"
        private var token: String = "Bearer 6fcb7f58475b4e5aad8f0f1cadce235e"
        private var enableLog: Boolean = true
        private var enableOTA: Boolean = true
        private var ffmpegConfig: FFmpegConfig = FFmpegConfig()
        private var imageConvertConfig: ImageConvertConfig = ImageConvertConfig()
        
        fun setApiBaseUrl(url: String) = apply { this.apiBaseUrl = url }
        fun setToken(token: String) = apply { this.token = token }
        fun setEnableLog(enable: Boolean) = apply { this.enableLog = enable }
        fun setEnableOTA(enable: Boolean) = apply { this.enableOTA = enable }
        fun setFFmpegConfig(config: FFmpegConfig) = apply { this.ffmpegConfig = config }
        fun setImageConvertConfig(config: ImageConvertConfig) = apply { this.imageConvertConfig = config }
        
        fun build() = SDKConfig(
            apiBaseUrl = apiBaseUrl,
            token = token,
            enableLog = enableLog,
            enableOTA = enableOTA,
            ffmpegConfig = ffmpegConfig,
            imageConvertConfig = imageConvertConfig
        )
    }
}

