package com.jarvismini.core

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

object MediaController {

    fun playPause(context: Context) =
        send(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun next(context: Context) =
        send(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(context: Context) =
        send(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun volumeUp(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeDown(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    private fun send(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
