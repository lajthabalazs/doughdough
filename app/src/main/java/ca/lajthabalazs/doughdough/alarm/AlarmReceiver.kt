package ca.lajthabalazs.doughdough.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ca.lajthabalazs.doughdough.MainActivity
import ca.lajthabalazs.doughdough.recipe.RecipeSession
import ca.lajthabalazs.doughdough.notification.NotificationHelper

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "ca.lajthabalazs.doughdough.ALARM") return
        val stepIndex = AlarmScheduler.getStepIndex(intent)
        if (stepIndex < 0) return

        val session = RecipeSession.restore(context) ?: return
        if (stepIndex >= session.recipe.steps.size) return

        val step = session.recipe.steps[stepIndex]
        session.setCurrentStep(stepIndex)
        RecipeSession.save(context, session)

        // Play alarm sound
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, alarmUri)
            ringtone.play()
        } catch (_: Exception) {}

        // Vibrate
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500L)
            }
        } catch (_: Exception) {}

        NotificationHelper.showTaskNotification(context, step.title, step.description, stepIndex)
    }
}
