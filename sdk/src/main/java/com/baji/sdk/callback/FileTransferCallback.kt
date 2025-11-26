package com.baji.sdk.callback

/**
 * 文件传输回调
 */
interface FileTransferCallback {
    /**
     * 传输开始
     */
    fun onTransferStart()
    
    /**
     * 传输进度
     * @param progress 进度百分比（0-100）
     * @param bytesTransferred 已传输字节数
     * @param totalBytes 总字节数
     */
    fun onTransferProgress(progress: Int, bytesTransferred: Long, totalBytes: Long)
    
    /**
     * 传输成功
     */
    fun onTransferSuccess()
    
    /**
     * 传输失败
     * @param error 错误信息
     */
    fun onTransferFailed(error: String)
}

