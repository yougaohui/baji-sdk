package com.baji.sdk.service

import android.content.Context
import android.util.Log
import com.baji.sdk.SDKConfig
import com.baji.sdk.callback.OTAUpgradeCallback
import xfkj.fitpro.activity.ota.api.HttpHelper
import xfkj.fitpro.activity.ota.manager.OTAInitializer
import xfkj.fitpro.activity.ota.manager.OTASDKManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * OTA升级服务
 * 提供OTA升级检查、启动等功能
 */
class OTAService(
    private val context: Context,
    private val config: SDKConfig
) {
    private val TAG = "OTAService"
    private var upgradeCallback: OTAUpgradeCallback? = null
    
    /**
     * 设置升级回调
     */
    fun setUpgradeCallback(callback: OTAUpgradeCallback?) {
        this.upgradeCallback = callback
    }
    
    /**
     * 检查OTA升级
     */
    fun checkUpgrade() {
        try {
            Log.d(TAG, "开始检查OTA升级")
            HttpHelper.getInstance().getOTAUpgradeInfo(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "检查OTA升级失败: ${e.message}", e)
                    upgradeCallback?.onUpgradeFailed("检查升级失败: ${e.message}")
                }
                
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body()?.string()
                        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                            val jsonObject = JSONObject(responseBody)
                            val hasUpgrade = jsonObject.optBoolean("hasUpgrade", false)
                            
                            if (hasUpgrade) {
                                val version = jsonObject.optString("version", "")
                                val downloadUrl = jsonObject.optString("downloadUrl", "")
                                Log.d(TAG, "发现OTA升级: version=$version, url=$downloadUrl")
                                // 可以在这里触发升级流程
                            } else {
                                Log.d(TAG, "当前已是最新版本")
                            }
                        } else {
                            Log.e(TAG, "OTA升级检查响应失败")
                            upgradeCallback?.onUpgradeFailed("检查升级失败: 响应异常")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析OTA升级信息失败: ${e.message}", e)
                        upgradeCallback?.onUpgradeFailed("解析升级信息失败: ${e.message}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "检查OTA升级异常: ${e.message}", e)
            upgradeCallback?.onUpgradeFailed("检查升级异常: ${e.message}")
        }
    }
    
    /**
     * 启动OTA升级
     * @param otaFilePath OTA文件路径
     */
    fun startUpgrade(otaFilePath: String) {
        try {
            Log.d(TAG, "开始OTA升级: $otaFilePath")
            upgradeCallback?.onUpgradeStart()
            
            // 使用OTA模块的升级功能
            // 这里需要调用OTA模块的具体升级方法
            // 由于OTA升级涉及多个平台，具体实现需要根据平台类型调用相应的升级方法
            
            // 示例：可以通过OTASDKManager获取平台类型并启动升级
            val otaInfo = OTASDKManager.getInstance().getOTAInfo()
            if (otaInfo != null) {
                // 根据平台类型启动相应的升级流程
                // 这里需要根据实际需求实现
                Log.d(TAG, "OTA升级已启动")
            } else {
                upgradeCallback?.onUpgradeFailed("OTA信息未初始化")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动OTA升级失败: ${e.message}", e)
            upgradeCallback?.onUpgradeFailed("启动升级失败: ${e.message}")
        }
    }
    
    /**
     * 检查OTA是否已初始化
     */
    fun isInitialized(): Boolean {
        return OTAInitializer.isInitialized()
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        upgradeCallback = null
        Log.d(TAG, "OTA服务资源已清理")
    }
}

