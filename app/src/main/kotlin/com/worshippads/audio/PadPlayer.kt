package com.worshippads.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.max

class PadPlayer(private val context: Context, private val key: MusicalKey) {
    private var primaryPlayer: MediaPlayer? = null
    private var secondaryPlayer: MediaPlayer? = null

    @Volatile
    private var volume = 0f
    @Volatile
    private var isPrepared = false
    @Volatile
    private var currentPack: AudioPack? = null
    @Volatile
    private var currentIsMinor = false
    @Volatile
    private var _isCrossfading = false

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Crossfade duration for looping (in ms)
    var loopCrossfadeDurationMs: Long = 10000L

    fun start(pack: AudioPack, isMinor: Boolean = false) {
        if (primaryPlayer != null) return

        currentPack = pack
        currentIsMinor = isMinor

        primaryPlayer = createPlayer(pack, isMinor)
        if (primaryPlayer == null) return

        primaryPlayer?.apply {
            setVolume(volume, volume)
            start()
            isPrepared = true
        }

        // Start monitoring for loop crossfade
        startLoopMonitor()
    }

    private fun createPlayer(pack: AudioPack, isMinor: Boolean): MediaPlayer? {
        return try {
            val resourceName = pack.getResourceName(key, isMinor)
            val resourceId = context.resources.getIdentifier(
                resourceName, "raw", context.packageName
            )
            if (resourceId == 0) {
                Log.e("PadPlayer", "Resource not found: $resourceName")
                return null
            }

            MediaPlayer.create(context, resourceId)?.apply {
                // Don't use built-in looping - we handle it ourselves
                isLooping = false
                setOnErrorListener { _, what, extra ->
                    Log.e("PadPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Failed to create MediaPlayer for ${key.noteName}", e)
            null
        }
    }

    private fun startLoopMonitor() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive && isPrepared) {
                val player = primaryPlayer
                if (player != null && isPrepared) {
                    try {
                        val position = player.currentPosition
                        val duration = player.duration
                        val timeUntilEnd = duration - position

                        // Start crossfade when we're within crossfade duration of the end
                        if (timeUntilEnd <= loopCrossfadeDurationMs && timeUntilEnd > 0) {
                            performLoopCrossfade()
                            // Wait for crossfade to complete before monitoring again
                            delay(loopCrossfadeDurationMs + 100)
                        } else {
                            delay(100) // Check every 100ms
                        }
                    } catch (e: Exception) {
                        delay(100)
                    }
                } else {
                    delay(100)
                }
            }
        }
    }

    private suspend fun performLoopCrossfade() {
        val pack = currentPack ?: return
        val oldPlayer = primaryPlayer ?: return

        _isCrossfading = true

        // Create new player starting from beginning
        val newPlayer = createPlayer(pack, currentIsMinor) ?: run {
            _isCrossfading = false
            return
        }
        secondaryPlayer = newPlayer

        newPlayer.setVolume(0f, 0f)
        newPlayer.start()

        val steps = max(1, loopCrossfadeDurationMs / 16)
        val oldStartVolume = volume

        repeat(steps.toInt()) { i ->
            if (!scope.isActive) {
                _isCrossfading = false
                return
            }
            val progress = (i + 1).toFloat() / steps

            val oldVol = (oldStartVolume * (1f - progress)).coerceAtLeast(0f)
            val newVol = (volume * progress).coerceAtMost(1f)

            try {
                oldPlayer.setVolume(oldVol, oldVol)
                newPlayer.setVolume(newVol, newVol)
            } catch (e: Exception) {
                // Player may have been released
            }

            delay(16)
        }

        // Swap players
        try {
            oldPlayer.stop()
            oldPlayer.release()
        } catch (e: Exception) {
            Log.e("PadPlayer", "Error releasing old player", e)
        }

        primaryPlayer = newPlayer
        secondaryPlayer = null
        newPlayer.setVolume(volume, volume)
        _isCrossfading = false
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        if (isPrepared) {
            try {
                primaryPlayer?.setVolume(volume, volume)
            } catch (e: Exception) {
                // Ignore if player not ready
            }
        }
    }

    fun getVolume(): Float = volume

    fun stop() {
        loopJob?.cancel()
        loopJob = null

        try {
            primaryPlayer?.apply {
                if (isPrepared) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Error stopping primary MediaPlayer", e)
        }

        try {
            secondaryPlayer?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Error stopping secondary MediaPlayer", e)
        }

        primaryPlayer = null
        secondaryPlayer = null
        isPrepared = false
        volume = 0f
        currentPack = null
    }

    fun pause() {
        if (isPrepared) {
            try {
                primaryPlayer?.pause()
                secondaryPlayer?.pause()
            } catch (e: Exception) {
                Log.e("PadPlayer", "Error pausing MediaPlayer", e)
            }
        }
    }

    fun resume() {
        if (isPrepared) {
            try {
                primaryPlayer?.start()
                secondaryPlayer?.start()
            } catch (e: Exception) {
                Log.e("PadPlayer", "Error resuming MediaPlayer", e)
            }
        }
    }

    fun isActive(): Boolean = primaryPlayer != null && isPrepared

    fun isCrossfading(): Boolean = _isCrossfading

    fun getPlayerStates(): List<PlayerState> {
        val states = mutableListOf<PlayerState>()
        primaryPlayer?.let {
            try {
                states.add(PlayerState(
                    label = if (_isCrossfading) "(old)" else "",
                    position = it.currentPosition,
                    duration = it.duration,
                    volume = if (_isCrossfading) {
                        // During crossfade, primary is fading out
                        volume * (1f - getCrossfadeProgress())
                    } else volume
                ))
            } catch (_: Exception) {}
        }
        secondaryPlayer?.let {
            try {
                states.add(PlayerState(
                    label = "(new)",
                    position = it.currentPosition,
                    duration = it.duration,
                    volume = volume * getCrossfadeProgress()
                ))
            } catch (_: Exception) {}
        }
        return states
    }

    private fun getCrossfadeProgress(): Float {
        if (!_isCrossfading) return 0f
        val secondary = secondaryPlayer ?: return 0f
        return try {
            (secondary.currentPosition.toFloat() / loopCrossfadeDurationMs).coerceIn(0f, 1f)
        } catch (_: Exception) { 0f }
    }

    fun getCurrentPosition(): Int = if (isPrepared) {
        try {
            primaryPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    } else 0

    fun getDuration(): Int = if (isPrepared) {
        try {
            primaryPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    } else 0

    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            try {
                primaryPlayer?.seekTo(positionMs)
            } catch (e: Exception) {
                Log.e("PadPlayer", "Error seeking", e)
            }
        }
    }

    fun cleanup() {
        loopJob?.cancel()
        scope.cancel()
        stop()
    }
}

data class PlayerState(
    val label: String,
    val position: Int,
    val duration: Int,
    val volume: Float
)

enum class MusicalKey(
    val sharpName: String,
    val flatName: String,
    val majorResource: String,
    val minorResource: String
) {
    C("C", "C", "c", "c_minor"),
    C_SHARP("C♯", "D♭", "c_sharp", "c_sharp_minor"),
    D("D", "D", "d", "d_minor"),
    D_SHARP("D♯", "E♭", "d_sharp", "d_sharp_minor"),
    E("E", "E", "e", "e_minor"),
    F("F", "F", "f", "f_minor"),
    F_SHARP("F♯", "G♭", "f_sharp", "f_sharp_minor"),
    G("G", "G", "g", "g_minor"),
    G_SHARP("G♯", "A♭", "g_sharp", "g_sharp_minor"),
    A("A", "A", "a", "a_minor"),
    A_SHARP("A♯", "B♭", "a_sharp", "a_sharp_minor"),
    B("B", "B", "b", "b_minor");

    val noteName: String get() = sharpName // Default for backwards compatibility

    fun displayName(useFlats: Boolean): String = if (useFlats) flatName else sharpName

    fun getResourceName(isMinor: Boolean): String = if (isMinor) minorResource else majorResource
}
