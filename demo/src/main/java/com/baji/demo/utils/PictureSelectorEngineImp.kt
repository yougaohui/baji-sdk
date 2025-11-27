package com.baji.demo.utils

import android.util.Log
import com.luck.picture.lib.engine.ImageEngine
import com.luck.picture.lib.engine.PictureSelectorEngine
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.listener.OnResultCallbackListener

/**
 * PictureSelector引擎实现
 * 用于在内存不足情况下重新创建ImageEngine和回调
 */
class PictureSelectorEngineImp : PictureSelectorEngine {
    
    companion object {
        private const val TAG = "PictureSelectorEngineImp"
    }

    override fun createEngine(): ImageEngine {
        // 内存极度不足的情况下，重新创建图片加载引擎
        Log.d(TAG, "重新创建ImageEngine")
        return GlideEngine.createGlideEngine()
    }

    override fun getResultCallbackListener(): OnResultCallbackListener<LocalMedia>? {
        // 内存极度不足的情况下，可以在这里进行补救措施
        Log.d(TAG, "重新创建ResultCallbackListener")
        return object : OnResultCallbackListener<LocalMedia> {
            override fun onResult(result: MutableList<LocalMedia>?) {
                Log.i(TAG, "onResult: ${result?.size ?: 0}")
                // 可以通过广播或其他方式将结果推送到相应页面
            }

            override fun onCancel() {
                Log.i(TAG, "PictureSelector onCancel")
            }
        }
    }
}

