package com.baji.sdk.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.baji.sdk.SDKConfig
import com.baji.sdk.callback.ImageConvertCallback
import com.baji.sdk.model.ImageConvertParams
import com.jieli.bmp_convert.BmpConvert
import com.jieli.bmp_convert.ConvertResult
import com.jieli.bmp_convert.OnConvertListener
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

/**
 * 图片转换服务
 * 提供图片格式转换、缩放、裁剪等功能
 */
class ImageConvertService(
    private val context: Context,
    private val config: SDKConfig
) {
    private val TAG = "ImageConvertService"
    private var convertCallback: ImageConvertCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 设置转换回调
     */
    fun setConvertCallback(callback: ImageConvertCallback?) {
        this.convertCallback = callback
    }
    
    /**
     * 转换图片
     * @param inputPath 输入图片路径
     * @param outputPath 输出文件路径
     * @param params 转换参数
     */
    fun convertImage(
        inputPath: String,
        outputPath: String,
        params: ImageConvertParams
    ) {
        serviceScope.launch {
            try {
                Log.d(TAG, "开始转换图片: $inputPath -> $outputPath")
                
                // 加载原始图片
                val originalBitmap = loadBitmapFromPath(inputPath)
                if (originalBitmap == null) {
                    withContext(Dispatchers.Main) {
                        convertCallback?.onConvertFailed("无法加载原始图片: $inputPath")
                    }
                    return@launch
                }
                
                // 缩放图片
                val scaledBitmap = scaleBitmap(originalBitmap, params.targetWidth, params.targetHeight)
                if (scaledBitmap == null) {
                    withContext(Dispatchers.Main) {
                        convertCallback?.onConvertFailed("图片缩放失败")
                    }
                    return@launch
                }
                
                // 根据输出格式进行转换
                when (params.outputFormat) {
                    ImageConvertParams.ImageFormat.BIN -> {
                        // 使用杰理SDK转换为bin格式
                        convertToBin(scaledBitmap, outputPath, params)
                    }
                    ImageConvertParams.ImageFormat.PNG -> {
                        saveBitmap(scaledBitmap, outputPath, Bitmap.CompressFormat.PNG, params.quality)
                    }
                    ImageConvertParams.ImageFormat.JPEG -> {
                        saveBitmap(scaledBitmap, outputPath, Bitmap.CompressFormat.JPEG, params.quality)
                    }
                    ImageConvertParams.ImageFormat.BMP -> {
                        // BMP格式需要特殊处理
                        convertToBMP(scaledBitmap, outputPath)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "图片转换异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    convertCallback?.onConvertFailed("转换异常: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 加载Bitmap
     */
    private suspend fun loadBitmapFromPath(imagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return@withContext null
            }
            
            BitmapFactory.decodeFile(imagePath)
        } catch (e: Exception) {
            Log.e(TAG, "加载图片失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 缩放图片
     */
    private suspend fun scaleBitmap(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }
            scaledBitmap
        } catch (e: Exception) {
            Log.e(TAG, "缩放图片失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 转换为bin格式（使用杰理SDK）
     */
    private suspend fun convertToBin(
        bitmap: Bitmap,
        outputPath: String,
        params: ImageConvertParams
    ) = withContext(Dispatchers.IO) {
        try {
            val binFile = File(outputPath)
            
            // 保存bitmap为临时文件
            val tempBitmapFile = File(context.getExternalFilesDir(null), "temp_bitmap_${System.currentTimeMillis()}.png")
            val tempOutputStream = FileOutputStream(tempBitmapFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, tempOutputStream)
            tempOutputStream.close()
            
            // 使用杰理SDK的BmpConvert进行转换
            val convert = BmpConvert()
            val algorithm = if (params.algorithm == 3) {
                BmpConvert.TYPE_707N_ARGB
            } else {
                BmpConvert.TYPE_BR_28
            }
            
            // 使用协程等待异步转换完成
            val result = suspendCancellableCoroutine<String?> { continuation ->
                val conversionListener = object : OnConvertListener {
                    override fun onStart(path: String?) {
                        Log.d(TAG, "BmpConvert 开始转换: $path")
                    }
                    
                    override fun onStop(success: Boolean, message: String?) {
                        Log.d(TAG, "BmpConvert 转换完成: 成功=$success, 消息=$message")
                        if (success && binFile.exists() && binFile.length() > 0) {
                            Log.d(TAG, "bin文件转换成功: ${binFile.absolutePath}, 大小: ${binFile.length()} bytes")
                            continuation.resume(binFile.absolutePath) {}
                        } else {
                            continuation.resume(null) {}
                        }
                    }

                    override fun onStop(
                        p0: ConvertResult?,
                        p1: String?
                    ) {
                    }
                }
                
                convert.bitmapConvert(
                    algorithm,
                    tempBitmapFile.absolutePath,
                    binFile.absolutePath,
                    conversionListener
                )
            }
            
            // 删除临时文件
            tempBitmapFile.delete()
            
            if (result != null) {
                withContext(Dispatchers.Main) {
                    convertCallback?.onConvertSuccess(result)
                }
            } else {
                withContext(Dispatchers.Main) {
                    convertCallback?.onConvertFailed("转换为bin格式失败")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "转换为bin格式异常: ${e.message}", e)
            withContext(Dispatchers.Main) {
                convertCallback?.onConvertFailed("转换异常: ${e.message}")
            }
        }
    }
    
    /**
     * 保存Bitmap
     */
    private suspend fun saveBitmap(
        bitmap: Bitmap,
        outputPath: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(outputPath)
            val outputStream = FileOutputStream(outputFile)
            bitmap.compress(format, quality, outputStream)
            outputStream.close()
            
            withContext(Dispatchers.Main) {
                convertCallback?.onConvertSuccess(outputPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存图片失败: ${e.message}", e)
            withContext(Dispatchers.Main) {
                convertCallback?.onConvertFailed("保存失败: ${e.message}")
            }
        }
    }
    
    /**
     * 转换为BMP格式
     */
    private suspend fun convertToBMP(bitmap: Bitmap, outputPath: String) {
        // BMP格式转换需要特殊处理，这里简化处理
        saveBitmap(bitmap, outputPath, Bitmap.CompressFormat.PNG, 100)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        serviceScope.cancel()
        convertCallback = null
        Log.d(TAG, "图片转换服务资源已清理")
    }
}

