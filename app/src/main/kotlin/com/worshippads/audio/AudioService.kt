package com.worshippads.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.worshippads.MainActivity

class AudioService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyName = intent?.getStringExtra(EXTRA_KEY_NAME) ?: "Playing"
        val isMinor = intent?.getBooleanExtra(EXTRA_IS_MINOR, false) ?: false

        val notification = createNotification(keyName, isMinor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Worship Pads Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when pads are playing"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(keyName: String, isMinor: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val modeText = if (isMinor) "minor" else "major"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Worship Pads")
            .setContentText("Playing $keyName $modeText")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "worship_pads_playback"
        const val NOTIFICATION_ID = 1
        const val EXTRA_KEY_NAME = "key_name"
        const val EXTRA_IS_MINOR = "is_minor"
    }
}
