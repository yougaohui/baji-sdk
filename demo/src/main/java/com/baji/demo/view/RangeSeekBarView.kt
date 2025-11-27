package com.baji.demo.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.Nullable
import com.baji.demo.ui.VideoCutActivity
import com.baji.demo.utils.VideoUtils

/**
 * 时间范围选择视图（简化版，仅显示，不支持触摸调整）
 */
class RangeSeekBarView @JvmOverloads constructor(
    context: Context,
    @Nullable attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        const val INVALID_POINTER_ID = 255
        const val ACTION_POINTER_INDEX_MASK = 0x0000ff00
        const val ACTION_POINTER_INDEX_SHIFT = 8
    }
    
    private var mMinShootTime = 5 * 1000L // 最小剪辑5s
    private var absoluteMinValuePrim = 0.0
    private var absoluteMaxValuePrim = 0.0
    private var normalizedMinValue = 0.0 // 点坐标占总长度的比例值，范围从0-1
    private var normalizedMaxValue = 1.0 // 点坐标占总长度的比例值，范围从0-1
    private var normalizedMinValueTime = 0.0
    private var normalizedMaxValueTime = 1.0
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mVideoTrimTimePaintL = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mVideoTrimTimePaintR = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val padding = 0f
    private var mStartPosition = 0L
    private var mEndPosition = 0L
    
    private var paddingTop = 0
    private val whiteColorRes = android.graphics.Color.WHITE
    
    enum class Thumb {
        MIN, MAX
    }
    
    private var mRangeSeekBarChangeListener: OnRangeSeekBarChangeListener? = null
    
    init {
        absoluteMinValuePrim = 0.0 * 1000
        absoluteMaxValuePrim = VideoCutActivity.MAX_TIME * 1000.0
        isFocusable = true
        isFocusableInTouchMode = true
        init()
    }
    
    private fun init() {
        paddingTop = VideoUtils.dp2px(context, 10f)
        
        val shadowColor = 0xAA000000.toInt() // 半透明黑色
        mShadow.apply {
            isAntiAlias = true
            color = shadowColor
        }
        
        rectPaint.apply {
            style = Paint.Style.FILL
            color = whiteColorRes
        }
        
        mVideoTrimTimePaintL.apply {
            strokeWidth = 3f
            setARGB(255, 51, 51, 51)
            textSize = 28f
            isAntiAlias = true
            color = whiteColorRes
            textAlign = Paint.Align.LEFT
        }
        
        mVideoTrimTimePaintR.apply {
            strokeWidth = 3f
            setARGB(255, 51, 51, 51)
            textSize = 28f
            isAntiAlias = true
            color = whiteColorRes
            textAlign = Paint.Align.RIGHT
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = 300
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        var height = 120
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bgMiddleLeft = 0f
        val bgMiddleRight = width - paddingRight.toFloat()
        val rangeL = normalizedToScreen(normalizedMinValue)
        val rangeR = normalizedToScreen(normalizedMaxValue)
        val leftRect = Rect(bgMiddleLeft.toInt(), height, rangeL.toInt(), 0)
        val rightRect = Rect(rangeR.toInt(), height, bgMiddleRight.toInt(), 0)
        canvas.drawRect(leftRect, mShadow)
        canvas.drawRect(rightRect, mShadow)
        
        // 上边框
        val thumbHalfWidth = VideoUtils.dp2px(context, 12.5f) / 2f
        val thumbPaddingTop = 0f
        canvas.drawRect(
            rangeL + thumbHalfWidth,
            thumbPaddingTop + paddingTop,
            rangeR - thumbHalfWidth,
            thumbPaddingTop + VideoUtils.dp2px(context, 2f) + paddingTop,
            rectPaint
        )
        
        // 下边框 - 预留文字显示空间
        val bottomBorderTop = height - VideoUtils.dp2px(context, 2f) - VideoUtils.dp2px(context, 20f).toFloat()
        val bottomBorderBottom = height - VideoUtils.dp2px(context, 20f).toFloat()
        canvas.drawRect(
            rangeL + thumbHalfWidth,
            bottomBorderTop,
            rangeR - thumbHalfWidth,
            bottomBorderBottom,
            rectPaint
        )
        
        // 绘制文字
        drawVideoTrimTimeText(canvas, rangeL, rangeR)
    }
    
    private fun drawVideoTrimTimeText(canvas: Canvas, leftX: Float, rightX: Float) {
        val leftThumbsTime = VideoUtils.convertSecondsToTime(mStartPosition)
        val rightThumbsTime = VideoUtils.convertSecondsToTime(mEndPosition)
        
        // 文字显示在底部边框上方
        val textY = height - VideoUtils.dp2px(context, 5f).toFloat()
        
        // 计算文字宽度，避免文字重叠
        val leftTextWidth = mVideoTrimTimePaintL.measureText(leftThumbsTime)
        val rightTextWidth = mVideoTrimTimePaintR.measureText(rightThumbsTime)
        
        var adjustedLeftX = leftX
        var adjustedRightX = rightX
        
        // 确保文字不会超出控件边界
        if (adjustedLeftX + leftTextWidth > width) {
            adjustedLeftX = width - leftTextWidth - VideoUtils.dp2px(context, 5f)
        }
        if (adjustedRightX - rightTextWidth < 0) {
            adjustedRightX = rightTextWidth + VideoUtils.dp2px(context, 5f)
        }
        
        canvas.drawText(leftThumbsTime, adjustedLeftX, textY, mVideoTrimTimePaintL)
        canvas.drawText(rightThumbsTime, adjustedRightX, textY, mVideoTrimTimePaintR)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 禁用触摸事件，强制固定5秒剪辑（与主项目保持一致）
        return false
    }
    
    private fun normalizedToScreen(normalizedCoord: Double): Float {
        return (paddingLeft + normalizedCoord * (width - paddingLeft - paddingRight)).toFloat()
    }
    
    private fun valueToNormalized(value: Long): Double {
        return if (absoluteMaxValuePrim - absoluteMinValuePrim == 0.0) {
            0.0
        } else {
            (value - absoluteMinValuePrim) / (absoluteMaxValuePrim - absoluteMinValuePrim)
        }
    }
    
    fun setStartEndTime(start: Long, end: Long) {
        mStartPosition = start / 1000
        mEndPosition = end / 1000
        invalidate()
    }
    
    fun setSelectedMinValue(value: Long) {
        if (absoluteMaxValuePrim - absoluteMinValuePrim == 0.0) {
            setNormalizedMinValue(0.0)
        } else {
            setNormalizedMinValue(valueToNormalized(value))
        }
    }
    
    fun setSelectedMaxValue(value: Long) {
        if (absoluteMaxValuePrim - absoluteMinValuePrim == 0.0) {
            setNormalizedMaxValue(1.0)
        } else {
            setNormalizedMaxValue(valueToNormalized(value))
        }
    }
    
    private fun setNormalizedMinValue(value: Double) {
        normalizedMinValue = value.coerceIn(0.0, normalizedMaxValue.coerceIn(0.0, 1.0))
        invalidate()
    }
    
    private fun setNormalizedMaxValue(value: Double) {
        normalizedMaxValue = value.coerceIn(normalizedMinValue.coerceIn(0.0, 1.0), 1.0)
        invalidate()
    }
    
    fun getSelectedMinValue(): Long {
        return normalizedToValue(normalizedMinValueTime)
    }
    
    fun getSelectedMaxValue(): Long {
        return normalizedToValue(normalizedMaxValueTime)
    }
    
    private fun normalizedToValue(normalized: Double): Long {
        return (absoluteMinValuePrim + normalized * (absoluteMaxValuePrim - absoluteMinValuePrim)).toLong()
    }
    
    fun setMinShootTime(minCutTime: Long) {
        mMinShootTime = minCutTime
    }
    
    fun setNotifyWhileDragging(flag: Boolean) {
        // 简化版本不支持拖动通知
    }
    
    fun setTouchDown(touchDown: Boolean) {
        // 简化版本不支持触摸
    }
    
    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("SUPER", super.onSaveInstanceState())
        bundle.putDouble("MIN", normalizedMinValue)
        bundle.putDouble("MAX", normalizedMaxValue)
        bundle.putDouble("MIN_TIME", normalizedMinValueTime)
        bundle.putDouble("MAX_TIME", normalizedMaxValueTime)
        return bundle
    }
    
    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = state as? Bundle ?: return
        super.onRestoreInstanceState(bundle.getParcelable("SUPER"))
        normalizedMinValue = bundle.getDouble("MIN")
        normalizedMaxValue = bundle.getDouble("MAX")
        normalizedMinValueTime = bundle.getDouble("MIN_TIME")
        normalizedMaxValueTime = bundle.getDouble("MAX_TIME")
    }
    
    interface OnRangeSeekBarChangeListener {
        fun onRangeSeekBarValuesChanged(
            bar: RangeSeekBarView,
            minValue: Long,
            maxValue: Long,
            action: Int,
            isMin: Boolean,
            pressedThumb: Thumb
        )
    }
    
    fun setOnRangeSeekBarChangeListener(listener: OnRangeSeekBarChangeListener?) {
        mRangeSeekBarChangeListener = listener
    }
}

