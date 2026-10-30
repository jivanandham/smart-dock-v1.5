package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log

/** Short one-shot sound used when a countdown or focus session finishes. */
object ChronoChime {
    fun play(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (e: Exception) {
            Log.w("ChronoChime", "Unable to play chime", e)
        }
    }
}
