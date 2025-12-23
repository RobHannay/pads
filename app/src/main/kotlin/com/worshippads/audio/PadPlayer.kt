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
    @Volatile
    private var currentIsMinor = false

    fun start(pack: AudioPack, isMinor: Boolean = false) {
        if (mediaPlayer != null) return

        currentIsMinor = isMinor
        try {
            val resourceName = pack.getResourceName(key, isMinor)
            val resourceId = context.resources.getIdentifier(
                resourceName, "raw", context.packageName
            )
            if (resourceId == 0) {
                Log.e("PadPlayer", "Resource not found: $resourceName")
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

    fun getCurrentPosition(): Int = if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0

    fun getDuration(): Int = if (isPrepared) mediaPlayer?.duration ?: 0 else 0
}

enum class MusicalKey(val noteName: String, val majorResource: String, val minorResource: String) {
    C("C", "c", "c_minor"),
    C_SHARP("C#", "c_sharp", "c_sharp_minor"),
    D("D", "d", "d_minor"),
    D_SHARP("D#", "d_sharp", "d_sharp_minor"),
    E("E", "e", "e_minor"),
    F("F", "f", "f_minor"),
    F_SHARP("F#", "f_sharp", "f_sharp_minor"),
    G("G", "g", "g_minor"),
    G_SHARP("G#", "g_sharp", "g_sharp_minor"),
    A("A", "a", "a_minor"),
    A_SHARP("A#", "a_sharp", "a_sharp_minor"),
    B("B", "b", "b_minor");

    fun getResourceName(isMinor: Boolean): String = if (isMinor) minorResource else majorResource
}
