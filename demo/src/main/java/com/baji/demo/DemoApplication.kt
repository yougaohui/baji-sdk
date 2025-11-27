package com.baji.demo

import android.app.Application
import android.content.Context
import com.baji.demo.utils.PictureSelectorEngineImp
import com.luck.picture.lib.app.IApp
import com.luck.picture.lib.engine.PictureSelectorEngine

class DemoApplication : Application(), IApp {
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun getAppContext(): Context {
        return this
    }
    
    override fun getPictureSelectorEngine(): PictureSelectorEngine {
        return PictureSelectorEngineImp()
    }
}

