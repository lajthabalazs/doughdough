package com.lajthabalazs.doughdough.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.lajthabalazs.doughdough.MainActivity
import com.lajthabalazs.doughdough.recipe.RecipeSession
import com.lajthabalazs.doughdough.notification.NotificationHelper

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.lajthabalazs.doughdough.ALARM") return
        val stepIndex = AlarmScheduler.getStepIndex(intent)
        if (stepIndex < 0) return

        val session = RecipeSession.restore(context) ?: return
        if (stepIndex >= session.recipe.steps.size) return

        val step = session.recipe.steps[stepIndex]
        // Do not advance session here: we stay on timer screen and count to negative until user taps Start

        // Use attributed context so AppOps sees a declared tag (avoids "attributionTag not declared" log)
        val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.createAttributionContext("alarm")
        } else {
            context
        }

        // Play alarm sound and register so UI can stop it when user taps Start or Cancel recipe
        var ringtone: Ringtone? = null
        var vibrator: Vibrator? = null
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(ctx, alarmUri)
            ringtone.play()
        } catch (_: Exception) {}

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500L)
            }
        } catch (_: Exception) {}

        AlarmSoundManager.setCurrent(ringtone, vibrator)

        NotificationHelper.showTaskNotification(ctx, step.title, step.description, stepIndex)
    }
}
