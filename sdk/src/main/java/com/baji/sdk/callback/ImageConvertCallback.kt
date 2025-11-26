package com.baji.sdk.callback

/**
 * 图片转换回调
 */
interface ImageConvertCallback {
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

