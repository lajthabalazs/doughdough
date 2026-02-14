package com.lajthabalazs.doughdough.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lajthabalazs.doughdough.MainActivity
import com.lajthabalazs.doughdough.recipe.RecipeSession

object AlarmScheduler {
    private const val ACTION_ALARM = "com.lajthabalazs.doughdough.ALARM"
    private const val EXTRA_STEP_INDEX = "step_index"
    private const val REQUEST_ALARM = 1000
    private const val REQUEST_SHOW = 2000

    fun scheduleAlarm(context: Context, triggerAtMillis: Long, stepIndex: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_STEP_INDEX, stepIndex)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_ALARM + stepIndex, intent, flags)

        // setAlarmClock exits Doze before firing - most reliable for waking device from deep sleep
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_OPEN_TASK_STEP, stepIndex)
            }
            val showPendingIntent = PendingIntent.getActivity(context, REQUEST_SHOW + stepIndex, showIntent, flags)
            val alarmInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, stepIndex: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_STEP_INDEX, stepIndex)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_ALARM + stepIndex, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    fun getStepIndex(intent: Intent?): Int = intent?.getIntExtra(EXTRA_STEP_INDEX, -1) ?: -1
}
