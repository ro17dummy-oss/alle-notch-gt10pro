package com.walle.notch.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.walle.notch.R
import com.walle.notch.core.state.IslandRepository
import com.walle.notch.ui.MainActivity

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            IslandRepository.loadCalibration(context.applicationContext)
            IslandRepository.schedulePrayerAlarms(context.applicationContext)
            return
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""

        IslandRepository.loadCalibration(context.applicationContext)
        IslandRepository.refreshPrayerTimes()
        IslandRepository.triggerPrayerAlert(
            context = context.applicationContext,
            prayerName = prayerName,
            prayerTime = prayerTime
        )
        showPrayerNotification(context.applicationContext, prayerName, prayerTime)
        IslandRepository.schedulePrayerAlarms(context.applicationContext)
    }

    private fun showPrayerNotification(context: Context, prayerName: String, prayerTime: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jadwal Sholat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pengingat waktu sholat Wall-E Notch"
                enableVibration(false) // Getaran dikelola langsung oleh pola adzan di IslandRepository
                setSound(null, null)   // Suara dikelola langsung agar berhenti otomatis saat dismiss
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            context,
            9001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walle_logo)
            .setContentTitle("Waktunya Sholat $prayerName")
            .setContentText("Padang · $prayerTime WIB")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TIME = "extra_prayer_time"
        private const val CHANNEL_ID = "walle_prayer_alert_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
