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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.worshippads.MainActivity
import com.worshippads.R

class AudioService : Service() {
    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "WorshipPads").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onStop() {
                    sendBroadcast(Intent(ACTION_STOP_PLAYBACK).setPackage(packageName))
                }

                override fun onPause() {
                    sendBroadcast(Intent(ACTION_STOP_PLAYBACK).setPackage(packageName))
                }

                override fun onPlay() {
                    // Already playing, ignore
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyName = intent?.getStringExtra(EXTRA_KEY_NAME) ?: "Playing"
        val isMinor = intent?.getBooleanExtra(EXTRA_IS_MINOR, false) ?: false

        updateMediaSession(keyName, isMinor)
        val notification = createNotification(keyName, isMinor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun updateMediaSession(keyName: String, isMinor: Boolean) {
        val modeText = if (isMinor) "Minor" else "Major"

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "$keyName $modeText")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Worship Pads")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "The Bridge")
                .build()
        )

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
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

        val modeText = if (isMinor) "Minor" else "Major"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$keyName $modeText")
            .setContentText("Worship Pads")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "worship_pads_playback"
        const val NOTIFICATION_ID = 1
        const val EXTRA_KEY_NAME = "key_name"
        const val EXTRA_IS_MINOR = "is_minor"
        const val ACTION_STOP_PLAYBACK = "com.worshippads.STOP_PLAYBACK"
    }
}
