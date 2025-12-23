package com.worshippads.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

class PadPlayer(private val context: Context, private val key: MusicalKey) {
    private var mediaPlayer: MediaPlayer? = null
    @Volatile
    private var volume = 0f
    @Volatile
    private var isPrepared = false

    fun start() {
        if (mediaPlayer != null) return

        try {
            val resourceId = context.resources.getIdentifier(
                key.resourceName, "raw", context.packageName
            )
            if (resourceId == 0) {
                Log.e("PadPlayer", "Resource not found for ${key.noteName}")
                return
            }

            mediaPlayer = MediaPlayer.create(context, resourceId)?.apply {
                isLooping = true
                setVolume(volume, volume)
                setOnErrorListener { _, what, extra ->
                    Log.e("PadPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
                start()
                isPrepared = true
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Failed to create MediaPlayer for ${key.noteName}", e)
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        if (isPrepared) {
            mediaPlayer?.setVolume(volume, volume)
        }
    }

    fun getVolume(): Float = volume

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPrepared) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null
        isPrepared = false
        volume = 0f
    }

    fun pause() {
        if (isPrepared) {
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                Log.e("PadPlayer", "Error pausing MediaPlayer", e)
            }
        }
    }

    fun resume() {
        if (isPrepared) {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.e("PadPlayer", "Error resuming MediaPlayer", e)
            }
        }
    }

    fun isActive(): Boolean = mediaPlayer != null && isPrepared
}

enum class MusicalKey(val noteName: String, val resourceName: String) {
    C("C", "c"),
    C_SHARP("C#", "c_sharp"),
    D("D", "d"),
    D_SHARP("D#", "d_sharp"),
    E("E", "e"),
    F("F", "f"),
    F_SHARP("F#", "f_sharp"),
    G("G", "g"),
    G_SHARP("G#", "g_sharp"),
    A("A", "a"),
    A_SHARP("A#", "a_sharp"),
    B("B", "b")
}
