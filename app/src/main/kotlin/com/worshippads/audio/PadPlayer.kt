package com.worshippads.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.pow

private fun pitchForOctave(octave: Int): Double = 2.0.pow(octave)

class PadPlayer(private val context: Context, private val key: MusicalKey) {
    private var primaryPlayer: ExoPlayer? = null
    private var primaryProcessor: RubberbandAudioProcessor? = null
    private var secondaryPlayer: ExoPlayer? = null
    private var secondaryProcessor: RubberbandAudioProcessor? = null

    @Volatile
    private var volume = 0f
    @Volatile
    private var isPrepared = false
    @Volatile
    private var currentPack: AudioPack? = null
    @Volatile
    private var currentIsMinor = false
    @Volatile
    private var currentOctave = 0
    @Volatile
    private var _isCrossfading = false

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Crossfade duration for looping (in ms)
    var loopCrossfadeDurationMs: Long = 10000L

    fun start(pack: AudioPack, isMinor: Boolean = false, octave: Int = 0) {
        if (primaryPlayer != null) return

        currentPack = pack
        currentIsMinor = isMinor
        currentOctave = octave

        val built = createPlayer(pack, isMinor, octave) ?: return
        primaryPlayer = built.first
        primaryProcessor = built.second

        built.first.let {
            it.volume = volume
            it.play()
        }
        isPrepared = true

        // Start monitoring for loop crossfade
        startLoopMonitor()
    }

    private fun createPlayer(
        pack: AudioPack,
        isMinor: Boolean,
        octave: Int,
    ): Pair<ExoPlayer, RubberbandAudioProcessor>? {
        return try {
            val resourceName = pack.getResourceName(key, isMinor)
            val resourceId = context.resources.getIdentifier(
                resourceName, "raw", context.packageName
            )
            if (resourceId == 0) {
                Log.e("PadPlayer", "Resource not found: $resourceName")
                return null
            }
            val uri = RawResourceDataSource.buildRawResourceUri(resourceId)

            val processor = RubberbandAudioProcessor().apply {
                pitchScale = pitchForOctave(octave)
            }
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(processor))
                        .build()
                }
            }

            val player = ExoPlayer.Builder(context, renderersFactory)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("PadPlayer", "ExoPlayer error: ${error.message}", error)
                        }
                    })
                }
            Pair(player, processor)
        } catch (e: Exception) {
            Log.e("PadPlayer", "Failed to create ExoPlayer for ${key.noteName}", e)
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
                        if (duration > 0) {
                            val timeUntilEnd = duration - position
                            if (timeUntilEnd <= loopCrossfadeDurationMs && timeUntilEnd > 0) {
                                performLoopCrossfade()
                                delay(loopCrossfadeDurationMs + 100)
                                continue
                            }
                        }
                        delay(100)
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

        val built = createPlayer(pack, currentIsMinor, currentOctave) ?: run {
            _isCrossfading = false
            return
        }
        val newPlayer = built.first
        val newProcessor = built.second
        secondaryPlayer = newPlayer
        secondaryProcessor = newProcessor

        newPlayer.volume = 0f
        newPlayer.play()

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
                oldPlayer.volume = oldVol
                newPlayer.volume = newVol
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
        primaryProcessor = newProcessor
        secondaryPlayer = null
        secondaryProcessor = null
        newPlayer.volume = volume
        _isCrossfading = false
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        if (isPrepared) {
            try {
                primaryPlayer?.volume = volume
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
            primaryPlayer?.run {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Error stopping primary ExoPlayer", e)
        }

        try {
            secondaryPlayer?.run {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("PadPlayer", "Error stopping secondary ExoPlayer", e)
        }

        primaryPlayer = null
        secondaryPlayer = null
        primaryProcessor = null
        secondaryProcessor = null
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
                Log.e("PadPlayer", "Error pausing ExoPlayer", e)
            }
        }
    }

    fun resume() {
        if (isPrepared) {
            try {
                primaryPlayer?.play()
                secondaryPlayer?.play()
            } catch (e: Exception) {
                Log.e("PadPlayer", "Error resuming ExoPlayer", e)
            }
        }
    }

    fun isActive(): Boolean = primaryPlayer != null && isPrepared

    fun isCrossfading(): Boolean = _isCrossfading

    fun getPlayerStates(): List<PlayerState> {
        val states = mutableListOf<PlayerState>()
        primaryPlayer?.let { p ->
            try {
                val duration = p.duration.let { if (it > 0) it.toInt() else 0 }
                states.add(PlayerState(
                    label = if (_isCrossfading) "(old)" else "",
                    position = p.currentPosition.toInt(),
                    duration = duration,
                    volume = if (_isCrossfading) {
                        volume * (1f - getCrossfadeProgress())
                    } else volume
                ))
            } catch (_: Exception) {}
        }
        secondaryPlayer?.let { p ->
            try {
                val duration = p.duration.let { if (it > 0) it.toInt() else 0 }
                states.add(PlayerState(
                    label = "(new)",
                    position = p.currentPosition.toInt(),
                    duration = duration,
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
            primaryPlayer?.currentPosition?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    } else 0

    fun getDuration(): Int = if (isPrepared) {
        try {
            primaryPlayer?.duration?.let { if (it > 0) it.toInt() else 0 } ?: 0
        } catch (e: Exception) {
            0
        }
    } else 0

    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            try {
                primaryPlayer?.seekTo(positionMs.toLong())
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
