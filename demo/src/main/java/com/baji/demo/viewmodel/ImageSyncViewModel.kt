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
     * 启动PictureSelector选择图片
     * 参考参考项目的实现
     */
    fun startPictureSelector(activity: Activity) {
        try {
            Log.d(TAG, "启动PictureSelector选择图片")
            PictureSelector.create(activity)
                .openGallery(PictureMimeType.ofImage()) // 打开相册选择图片
                .imageEngine(GlideEngine.createGlideEngine()) // 使用Glide加载引擎
                .isPreviewImage(true) // 可预览图片
                .selectionMode(PictureConfig.SINGLE) // 单选模式
                .isSingleDirectReturn(true) // 单选模式下直接返回
                .isPreviewVideo(false) // 不预览视频
                .isCamera(false) // 不显示拍照按钮
                .isZoomAnim(true) // 图片列表点击缩放效果
                .isEnableCrop(false) // 不启用裁剪（根据需求可以改为true）
                .isCompress(true) // 启用压缩
                .minimumCompressSize(100) // 小于100kb不压缩
                .forResult(PictureConfig.CHOOSE_REQUEST) // 结果回调
        } catch (e: Exception) {
            Log.e(TAG, "启动图片选择器失败: ${e.message}", e)
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
            fileTransferCallback?.onTransferStart()
            
            // 转换图片为设备格式（240x240, bin格式）
            val imageService = BajiSDK.getInstance().getImageConvertService()
            val outputDir = activity.getExternalFilesDir(null)
            val outputPath = "${outputDir?.absolutePath}/converted_image_${System.currentTimeMillis()}.bin"
            
            val params = ImageConvertParams(
                targetWidth = 240,
                targetHeight = 240,
                quality = 90,
                outputFormat = ImageConvertParams.ImageFormat.BIN,
                algorithm = 0
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

