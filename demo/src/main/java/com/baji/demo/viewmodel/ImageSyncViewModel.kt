package com.baji.demo.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baji.demo.utils.GlideEngine
import com.baji.sdk.BajiSDK
import com.baji.sdk.callback.FileTransferCallback
import com.baji.sdk.model.FileInfo
import com.baji.sdk.model.ImageConvertParams
import com.baji.sdk.service.ImageConvertService
import com.luck.picture.lib.PictureSelector
import com.luck.picture.lib.config.PictureConfig
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 图片同步ViewModel
 * 管理图片选择和上传逻辑
 */
class ImageSyncViewModel : ViewModel() {
    
    private val TAG = "ImageSyncViewModel"
    private var fileTransferCallback: FileTransferCallback? = null
    
    /**
     * 设置文件传输回调
     */
    fun setFileTransferCallback(callback: FileTransferCallback?) {
        this.fileTransferCallback = callback
    }
    
    /**
     * 根据表盘信息获取裁剪尺寸
     * 从表盘信息中获取实际的宽高值
     */
    private fun getCropSizeFromClockDialInfo(): Pair<Int, Int>? {
        return try {
            val clockDialInfoService = BajiSDK.getInstance().getClockDialInfoService()
            val clockDialInfo = clockDialInfoService.getCurrentClockDialInfo()
            
            if (clockDialInfo != null) {
                val width = clockDialInfo.width.toInt()
                val height = clockDialInfo.height.toInt()
                
                Log.d(TAG, "=== 图片裁剪尺寸设置 ===")
                Log.d(TAG, "从表盘信息获取尺寸: ${width}x${height}")
                Log.d(TAG, "屏幕类型: ${if (clockDialInfo.screenType == 0) "方屏" else "圆屏"}")
                
                if (width > 0 && height > 0) {
                    Pair(width, height)
                } else {
                    Log.w(TAG, "表盘信息尺寸无效: ${width}x${height}")
                    null
                }
            } else {
                Log.w(TAG, "表盘信息不存在")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取表盘信息失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 启动PictureSelector选择图片
     * 参考参考项目的实现
     */
    fun startPictureSelector(activity: Activity) {
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
                        Log.e(TAG, "表盘信息仍然不存在，无法选择图片")
                        fileTransferCallback?.onTransferFailed("表盘信息不存在，请确认设备已连接并重试")
                        return@launch
                    }
                    
                    Log.d(TAG, "表盘信息已获取成功")
                }
                
                // 获取裁剪尺寸
                val cropSize = getCropSizeFromClockDialInfo()
                if (cropSize == null) {
                    Log.e(TAG, "无法获取裁剪尺寸")
                    fileTransferCallback?.onTransferFailed("无法获取设备屏幕尺寸，请重新连接设备")
                    return@launch
                }
                
                Log.d(TAG, "启动PictureSelector选择图片，裁剪尺寸: ${cropSize.first}x${cropSize.second}")
                
                // 在UI线程中启动PictureSelector
                PictureSelector.create(activity)
                    .openGallery(PictureMimeType.ofImage()) // 打开相册选择图片
                    .imageEngine(GlideEngine.createGlideEngine()) // 使用Glide加载引擎
                    .isPreviewImage(true) // 可预览图片
                    .selectionMode(PictureConfig.SINGLE) // 单选模式
                    .isSingleDirectReturn(true) // 单选模式下直接返回
                    .isPreviewVideo(false) // 不预览视频
                    .isCamera(false) // 不显示拍照按钮
                    .isZoomAnim(true) // 图片列表点击缩放效果
                    .isEnableCrop(true) // 启用裁剪
                    .isCompress(true) // 启用压缩
                    .synOrAsy(false) // 同步压缩
                    .withAspectRatio(cropSize.first, cropSize.second) // 根据表盘信息设置裁剪比例
                    .freeStyleCropEnabled(true) // 允许自由裁剪
                    .showCropFrame(true) // 显示裁剪框
                    .showCropGrid(false) // 不显示网格
                    .cutOutQuality(90) // 裁剪输出质量
                    .minimumCompressSize(100) // 小于100kb不压缩
                    .cropImageWideHigh(cropSize.first, cropSize.second) // 设置具体的裁剪尺寸
                    .forResult(PictureConfig.CHOOSE_REQUEST) // 结果回调
            } catch (e: Exception) {
                Log.e(TAG, "启动图片选择器失败: ${e.message}", e)
                fileTransferCallback?.onTransferFailed("启动图片选择器失败: ${e.message}")
            }
        }
    }
    
    /**
     * 处理PictureSelector返回的结果
     */
    fun handlePictureSelectorResult(localMedia: LocalMedia, activity: Activity) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "PictureSelector 返回结果: ${localMedia.path}")
                
                // 优先使用裁剪后的图片路径，其次是压缩后的路径，最后是原始路径
                val imagePath = localMedia.cutPath ?: localMedia.compressPath ?: localMedia.path
                
                if (imagePath.isNullOrEmpty()) {
                    Log.w(TAG, "图片路径为空")
                    fileTransferCallback?.onTransferFailed("图片获取失败，请重试")
                    return@launch
                }
                
                val imageFile = File(imagePath)
                if (!imageFile.exists()) {
                    Log.e(TAG, "图片文件不存在: $imagePath")
                    fileTransferCallback?.onTransferFailed("图片文件不存在")
                    return@launch
                }
                
                Log.d(TAG, "使用图片路径: $imagePath")
                syncImage(imagePath, activity)
                
            } catch (e: Exception) {
                Log.e(TAG, "处理PictureSelector结果失败", e)
                fileTransferCallback?.onTransferFailed("图片处理失败: ${e.message}")
            }
        }
    }
    
    /**
     * 同步图片到设备
     */
    private fun syncImage(imagePath: String, activity: Activity) {
        try {
            Log.d(TAG, "开始同步图片: $imagePath")
            
            // 获取表盘信息以确定转换尺寸
            val cropSize = getCropSizeFromClockDialInfo()
            if (cropSize == null) {
                Log.e(TAG, "无法获取表盘信息尺寸")
                fileTransferCallback?.onTransferFailed("无法获取设备屏幕尺寸，请重新连接设备")
                return
            }
            
            fileTransferCallback?.onTransferStart()
            
            // 转换图片为设备格式（使用表盘信息的尺寸）
            val imageService = BajiSDK.getInstance().getImageConvertService()
            val outputDir = activity.getExternalFilesDir(null)
            val outputPath = "${outputDir?.absolutePath}/converted_image_${System.currentTimeMillis()}.bin"
            
            // 获取算法（如果有）
            val algorithm = try {
                val clockDialInfo = BajiSDK.getInstance().getClockDialInfoService().getCurrentClockDialInfo()
                clockDialInfo?.algorithm?.toInt() ?: 0
            } catch (e: Exception) {
                Log.w(TAG, "获取算法失败，使用默认值: ${e.message}")
                0
            }
            
            val params = ImageConvertParams(
                targetWidth = cropSize.first,
                targetHeight = cropSize.second,
                quality = 90,
                outputFormat = ImageConvertParams.ImageFormat.BIN,
                algorithm = algorithm
            )
            
            // 设置转换回调
            imageService.setConvertCallback(object : com.baji.sdk.callback.ImageConvertCallback {
                override fun onConvertSuccess(outputPath: String) {
                    Log.d(TAG, "图片转换成功: $outputPath")
                    // 上传转换后的图片
                    val fileService = BajiSDK.getInstance().getFileTransferService()
                    fileService.setTransferCallback(fileTransferCallback)
                    fileService.uploadFile(outputPath, FileInfo.FileType.IMAGE)
                }
                
                override fun onConvertFailed(error: String) {
                    Log.e(TAG, "图片转换失败: $error")
                    fileTransferCallback?.onTransferFailed("图片转换失败: $error")
                }
            })
            
            // 开始转换
            imageService.convertImage(imagePath, outputPath, params)
            
        } catch (e: Exception) {
            Log.e(TAG, "同步图片失败: ${e.message}", e)
            fileTransferCallback?.onTransferFailed("同步图片失败: ${e.message}")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        fileTransferCallback = null
        Log.d(TAG, "ViewModel已清理")
    }
}

