package com.jarvismini

import android.app.Application
import com.jarvismini.engine.LLMEngine

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LLMEngine.init(this)
    }
}
