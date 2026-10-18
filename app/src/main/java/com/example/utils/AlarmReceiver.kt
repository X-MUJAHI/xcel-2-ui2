package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val ALARM_REQUEST_CODE = 300
        const val ACTION_AUTO_DOWNLOAD_SCRIPTS = "com.example.action.AUTO_DOWNLOAD_SCRIPTS"
        private const val AUTO_DOWNLOAD_REQUEST_CODE = 301

        const val ACTION_SUSPEND_FFTH = "com.example.action.SUSPEND_FFTH"
        private const val SUSPEND_FFTH_REQUEST_CODE = 302

        fun scheduleAlarm(context: Context, triggerTimeMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun scheduleAutoDownloadAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_AUTO_DOWNLOAD_SCRIPTS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, AUTO_DOWNLOAD_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + (6 * 60 * 60 * 1000L)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun scheduleSuspendAlarm(context: Context, delayMillis: Long = 5 * 60 * 1000L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SUSPEND_FFTH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, SUSPEND_FFTH_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + delayMillis
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelSuspendAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SUSPEND_FFTH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, SUSPEND_FFTH_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SUSPEND_FFTH) {
            Thread {
                val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                RenameUtil.suspendPackage("com.dts.freefireth")
                prefs.edit().putBoolean("ffth_opened_via_app", false).apply()
            }.start()
            return
        }

        if (intent.action == ACTION_AUTO_DOWNLOAD_SCRIPTS) {
            PanelFileManager.autoDownloadActiveScriptIfNeeded(context) {
                scheduleAutoDownloadAlarm(context)
            }
            return
        }

        Thread {
            val optDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional"
            val aData = "$optDir/android-data"
            if (RenameUtil.checkDirExists(aData)) {
                RenameUtil.turnOff()
                context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(MainActivity.KEY_AUTO_OFF_TIME).apply()
            }
        }.start()
    }
}
