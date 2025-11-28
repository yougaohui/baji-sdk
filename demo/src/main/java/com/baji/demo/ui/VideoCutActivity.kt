package com.baji.demo.ui

import android.content.Intent
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baji.demo.R
import com.baji.demo.adapter.FramesAdapter
import com.baji.demo.model.VideoInfo
import com.baji.demo.utils.TimerTaskImp
import com.baji.demo.utils.VideoUtils
import com.baji.demo.view.RangeSeekBarView
import com.baji.demo.view.VideoCropOverlayView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.baji.sdk.BajiSDK
import com.baji.sdk.callback.FileTransferCallback
import com.baji.sdk.callback.VideoConvertCallback
import com.baji.sdk.model.FileInfo
import com.baji.sdk.model.VideoConvertParams
import java.io.File
import java.util.ArrayList
import java.util.Timer

/**
 * 视频裁剪Activity
 * 简化版本：固定5秒视频，使用VideoConvertService进行转换
 */
class VideoCutActivity : AppCompatActivity() {
    
    companion object {
        const val PATH = "path"
        const val TAG = "VideoCutActivity"
        const val MAX_TIME = 5 // 最大5秒
    }
    
    private lateinit var mVideoView: VideoView
    private lateinit var mRangeSeekBarView: RangeSeekBarView
    private lateinit var mTvOk: TextView
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mCropOverlayView: VideoCropOverlayView
    
    private var videoInfo: VideoInfo? = null
    private var mp: MediaPlayer? = null
    private var mAdapter: FramesAdapter? = null
    private var mMinTime = 0L // 默认从0s开始
    private var mMaxTime = MAX_TIME * 1000L // 默认到5s结束
    private var timer: Timer? = null
    private var timerTaskImp: TimerTaskImp? = null
    private var loadingDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressPercentText: TextView? = null
    private lateinit var mCacheRootPath: String
    private lateinit var outDir: String
    
    // 视频帧相关
    private var mFrames = 0 // 视频帧数（秒数）
    private val frameList = ArrayList<String>() // 存储每一帧的路径
    
    // 视频原始尺寸
    private var mVideoOriginalWidth = 0
    private var mVideoOriginalHeight = 0
    
    // 裁剪参数
    private var mCropX = 0f
    private var mCropY = 0f
    private var mCropWidth = 0f
    private var mCropHeight = 0f
    
    // 用于帧提取的视频路径（如果是Content URI，需要先复制到临时文件）
    private var frameExtractionVideoPath: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_cut)
        
        mVideoView = findViewById(R.id.mVideoView)
        mRangeSeekBarView = findViewById(R.id.mRangeSeekBarView)
        mTvOk = findViewById(R.id.mTvOk)
        mRecyclerView = findViewById(R.id.mRecyclerView)
        mCropOverlayView = findViewById(R.id.mCropOverlayView)
        
        videoInfo = intent.getParcelableExtra(PATH)
        mCacheRootPath = getCacheRootPath()
        outDir = mCacheRootPath + VideoUtils.getFileName(videoInfo?.name)
        
        mAdapter = FramesAdapter()
        mRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRecyclerView.adapter = mAdapter
        
        // 根据RangeSeekBarView的宽度计算每一帧的宽度
        mRangeSeekBarView.post {
            val width = mRangeSeekBarView.width / MAX_TIME
            mAdapter?.setItemWidth(width) // 根据seekbar的长度除以最大帧数，就是我们每一帧需要的宽度
            Log.d(TAG, "设置帧宽度: $width (RangeSeekBarView宽度: ${mRangeSeekBarView.width}, MAX_TIME: $MAX_TIME)")
        }
        
        mTvOk.setOnClickListener {
            trimVideo()
        }
        
        initVideo()
    }
    
    private fun getCacheRootPath(): String {
        val base = getExternalFilesDir(null) ?: cacheDir
        return "${base.absolutePath}${File.separator}videoCut${File.separator}".apply {
            File(this).mkdirs()
        }
    }
    
    private fun initVideo() {
        if (videoInfo == null) {
            Toast.makeText(this, "视频资源无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            val videoPath = videoInfo!!.path
            val videoUri = videoInfo!!.uri
            
            Log.d(TAG, "=== 视频信息 ===")
            Log.d(TAG, "视频路径: $videoPath")
            Log.d(TAG, "视频URI: $videoUri")
            
            // 检查是否是 Content URI - 优先检查videoPath，因为可能是Content URI字符串
            val isContentUri = (videoPath.isNotEmpty() && videoPath.startsWith("content://")) || 
                              (videoUri != null && videoUri.toString().startsWith("content://"))
            
            Log.d(TAG, "是否为Content URI: $isContentUri")
            
            if (!isContentUri && videoPath.isNotEmpty()) {
                // 只有非 Content URI 才检查文件是否存在
                try {
                    val videoFile = File(videoPath)
                    if (!videoFile.exists()) {
                        Log.e(TAG, "视频文件不存在: $videoPath")
                        Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                    Log.d(TAG, "视频文件存在: $videoPath, 大小: ${videoFile.length()} bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "检查视频文件失败: ${e.message}", e)
                    Toast.makeText(this, "无法访问视频文件", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            } else if (isContentUri) {
                Log.d(TAG, "跳过文件存在检查（Content URI）")
            } else {
                Log.e(TAG, "视频路径为空")
                Toast.makeText(this, "视频路径无效", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // 设置视频路径
            if (isContentUri && videoUri != null) {
                Log.d(TAG, "使用 Content URI 播放视频: $videoUri")
                mVideoView.setVideoURI(videoUri)
            } else {
                Log.d(TAG, "使用文件路径播放视频: $videoPath")
                mVideoView.setVideoPath(videoPath)
            }
            
            mVideoView.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "视频播放错误: what=$what, extra=$extra")
                Toast.makeText(this, "视频播放失败", Toast.LENGTH_SHORT).show()
                finish()
                true
            }
            
            mVideoView.setOnPreparedListener { mediaPlayer ->
                mp = mediaPlayer
                mVideoOriginalWidth = mediaPlayer.videoWidth
                mVideoOriginalHeight = mediaPlayer.videoHeight
                
                Log.d(TAG, "视频准备完成，尺寸: ${mVideoOriginalWidth}x${mVideoOriginalHeight}")
                
                // 准备帧提取的视频路径（如果是Content URI，需要先复制）
                prepareVideoForFrameExtraction { success ->
                    if (success) {
                        // 初始化裁剪框
                        mVideoView.post { initCropOverlay() }
                        
                        // 初始化时间范围
                        initSeekBar()
                        
                        // 开始提取视频帧
                        analysisVideo()
                        
                        // 开始播放
                        mVideoView.start()
                        startTimer()
                    } else {
                        Log.e(TAG, "准备视频文件失败，无法提取帧")
                        Toast.makeText(this, "无法提取视频帧", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            mVideoView.requestFocus()
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化视频播放失败", e)
            Toast.makeText(this, "视频初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun initCropOverlay() {
        if (mVideoOriginalWidth <= 0 || mVideoOriginalHeight <= 0) {
            return
        }
        
        val location = IntArray(2)
        mVideoView.getLocationOnScreen(location)
        val videoViewLeft = location[0]
        val videoViewTop = location[1]
        val videoViewWidth = mVideoView.width
        val videoViewHeight = mVideoView.height
        
        val cropLocation = IntArray(2)
        mCropOverlayView.getLocationOnScreen(cropLocation)
        
        val videoLeft = videoViewLeft - cropLocation[0].toFloat()
        val videoTop = videoViewTop - cropLocation[1].toFloat()
        val videoRight = videoLeft + videoViewWidth
        val videoBottom = videoTop + videoViewHeight
        
        mCropOverlayView.setVideoBounds(videoLeft, videoTop, videoRight, videoBottom)
        
        // 获取目标宽高比
        val clockDialInfo = BajiSDK.getInstance().getClockDialInfoService().getCurrentClockDialInfo()
        val aspectRatio = if (clockDialInfo != null) {
            clockDialInfo.width.toFloat() / clockDialInfo.height.toFloat()
        } else {
            1.0f
        }
        
        mCropOverlayView.setAspectRatio(aspectRatio)
        
        mCropOverlayView.setOnCropChangeListener(object : VideoCropOverlayView.OnCropChangeListener {
            override fun onCropChanged(
                cropRect: RectF,
                cropX: Float,
                cropY: Float,
                cropWidth: Float,
                cropHeight: Float
            ) {
                val scaleX = mVideoOriginalWidth.toFloat() / videoViewWidth
                val scaleY = mVideoOriginalHeight.toFloat() / videoViewHeight
                
                this@VideoCutActivity.mCropX = cropX * scaleX
                this@VideoCutActivity.mCropY = cropY * scaleY
                this@VideoCutActivity.mCropWidth = cropWidth * scaleX
                this@VideoCutActivity.mCropHeight = cropHeight * scaleY
            }
        })
    }
    
    private fun initSeekBar() {
        mRangeSeekBarView.setSelectedMinValue(mMinTime)
        mRangeSeekBarView.setSelectedMaxValue(mMaxTime)
        mRangeSeekBarView.setStartEndTime(mMinTime, mMaxTime)
        mRangeSeekBarView.setNotifyWhileDragging(true)
        mRangeSeekBarView.setOnRangeSeekBarChangeListener(object : RangeSeekBarView.OnRangeSeekBarChangeListener {
            override fun onRangeSeekBarValuesChanged(
                bar: RangeSeekBarView,
                minValue: Long,
                maxValue: Long,
                action: Int,
                isMin: Boolean,
                pressedThumb: RangeSeekBarView.Thumb
            ) {
                Log.d(TAG, "范围改变: mMinTime = $minValue, mMaxTime = $maxValue")
                mMinTime = minValue
                mMaxTime = maxValue
                mRangeSeekBarView.setStartEndTime(mMinTime, mMaxTime)
                reStartVideo()
            }
        })
    }
    
    private fun startTimer() {
        if (timer == null) {
            timer = Timer()
            timerTaskImp = TimerTaskImp(this)
            timer!!.schedule(timerTaskImp, 0, 100)
        }
    }
    
    fun getVideoProgress() {
        try {
            val currentPosition = mVideoView.currentPosition
            if (currentPosition >= mMaxTime) {
                reStartVideo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取视频进度失败", e)
        }
    }
    
    private fun reStartVideo() {
        try {
            if (mp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp!!.seekTo(mMinTime, MediaPlayer.SEEK_CLOSEST)
            } else {
                mVideoView.seekTo(mMinTime.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "重新播放视频失败", e)
        }
    }
    
    /**
     * 准备视频文件用于帧提取（如果是Content URI，需要先复制到临时文件）
     */
    private fun prepareVideoForFrameExtraction(callback: (Boolean) -> Unit) {
        val videoPath = videoInfo?.path ?: ""
        val videoUri = videoInfo?.uri
        val isContentUri = (videoPath.isNotEmpty() && videoPath.startsWith("content://")) || 
                          (videoUri != null && videoUri.toString().startsWith("content://"))
        
        if (isContentUri && videoUri != null) {
            // Content URI，需要复制到临时文件
            Log.d(TAG, "检测到Content URI，复制视频到临时文件用于帧提取...")
            val tempPath = copyContentUriToTempFile(videoUri)
            if (tempPath != null) {
                frameExtractionVideoPath = tempPath
                Log.d(TAG, "视频已复制到临时文件: $tempPath")
                callback(true)
            } else {
                Log.e(TAG, "复制Content URI失败")
                callback(false)
            }
        } else {
            // 普通文件路径
            frameExtractionVideoPath = videoPath
            callback(true)
        }
    }
    
    /**
     * 分析视频并开始提取帧
     */
    private fun analysisVideo() {
        try {
            if (mp == null) {
                Log.e(TAG, "MediaPlayer为空，无法分析视频")
                return
            }
            
            val duration = mp!!.duration
            mFrames = duration / 1000 // 转换为秒数
            if (mFrames > MAX_TIME) {
                mFrames = MAX_TIME // 最多提取5秒的帧
            }
            
            Log.d(TAG, "视频总时长: ${duration}ms, 帧数: $mFrames")
            
            // 如果帧数小于1，不进行提取
            if (mFrames < 1) {
                Log.w(TAG, "视频时长太短，无法提取帧")
                return
            }
            
            // 确保输出目录存在
            val dir = File(outDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            // 开始提取第一帧
            gotoGetFrameAtTime(0)
        } catch (e: Exception) {
            Log.e(TAG, "分析视频失败: ${e.message}", e)
        }
    }
    
    /**
     * 提取指定时间的视频帧
     */
    private fun gotoGetFrameAtTime(time: Int) {
        if (time >= mFrames) {
            Log.d(TAG, "所有帧提取完成，共 $mFrames 帧")
            return
        }
        
        val inputPath = frameExtractionVideoPath
        if (inputPath == null || inputPath.isEmpty()) {
            Log.e(TAG, "视频路径为空，无法提取帧")
            return
        }
        
        // 检查是否是Content URI（这种情况不应该发生，但为了安全）
        if (inputPath.startsWith("content://")) {
            Log.e(TAG, "FFmpeg 无法处理 Content URI: $inputPath")
            return
        }
        
        val outfile = "$outDir${File.separator}$time.jpg"
        
        // 获取表盘信息并计算帧尺寸
        val frameSize = getFrameSizeFromClockDialInfo()
        
        // 构建FFmpeg命令字符串
        val command = "-y -ss $time -i \"$inputPath\" -frames:v 1 -f image2 -s $frameSize \"$outfile\""
        val nextTime = time + 1
        
        Log.d(TAG, "提取第 $time 秒的帧，命令: $command")
        
        // 检查Activity是否还在运行
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity已销毁，取消获取帧")
            return
        }
        
        // 使用FFmpegKit执行命令
        FFmpegKit.executeAsync(command) { session ->
            runOnUiThread {
                try {
                    if (isFinishing || isDestroyed) {
                        Log.w(TAG, "Activity已销毁，取消获取帧回调")
                        return@runOnUiThread
                    }
                    
                    // 检查执行结果
                    val returnCode = session.returnCode
                    if (ReturnCode.isSuccess(returnCode)) {
                        Log.d(TAG, "完成提取第 $time 秒的帧")
                        if (time == 0) {
                            // 第一帧，初始化列表
                            frameList.clear()
                            for (x in 0 until mFrames) {
                                frameList.add(outfile)
                            }
                            mAdapter?.updateList(frameList)
                        } else {
                            // 更新指定位置的帧
                            if (time < frameList.size) {
                                frameList[time] = outfile
                                mAdapter?.updateItem(time, outfile)
                            }
                        }
                        // 继续提取下一帧
                        gotoGetFrameAtTime(nextTime)
                    } else {
                        val output = session.output
                        Log.e(TAG, "提取第 $time 秒的帧错误: $output")
                        // 继续处理下一帧
                        gotoGetFrameAtTime(nextTime)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取帧完成回调中出错", e)
                    // 继续处理下一帧
                    gotoGetFrameAtTime(nextTime)
                }
            }
        }
    }
    
    /**
     * 从表盘信息获取帧尺寸
     */
    private fun getFrameSizeFromClockDialInfo(): String {
        return try {
            val clockDialInfo = BajiSDK.getInstance().getClockDialInfoService().getCurrentClockDialInfo()
            if (clockDialInfo != null) {
                val width = clockDialInfo.width.toInt()
                val height = clockDialInfo.height.toInt()
                val frameSize = "${width}x${height}"
                
                Log.d(TAG, "=== 帧尺寸设置 ===")
                Log.d(TAG, "从表盘信息获取帧尺寸: $frameSize")
                Log.d(TAG, "设备屏幕尺寸: ${width}x${height}")
                
                frameSize
            } else {
                Log.w(TAG, "表盘信息不存在，使用默认帧尺寸")
                val defaultSize = "320x384"
                Log.d(TAG, "默认帧尺寸: $defaultSize")
                defaultSize
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取表盘信息失败，使用默认帧尺寸", e)
            val defaultSize = "320x384"
            defaultSize
        }
    }
    
    private fun trimVideo() {
        if (videoInfo == null) {
            return
        }
        
        // 显示加载对话框
        loadingDialog = AlertDialog.Builder(this)
            .setMessage("正在转换视频...")
            .setCancelable(false)
            .show()
        
        val clockDialInfo = BajiSDK.getInstance().getClockDialInfoService().getCurrentClockDialInfo()
        if (clockDialInfo == null) {
            loadingDialog?.dismiss()
            Toast.makeText(this, "表盘信息不存在，请重新连接设备", Toast.LENGTH_SHORT).show()
            return
        }
        
        val targetWidth = clockDialInfo.width.toInt()
        val targetHeight = clockDialInfo.height.toInt()
        
        val videoPath = videoInfo!!.path
        val videoUri = videoInfo!!.uri
        val isContentUri = (videoPath.isNotEmpty() && videoPath.startsWith("content://")) || 
                          (videoUri != null && videoUri.toString().startsWith("content://"))
        
        Log.d(TAG, "=== 视频转换准备 ===")
        Log.d(TAG, "视频路径: $videoPath")
        Log.d(TAG, "视频URI: $videoUri")
        Log.d(TAG, "是否为Content URI: $isContentUri")
        
        // 如果是 Content URI，需要先复制到临时文件（FFmpeg需要实际的文件路径）
        val inputPath = if (isContentUri && videoUri != null) {
            Log.d(TAG, "检测到Content URI，开始复制到临时文件...")
            loadingDialog?.setMessage("正在复制视频文件...")
            // 复制 Content URI 到临时文件
            copyContentUriToTempFile(videoUri)
        } else {
            Log.d(TAG, "使用文件路径: $videoPath")
            videoPath
        }
        
        if (inputPath == null || inputPath.isEmpty()) {
            loadingDialog?.dismiss()
            val errorMsg = if (isContentUri) {
                "无法复制视频文件，请检查文件权限"
            } else {
                "无法访问视频文件"
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "输入路径无效: $inputPath")
            return
        }
        
        Log.d(TAG, "最终使用的输入路径: $inputPath")
        
        val outputPath = "$mCacheRootPath${VideoUtils.getFileName(videoInfo!!.name)}_trim.avi"
        
        // 创建裁剪区域（如果有）
        val cropRegion = if (mCropWidth > 0 && mCropHeight > 0) {
            VideoConvertParams.CropRegion(
                x = mCropX.toInt(),
                y = mCropY.toInt(),
                width = mCropWidth.toInt(),
                height = mCropHeight.toInt()
            )
        } else {
            null
        }
        
        val params = VideoConvertParams(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            fps = 5,
            quality = 10,
            startTime = mMinTime / 1000f,
            duration = (mMaxTime - mMinTime) / 1000f,
            cropRegion = cropRegion
        )
        
        val videoService = BajiSDK.getInstance().getVideoConvertService()
        videoService?.setConvertCallback(object : VideoConvertCallback {
            override fun onConvertStart() {
                Log.d(TAG, "视频转换开始")
                runOnUiThread {
                    loadingDialog?.setMessage("正在转换视频: 0%")
                }
            }
            
            override fun onConvertProgress(progress: Int) {
                Log.d(TAG, "视频转换进度: $progress%")
                runOnUiThread {
                    loadingDialog?.setMessage("正在转换视频: $progress%")
                }
            }
            
            override fun onConvertSuccess(outputPath: String) {
                Log.d(TAG, "视频转换成功: $outputPath")
                runOnUiThread {
                    loadingDialog?.dismiss()
                }
                
                // 上传视频
                val fileService = BajiSDK.getInstance().getFileTransferService()
                fileService?.setTransferCallback(object : FileTransferCallback {
                    override fun onTransferStart() {
                        Log.d(TAG, "视频上传开始")
                        runOnUiThread {
                            showProgressDialog()
                        }
                    }
                    
                    override fun onTransferProgress(progress: Int, bytesTransferred: Long, totalBytes: Long) {
                        Log.d(TAG, "视频上传进度: $progress% ($bytesTransferred/$totalBytes bytes)")
                        runOnUiThread {
                            updateProgress(progress)
                        }
                    }
                    
                    override fun onTransferSuccess() {
                        Log.d(TAG, "视频上传成功")
                        runOnUiThread {
                            dismissProgressDialog()
                            Toast.makeText(this@VideoCutActivity, "视频上传成功", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    
                    override fun onTransferFailed(error: String) {
                        Log.e(TAG, "视频上传失败: $error")
                        runOnUiThread {
                            dismissProgressDialog()
                            Toast.makeText(this@VideoCutActivity, "视频上传失败: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                
                fileService?.uploadFile(outputPath, FileInfo.FileType.VIDEO)
            }
            
            override fun onConvertFailed(error: String) {
                Log.e(TAG, "视频转换失败: $error")
                loadingDialog?.dismiss()
                Toast.makeText(this@VideoCutActivity, "视频转换失败: $error", Toast.LENGTH_SHORT).show()
            }
        })
        
        videoService?.convertToAVI(inputPath, outputPath, params)
    }
    
    /**
     * 将 Content URI 复制到临时文件
     * @param uri Content URI
     * @return 临时文件路径，如果失败返回 null
     */
    private fun copyContentUriToTempFile(uri: android.net.Uri): String? {
        return try {
            Log.d(TAG, "开始复制 Content URI 到临时文件: $uri")
            
            val tempFile = File(mCacheRootPath, "temp_video_${System.currentTimeMillis()}.mp4")
            tempFile.parentFile?.mkdirs()
            
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    Log.d(TAG, "Content URI 复制完成，共 ${totalBytes} bytes")
                }
            } ?: run {
                Log.e(TAG, "无法打开 Content URI 输入流")
                return null
            }
            
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "临时文件创建失败或为空")
                return null
            }
            
            Log.d(TAG, "Content URI 已复制到临时文件: ${tempFile.absolutePath}, 大小: ${tempFile.length()} bytes")
            tempFile.absolutePath
        } catch (e: SecurityException) {
            Log.e(TAG, "复制 Content URI 失败（权限问题）: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "复制 Content URI 到临时文件失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 显示进度对话框
     */
    private fun showProgressDialog() {
        try {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
            progressBar = view.findViewById(R.id.progressBar)
            progressPercentText = view.findViewById(R.id.progressPercent)
            val progressMessage = view.findViewById<TextView>(R.id.progressMessage)
            
            progressMessage?.text = "正在上传视频..."
            progressBar?.progress = 0
            progressPercentText?.text = "0%"
            
            progressDialog = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create()
            
            progressDialog?.show()
        } catch (e: Exception) {
            Log.e(TAG, "显示进度对话框失败: ${e.message}", e)
            // 如果创建进度对话框失败，使用简单的加载对话框
            loadingDialog = AlertDialog.Builder(this)
                .setMessage("正在上传视频...")
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * 更新进度
     */
    private fun updateProgress(progress: Int) {
        try {
            progressBar?.progress = progress
            progressPercentText?.text = "$progress%"
        } catch (e: Exception) {
            Log.e(TAG, "更新进度失败: ${e.message}", e)
        }
    }
    
    /**
     * 关闭进度对话框
     */
    private fun dismissProgressDialog() {
        try {
            progressDialog?.dismiss()
            progressDialog = null
            progressBar = null
            progressPercentText = null
        } catch (e: Exception) {
            Log.e(TAG, "关闭进度对话框失败: ${e.message}", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        mp?.release()
        mp = null
        loadingDialog?.dismiss()
        dismissProgressDialog()
        
        // 清理临时文件
        try {
            val tempDir = File(mCacheRootPath)
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_video_")) {
                        file.delete()
                        Log.d(TAG, "已删除临时文件: ${file.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理临时文件失败: ${e.message}", e)
        }
    }
}

