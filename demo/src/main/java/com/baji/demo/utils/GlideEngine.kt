package com.baji.demo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.luck.picture.lib.engine.ImageEngine
import com.luck.picture.lib.listener.OnImageCompleteCallback
import com.luck.picture.lib.tools.MediaUtils
import com.luck.picture.lib.widget.longimage.ImageSource
import com.luck.picture.lib.widget.longimage.ImageViewState
import com.luck.picture.lib.widget.longimage.SubsamplingScaleImageView

/**
 * Glide图片加载引擎
 * 用于PictureSelector图片选择库
 */
class GlideEngine private constructor() : ImageEngine {

    /**
     * 加载图片
     */
    override fun loadImage(@NonNull context: Context, @NonNull url: String, @NonNull imageView: ImageView) {
        Glide.with(context)
            .load(url)
            .into(imageView)
    }

    /**
     * 加载网络图片适配长图方案
     */
    override fun loadImage(
        @NonNull context: Context,
        @NonNull url: String,
        @NonNull imageView: ImageView,
        longImageView: SubsamplingScaleImageView?,
        callback: OnImageCompleteCallback?
    ) {
        Glide.with(context)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onLoadStarted(placeholder: Drawable?) {
                    super.onLoadStarted(placeholder)
                    callback?.onShowLoading()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    callback?.onHideLoading()
                }

                override fun onResourceReady(
                    @NonNull resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    callback?.onHideLoading()
                    val eqLongImage = MediaUtils.isLongImg(resource.width, resource.height)
                    
                    longImageView?.apply {
                        visibility = if (eqLongImage) View.VISIBLE else View.GONE
                        if (eqLongImage) {
                            setQuickScaleEnabled(true)
                            setZoomEnabled(true)
                            setDoubleTapZoomDuration(100)
                            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP)
                            setDoubleTapZoomDpi(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
                            setImage(
                                ImageSource.cachedBitmap(resource),
                                ImageViewState(0f, PointF(0f, 0f), 0)
                            )
                        }
                    }
                    
                    imageView.visibility = if (eqLongImage) View.GONE else View.VISIBLE
                    if (!eqLongImage) {
                        imageView.setImageBitmap(resource)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // 清理资源
                }
            })
    }

    /**
     * 加载相册目录
     */
    override fun loadFolderImage(
        @NonNull context: Context,
        @NonNull url: String,
        @NonNull imageView: ImageView
    ) {
        Glide.with(context)
            .asBitmap()
            .load(url)
            .override(180, 180)
            .centerCrop()
            .sizeMultiplier(0.5f)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(object : BitmapImageViewTarget(imageView) {
                override fun setResource(resource: Bitmap?) {
                    if (resource != null) {
                        val circularBitmapDrawable = RoundedBitmapDrawableFactory
                            .create(context.resources, resource)
                        circularBitmapDrawable.cornerRadius = 8f
                        imageView.setImageDrawable(circularBitmapDrawable)
                    }
                }
            })
    }

    /**
     * 加载图片列表图片
     */
    override fun loadGridImage(
        @NonNull context: Context,
        @NonNull url: String,
        @NonNull imageView: ImageView
    ) {
        Glide.with(context)
            .load(url)
            .override(200, 200)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imageView)
    }

    companion object {
        @Volatile
        private var instance: GlideEngine? = null

        fun createGlideEngine(): GlideEngine {
            return instance ?: synchronized(this) {
                instance ?: GlideEngine().also { instance = it }
            }
        }
    }
}

