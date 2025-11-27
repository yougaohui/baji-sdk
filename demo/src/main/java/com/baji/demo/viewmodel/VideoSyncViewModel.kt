package com.baji.demo.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baji.demo.utils.GlideEngine
import com.baji.sdk.BajiSDK
import com.luck.picture.lib.PictureSelector
import com.luck.picture.lib.config.PictureConfig
import com.luck.picture.lib.config.PictureMimeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 视频同步ViewModel
 * 管理视频选择和上传逻辑
 */
class VideoSyncViewModel : ViewModel() {

    private val TAG = "VideoSyncViewModel"

    /**
     * 启动PictureSelector选择视频
     * 参考图片选择的实现
     */
    fun startPictureSelectorForVideo(activity: Activity) {
        viewModelScope.launch {
            try {
                // 先检查表盘信息是否存在
                val clockDialInfoService = BajiSDK.getInstance().getClockDialInfoService()

                if (!clockDialInfoService.hasClockDialInfo()) {
                    Log.w(TAG, "表盘信息不存在，尝试重新请求...")

                    // 请求表盘信息
                    clockDialInfoService.requestClockDialInfo()

                    // 等待5秒，让设备有时间响应
                    Log.d(TAG, "等待5秒，让设备响应表盘信息请求...")
                    delay(5000)

                    // 再次检查
                    if (!clockDialInfoService.hasClockDialInfo()) {
                        Log.e(TAG, "表盘信息仍然不存在，无法选择视频")
                        return@launch
                    }

                    Log.d(TAG, "表盘信息已获取成功")
                }

                Log.d(TAG, "启动PictureSelector选择视频")

                // 在UI线程中启动PictureSelector
                PictureSelector.create(activity)
                    .openGallery(PictureMimeType.ofVideo()) // 打开相册选择视频
                    .imageEngine(GlideEngine.createGlideEngine()) // 使用Glide加载引擎
                    .isPreviewVideo(true) // 可预览视频
                    .selectionMode(PictureConfig.SINGLE) // 单选模式
                    .isSingleDirectReturn(true) // 单选模式下直接返回
                    .isCamera(false) // 不显示拍摄按钮
                    .isZoomAnim(true) // 视频列表点击缩放效果
                    .forResult(PictureConfig.CHOOSE_REQUEST) // 结果回调
            } catch (e: Exception) {
                Log.e(TAG, "启动视频选择器失败: ${e.message}", e)
            }
        }
    }
}

