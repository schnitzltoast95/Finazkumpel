package de.quartalsmappe.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.quartalsmappe.app.MainActivity
import de.quartalsmappe.app.R
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.ReminderSettings
import de.quartalsmappe.app.time.SystemAppClock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun scheduleAll(context: Context, settings: ReminderSettings) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        DayOfWeek.entries.forEach { day ->
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra("day", day.value)
            val pending = PendingIntent.getBroadcast(
                context,
                7_000 + day.value,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pending)
            if (day in settings.enabledDays) {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextOccurrenceMillis(day, settings.hour, settings.minute),
                    WEEK_MILLIS,
                    pending,
                )
            }
        }
    }

    private fun nextOccurrenceMillis(day: DayOfWeek, hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var candidate = now.toLocalDate().atTime(hour, minute)
        while (candidate.dayOfWeek != day || !candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = AppStore(context.applicationContext)
        if (store.dailyCloses.any { it.businessDate == SystemAppClock.today() }) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "daily_close"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Tagesabschluss",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Erinnerung an den täglichen Z-Abschlag"
            }
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tagesabschluss noch offen")
            .setContentText("Den Betrag vom heutigen Z-Abschlag jetzt eintragen.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        if (
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(7_001, notification)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val store = AppStore(context.applicationContext)
            ReminderScheduler.scheduleAll(context, store.reminderSettings)
        }
    }
}

