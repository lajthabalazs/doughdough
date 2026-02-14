package com.lajthabalazs.doughdough.alarm

import android.media.Ringtone
import android.os.Vibrator

/**
 * Holds the current alarm ringtone and vibrator so they can be stopped when the user
 * dismisses the alarm (e.g. taps Start or Cancel recipe).
 */
object AlarmSoundManager {
    @Volatile
    private var currentRingtone: Ringtone? = null

    @Volatile
    private var currentVibrator: Vibrator? = null

    fun setCurrent(ringtone: Ringtone?, vibrator: Vibrator?) {
        currentRingtone = ringtone
        currentVibrator = vibrator
    }

    fun stop() {
        try {
            currentRingtone?.stop()
        } catch (_: Exception) {}
        currentRingtone = null
        try {
            currentVibrator?.cancel()
        } catch (_: Exception) {}
        currentVibrator = null
    }
}
