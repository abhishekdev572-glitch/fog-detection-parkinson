package com.fogdetector.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fogdetector.R

object NotificationHelper {

    private const val CHANNEL_ID   = "fog_alerts"
    private const val CHANNEL_NAME = "FOG Alerts"
    private const val NOTIF_ID     = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when Freezing of Gait is detected"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    fun notifyFogDetected(context: Context, probability: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ FOG Detected")
            .setContentText("Freezing of Gait detected ($probability % confidence). Tactile alert active.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        try {
            nm.notify(NOTIF_ID, notification)
        } catch (_: SecurityException) { /* permission not granted */ }
    }

    fun cancelFogNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
