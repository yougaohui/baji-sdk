package com.baji.demo.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.Nullable
import com.baji.demo.utils.VideoUtils
import kotlin.math.pow

/**
 * 视频裁剪覆盖层视图
 * 支持可拖拽、可缩放的裁剪框，保持目标宽高比
 */
class VideoCropOverlayView @JvmOverloads constructor(
    context: Context,
    @Nullable attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 裁剪框
    private val mCropRect = RectF()
    
    // 画笔
    private lateinit var mBorderPaint: Paint
    private lateinit var mOverlayPaint: Paint
    private lateinit var mCornerPaint: Paint
    
    // 触摸相关
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mIsDragging = false
    private var mIsScaling = false
    private var mActivePointerId = -1
    private var mSecondPointerId = -1
    private var mInitialDistance = 0f
    
    // 裁剪框边界
    private val mVideoBounds = RectF()
    
    // 目标宽高比
    private var mAspectRatio = 1.0f
    
    // 回调接口
    private var mCropChangeListener: OnCropChangeListener? = null
    
    // 裁剪框最小尺寸（像素）
    private var mMinCropSize = VideoUtils.dp2px(context, 50f).toFloat()
    
    // 边角大小
    private var mCornerSize = VideoUtils.dp2px(context, 20f).toFloat()
    
    interface OnCropChangeListener {
        /**
         * 裁剪框变化回调
         */
        fun onCropChanged(cropRect: RectF, cropX: Float, cropY: Float, cropWidth: Float, cropHeight: Float)
    }
    
    init {
        init()
    }
    
    private fun init() {
        // 初始化边框画笔
        mBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = VideoUtils.dp2px(context, 2f).toFloat()
            color = 0xFFFFFFFF.toInt() // 白色边框
        }
        
        // 初始化遮罩画笔
        mOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x80000000.toInt() // 半透明黑色遮罩
        }
        
        // 初始化边角画笔
        mCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = VideoUtils.dp2px(context, 3f).toFloat()
            color = 0xFFFFFFFF.toInt() // 白色边角
        }
        
        setWillNotDraw(false)
    }
    
    /**
     * 设置视频边界（视频预览在屏幕上的位置和大小）
     */
    fun setVideoBounds(left: Float, top: Float, right: Float, bottom: Float) {
        mVideoBounds.set(left, top, right, bottom)
        initializeCropRect()
        invalidate()
    }
    
    /**
     * 设置目标宽高比
     */
    fun setAspectRatio(aspectRatio: Float) {
        if (aspectRatio > 0) {
            mAspectRatio = aspectRatio
            adjustCropRectToAspectRatio()
            invalidate()
        }
    }
    
    /**
     * 初始化裁剪框
     */
    private fun initializeCropRect() {
        val videoWidth = mVideoBounds.width()
        val videoHeight = mVideoBounds.height()
        
        if (videoWidth <= 0 || videoHeight <= 0) {
            return
        }
        
        // 计算初始裁剪框大小（尽可能铺满，保持宽高比）
        var cropWidth: Float
        var cropHeight: Float
        val videoAspectRatio = videoWidth / videoHeight
        
        if (videoAspectRatio > mAspectRatio) {
            // 视频更宽，以高度为准，铺满高度
            cropHeight = videoHeight
            cropWidth = cropHeight * mAspectRatio
        } else {
            // 视频更高，以宽度为准，铺满宽度
            cropWidth = videoWidth
            cropHeight = cropWidth / mAspectRatio
        }
        
        // 确保不超过视频边界
        if (cropWidth > videoWidth) {
            cropWidth = videoWidth
            cropHeight = cropWidth / mAspectRatio
        }
        if (cropHeight > videoHeight) {
            cropHeight = videoHeight
            cropWidth = cropHeight * mAspectRatio
        }
        
        // 确保不小于最小尺寸
        if (cropWidth < mMinCropSize) {
            cropWidth = mMinCropSize
            cropHeight = cropWidth / mAspectRatio
        }
        if (cropHeight < mMinCropSize) {
            cropHeight = mMinCropSize
            cropWidth = cropHeight * mAspectRatio
        }
        
        // 居中放置
        val left = mVideoBounds.left + (videoWidth - cropWidth) / 2
        val top = mVideoBounds.top + (videoHeight - cropHeight) / 2
        
        mCropRect.set(left, top, left + cropWidth, top + cropHeight)
        
        notifyCropChanged()
    }
    
    /**
     * 调整裁剪框以保持宽高比
     */
    private fun adjustCropRectToAspectRatio() {
        val currentWidth = mCropRect.width()
        val currentHeight = mCropRect.height()
        val currentAspectRatio = currentWidth / currentHeight
        
        var newWidth: Float
        var newHeight: Float
        val centerX = mCropRect.centerX()
        val centerY = mCropRect.centerY()
        
        if (currentAspectRatio > mAspectRatio) {
            // 当前更宽，以高度为准
            newHeight = currentHeight
            newWidth = newHeight * mAspectRatio
        } else {
            // 当前更高，以宽度为准
            newWidth = currentWidth
            newHeight = newWidth / mAspectRatio
        }
        
        // 确保不小于最小尺寸
        if (newWidth < mMinCropSize) {
            newWidth = mMinCropSize
            newHeight = newWidth / mAspectRatio
        }
        if (newHeight < mMinCropSize) {
            newHeight = mMinCropSize
            newWidth = newHeight * mAspectRatio
        }
        
        // 保持中心点不变
        var left = centerX - newWidth / 2
        var top = centerY - newHeight / 2
        var right = centerX + newWidth / 2
        var bottom = centerY + newHeight / 2
        
        // 限制在视频边界内
        if (left < mVideoBounds.left) {
            left = mVideoBounds.left
            right = left + newWidth
        }
        if (right > mVideoBounds.right) {
            right = mVideoBounds.right
            left = right - newWidth
        }
        if (top < mVideoBounds.top) {
            top = mVideoBounds.top
            bottom = top + newHeight
        }
        if (bottom > mVideoBounds.bottom) {
            bottom = mVideoBounds.bottom
            top = bottom - newHeight
        }
        
        mCropRect.set(left, top, right, bottom)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (mVideoBounds.isEmpty || mCropRect.isEmpty) {
            return
        }
        
        // 绘制遮罩（视频区域外的部分）
        if (mCropRect.top > mVideoBounds.top) {
            canvas.drawRect(mVideoBounds.left, mVideoBounds.top, 
                    mVideoBounds.right, mCropRect.top, mOverlayPaint)
        }
        if (mCropRect.bottom < mVideoBounds.bottom) {
            canvas.drawRect(mVideoBounds.left, mCropRect.bottom, 
                    mVideoBounds.right, mVideoBounds.bottom, mOverlayPaint)
        }
        if (mCropRect.left > mVideoBounds.left) {
            canvas.drawRect(mVideoBounds.left, mCropRect.top, 
                    mCropRect.left, mCropRect.bottom, mOverlayPaint)
        }
        if (mCropRect.right < mVideoBounds.right) {
            canvas.drawRect(mCropRect.right, mCropRect.top, 
                    mVideoBounds.right, mCropRect.bottom, mOverlayPaint)
        }
        
        // 绘制裁剪框边框
        canvas.drawRect(mCropRect, mBorderPaint)
        
        // 绘制四个角
        drawCorner(canvas, mCropRect.left, mCropRect.top, true, true) // 左上
        drawCorner(canvas, mCropRect.right, mCropRect.top, false, true) // 右上
        drawCorner(canvas, mCropRect.left, mCropRect.bottom, true, false) // 左下
        drawCorner(canvas, mCropRect.right, mCropRect.bottom, false, false) // 右下
    }
    
    /**
     * 绘制边角
     */
    private fun drawCorner(canvas: Canvas, x: Float, y: Float, isLeft: Boolean, isTop: Boolean) {
        val halfSize = mCornerSize / 2
        val startX = if (isLeft) x else x - mCornerSize
        val endX = if (isLeft) x + mCornerSize else x
        val startY = if (isTop) y else y - mCornerSize
        val endY = if (isTop) y + mCornerSize else y
        
        // 水平线
        canvas.drawLine(startX, y, endX, y, mCornerPaint)
        // 垂直线
        canvas.drawLine(x, startY, x, endY, mCornerPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mVideoBounds.isEmpty || mCropRect.isEmpty) {
            return false
        }
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return handleTouchDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> return handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> return handleTouchMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return handleTouchUp()
            MotionEvent.ACTION_POINTER_UP -> return handlePointerUp(event)
        }
        
        return super.onTouchEvent(event)
    }
    
    private fun handleTouchDown(event: MotionEvent): Boolean {
        mActivePointerId = event.getPointerId(0)
        mLastTouchX = event.x
        mLastTouchY = event.y
        
        // 检查是否点击在裁剪框内（用于拖拽）
        if (mCropRect.contains(mLastTouchX, mLastTouchY)) {
            mIsDragging = true
            return true
        }
        
        return false
    }
    
    private fun handlePointerDown(event: MotionEvent): Boolean {
        if (event.pointerCount == 2) {
            mSecondPointerId = event.getPointerId(1)
            mIsScaling = true
            mIsDragging = false
            
            // 计算初始距离
            val x1 = event.getX(0)
            val y1 = event.getY(0)
            val x2 = event.getX(1)
            val y2 = event.getY(1)
            mInitialDistance = kotlin.math.sqrt((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2)).toFloat()
            
            return true
        }
        return false
    }
    
    private fun handleTouchMove(event: MotionEvent): Boolean {
        if (mIsScaling && event.pointerCount == 2) {
            // 双指缩放（简化实现）
            val index1 = event.findPointerIndex(mActivePointerId)
            val index2 = event.findPointerIndex(mSecondPointerId)
            
            if (index1 >= 0 && index2 >= 0) {
                val x1 = event.getX(index1)
                val y1 = event.getY(index1)
                val x2 = event.getX(index2)
                val y2 = event.getY(index2)
                
                val currentDistance = kotlin.math.sqrt((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2)).toFloat()
                val scale = currentDistance / mInitialDistance
                
                // 以裁剪框中心为基准缩放
                val centerX = mCropRect.centerX()
                val centerY = mCropRect.centerY()
                var newWidth = mCropRect.width() * scale
                var newHeight = newWidth / mAspectRatio
                
                // 限制在视频边界内
                val videoWidth = mVideoBounds.width()
                val videoHeight = mVideoBounds.height()
                
                var maxWidth = videoWidth
                var maxHeight = videoHeight
                
                if (maxWidth / maxHeight > mAspectRatio) {
                    maxWidth = maxHeight * mAspectRatio
                } else {
                    maxHeight = maxWidth / mAspectRatio
                }
                
                if (newWidth > maxWidth) {
                    newWidth = maxWidth
                    newHeight = newWidth / mAspectRatio
                }
                if (newHeight > maxHeight) {
                    newHeight = maxHeight
                    newWidth = newHeight * mAspectRatio
                }
                
                if (newWidth < mMinCropSize) {
                    newWidth = mMinCropSize
                    newHeight = newWidth / mAspectRatio
                }
                if (newHeight < mMinCropSize) {
                    newHeight = mMinCropSize
                    newWidth = newHeight * mAspectRatio
                }
                
                var left = centerX - newWidth / 2
                var top = centerY - newHeight / 2
                var right = centerX + newWidth / 2
                var bottom = centerY + newHeight / 2
                
                // 限制在视频边界内
                if (left < mVideoBounds.left) {
                    val offset = mVideoBounds.left - left
                    left += offset
                    right += offset
                }
                if (right > mVideoBounds.right) {
                    val offset = right - mVideoBounds.right
                    left -= offset
                    right -= offset
                }
                if (top < mVideoBounds.top) {
                    val offset = mVideoBounds.top - top
                    top += offset
                    bottom += offset
                }
                if (bottom > mVideoBounds.bottom) {
                    val offset = bottom - mVideoBounds.bottom
                    top -= offset
                    bottom -= offset
                }
                
                mCropRect.set(left, top, right, bottom)
                mInitialDistance = currentDistance
                invalidate()
                notifyCropChanged()
            }
            return true
        } else if (mIsDragging) {
            // 单指拖拽
            val index = event.findPointerIndex(mActivePointerId)
            if (index >= 0) {
                val x = event.getX(index)
                val y = event.getY(index)
                
                val dx = x - mLastTouchX
                val dy = y - mLastTouchY
                
                // 移动裁剪框
                var newLeft = mCropRect.left + dx
                var newTop = mCropRect.top + dy
                var newRight = mCropRect.right + dx
                var newBottom = mCropRect.bottom + dy
                
                // 限制在视频边界内
                if (newLeft < mVideoBounds.left) {
                    val offset = mVideoBounds.left - newLeft
                    newLeft += offset
                    newRight += offset
                }
                if (newRight > mVideoBounds.right) {
                    val offset = newRight - mVideoBounds.right
                    newLeft -= offset
                    newRight -= offset
                }
                if (newTop < mVideoBounds.top) {
                    val offset = mVideoBounds.top - newTop
                    newTop += offset
                    newBottom += offset
                }
                if (newBottom > mVideoBounds.bottom) {
                    val offset = newBottom - mVideoBounds.bottom
                    newTop -= offset
                    newBottom -= offset
                }
                
                mCropRect.set(newLeft, newTop, newRight, newBottom)
                mLastTouchX = x
                mLastTouchY = y
                invalidate()
                notifyCropChanged()
            }
            return true
        }
        
        return false
    }
    
    private fun handleTouchUp(): Boolean {
        mIsDragging = false
        mIsScaling = false
        mActivePointerId = -1
        mSecondPointerId = -1
        return true
    }
    
    private fun handlePointerUp(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        
        if (pointerId == mActivePointerId) {
            val newIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = event.getPointerId(newIndex)
            mLastTouchX = event.getX(newIndex)
            mLastTouchY = event.getY(newIndex)
        } else if (pointerId == mSecondPointerId) {
            mSecondPointerId = -1
        }
        
        mIsScaling = false
        return true
    }
    
    /**
     * 通知裁剪框变化
     */
    private fun notifyCropChanged() {
        mCropChangeListener?.let { listener ->
            val cropX = mCropRect.left - mVideoBounds.left
            val cropY = mCropRect.top - mVideoBounds.top
            val cropWidth = mCropRect.width()
            val cropHeight = mCropRect.height()
            
            listener.onCropChanged(
                RectF(mCropRect),
                cropX, cropY, cropWidth, cropHeight
            )
        }
    }
    
    /**
     * 设置裁剪变化监听器
     */
    fun setOnCropChangeListener(listener: OnCropChangeListener?) {
        mCropChangeListener = listener
    }
    
    /**
     * 获取当前裁剪框（相对于视频预览的坐标）
     */
    fun getCropRect(): RectF {
        return RectF(mCropRect)
    }
}

