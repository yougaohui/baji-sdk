package com.baji.demo.utils

import com.baji.demo.ui.VideoCutActivity
import java.lang.ref.WeakReference
import java.util.TimerTask

/**
 * 定时任务实现
 * 用于VideoCutActivity中定时更新视频进度
 */
class TimerTaskImp(activity: VideoCutActivity) : TimerTask() {
    private val weakReference: WeakReference<VideoCutActivity> = WeakReference(activity)
    
    override fun run() {
        weakReference.get()?.getVideoProgress()
    }
}
