package com.baji.sdk.callback

/**
 * 通用错误回调
 */
interface ErrorCallback {
    /**
     * 错误发生
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    fun onError(errorCode: Int, errorMessage: String)
}

