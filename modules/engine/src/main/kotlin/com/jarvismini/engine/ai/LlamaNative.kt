package com.jarvismini.engine.ai

object LlamaNative {
    init {
        System.loadLibrary("jarvis_native")
    }

    external fun nativeInit(): Long
    external fun nativeLoad(handle: Long, modelPath: String, nCtx: Int, nThreads: Int): Boolean
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String
    external fun nativeRelease(handle: Long)
}
