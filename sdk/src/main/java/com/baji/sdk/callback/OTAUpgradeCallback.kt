package com.baji.sdk.callback

/**
 * OTA升级回调
 */
interface OTAUpgradeCallback {
    /**
     * 开始升级
     */
    fun onUpgradeStart()
    
    /**
     * 升级进度
     * @param progress 进度百分比（0-100）
     */
    fun onUpgradeProgress(progress: Int)
    
    /**
     * 升级成功
     */
    fun onUpgradeSuccess()
    
    /**
     * 升级失败
     * @param error 错误信息
     */
    fun onUpgradeFailed(error: String)
}

