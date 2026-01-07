package com.ritwikg.messageinajar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.AlarmManager
import android.app.PendingIntent

private const val ACTION_SHOW = "jar.ACTION_SHOW"
private const val ACTION_SNOOZE = "jar.ACTION_SNOOZE"
private const val ACTION_REMIND_LATER = "jar.ACTION_REMIND_LATER"

private const val NOTIFICATION_ID = 1001

class JarAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = NotificationManagerCompat.from(context)

        when (intent.action) {

            ACTION_SNOOZE -> {
                nm.cancel(NOTIFICATION_ID)
            }

            ACTION_REMIND_LATER -> {
                nm.cancel(NOTIFICATION_ID)
                scheduleReminder(context, 30)
            }

            ACTION_SHOW, null -> {
                showNotification(context)
            }
        }
    }

    private fun showNotification(context: Context) {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, JarAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remindIntent = Intent(context, JarAlarmReceiver::class.java).apply {
            action = ACTION_REMIND_LATER
        }
        val remindPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            remindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "jar_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 👈 replace launcher icon
            .setContentTitle("Jar unlocked 🫙")
            .setContentText("Your message is ready to be revealed.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

            .addAction(
                R.drawable.ic_launcher_foreground,
                "Open jar",
                openPendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Snooze",
                snoozePendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Remind me later",
                remindPendingIntent
            )

            .build()

        val nm = NotificationManagerCompat.from(context)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun scheduleReminder(context: Context, minutes: Int) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, JarAlarmReceiver::class.java).apply {
            action = ACTION_SHOW
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutes * 60 * 1000,
            pendingIntent
        )
    }
}