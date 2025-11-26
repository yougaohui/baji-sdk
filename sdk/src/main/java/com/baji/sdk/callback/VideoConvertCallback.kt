package com.baji.sdk.callback

/**
 * 视频转换回调
 */
interface VideoConvertCallback {
    /**
     * 转换开始
     */
    fun onConvertStart()
    
    /**
     * 转换进度
     * @param progress 进度百分比（0-100）
     */
    fun onConvertProgress(progress: Int)
    
    /**
     * 转换成功
     * @param outputPath 输出文件路径
     */
    fun onConvertSuccess(outputPath: String)
    
    /**
     * 转换失败
     * @param error 错误信息
     */
    fun onConvertFailed(error: String)
}

