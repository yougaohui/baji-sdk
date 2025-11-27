package com.baji.demo.ui

import android.content.Intent
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
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
import com.baji.sdk.BajiSDK
import com.baji.sdk.callback.FileTransferCallback
import com.baji.sdk.callback.VideoConvertCallback
import com.baji.sdk.model.FileInfo
import com.baji.sdk.model.VideoConvertParams
import java.io.File
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
    private lateinit var mCacheRootPath: String
    private lateinit var outDir: String
    
    // 视频原始尺寸
    private var mVideoOriginalWidth = 0
    private var mVideoOriginalHeight = 0
    
    // 裁剪参数
    private var mCropX = 0f
    private var mCropY = 0f
    private var mCropWidth = 0f
    private var mCropHeight = 0f
    
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
            val videoFile = File(videoPath)
            
            if (!videoFile.exists()) {
                Log.e(TAG, "视频文件不存在: $videoPath")
                Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // 设置视频路径
            val videoUri = videoInfo!!.uri
            if (videoUri != null && videoUri.toString().startsWith("content://")) {
                mVideoView.setVideoURI(videoUri)
            } else {
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
                
                // 初始化裁剪框
                mVideoView.post { initCropOverlay() }
                
                // 初始化时间范围
                initSeekBar()
                
                // 开始播放
                mVideoView.start()
                startTimer()
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
        
        val inputPath = videoInfo!!.path
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
            }
            
            override fun onConvertProgress(progress: Int) {
                Log.d(TAG, "视频转换进度: $progress%")
            }
            
            override fun onConvertSuccess(outputPath: String) {
                Log.d(TAG, "视频转换成功: $outputPath")
                loadingDialog?.dismiss()
                
                // 上传视频
                val fileService = BajiSDK.getInstance().getFileTransferService()
                fileService?.setTransferCallback(object : FileTransferCallback {
                    override fun onTransferStart() {
                        Log.d(TAG, "视频上传开始")
                        loadingDialog = AlertDialog.Builder(this@VideoCutActivity)
                            .setMessage("正在上传视频...")
                            .setCancelable(false)
                            .show()
                    }
                    
                    override fun onTransferProgress(progress: Int, bytesTransferred: Long, totalBytes: Long) {
                        Log.d(TAG, "视频上传进度: $progress%")
                    }
                    
                    override fun onTransferSuccess() {
                        Log.d(TAG, "视频上传成功")
                        loadingDialog?.dismiss()
                        Toast.makeText(this@VideoCutActivity, "视频上传成功", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    
                    override fun onTransferFailed(error: String) {
                        Log.e(TAG, "视频上传失败: $error")
                        loadingDialog?.dismiss()
                        Toast.makeText(this@VideoCutActivity, "视频上传失败: $error", Toast.LENGTH_SHORT).show()
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
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        mp?.release()
        mp = null
        loadingDialog?.dismiss()
    }
}

