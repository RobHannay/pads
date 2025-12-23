package com.worshippads.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class AudioEngine(private val context: Context) {
    private val majorPlayers = mutableMapOf<MusicalKey, PadPlayer>()
    private val minorPlayers = mutableMapOf<MusicalKey, PadPlayer>()

    private val _activePad = MutableStateFlow<MusicalKey?>(null)
    val activePad: StateFlow<MusicalKey?> = _activePad.asStateFlow()

    private val _isMinor = MutableStateFlow(false)
    val isMinor: StateFlow<Boolean> = _isMinor.asStateFlow()

    private val _audioPack = MutableStateFlow(AudioPack.BRIDGE)
    val audioPack: StateFlow<AudioPack> = _audioPack.asStateFlow()

    private val prefs = context.getSharedPreferences("worship_pads_prefs", Context.MODE_PRIVATE)
    private val _fadeInDurationMs = MutableStateFlow(prefs.getLong(KEY_FADE_IN_DURATION, 2000L))
    private val _fadeOutDurationMs = MutableStateFlow(prefs.getLong(KEY_FADE_OUT_DURATION, 2000L))
    private val _showDebugOverlay = MutableStateFlow(prefs.getBoolean(KEY_SHOW_DEBUG, false))
    val showDebugOverlay: StateFlow<Boolean> = _showDebugOverlay.asStateFlow()

    private val _startFromA = MutableStateFlow(prefs.getBoolean(KEY_START_FROM_A, false))
    val startFromA: StateFlow<Boolean> = _startFromA.asStateFlow()

    private val _useFlats = MutableStateFlow(prefs.getBoolean(KEY_USE_FLATS, false))
    val useFlats: StateFlow<Boolean> = _useFlats.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentFadeJob: Job? = null

    init {
        MusicalKey.entries.forEach { key ->
            majorPlayers[key] = PadPlayer(context, key)
            minorPlayers[key] = PadPlayer(context, key)
        }
    }

    private fun getPlayers(minor: Boolean) = if (minor) minorPlayers else majorPlayers

    // Stop all active players except those in the specified sets
    private fun stopOrphanedPlayers(
        keepMajorKeys: Set<MusicalKey> = emptySet(),
        keepMinorKeys: Set<MusicalKey> = emptySet()
    ) {
        majorPlayers.forEach { (k, player) ->
            if (k !in keepMajorKeys && player.isActive()) player.stop()
        }
        minorPlayers.forEach { (k, player) ->
            if (k !in keepMinorKeys && player.isActive()) player.stop()
        }
    }

    private fun startForegroundService(key: MusicalKey, isMinor: Boolean) {
        val intent = Intent(context, AudioService::class.java).apply {
            putExtra(AudioService.EXTRA_KEY_NAME, key.noteName)
            putExtra(AudioService.EXTRA_IS_MINOR, isMinor)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundService() {
        context.stopService(Intent(context, AudioService::class.java))
    }

    fun setFadeInDuration(durationSeconds: Float) {
        val durationMs = (durationSeconds * 1000).toLong()
        _fadeInDurationMs.value = durationMs
        prefs.edit().putLong(KEY_FADE_IN_DURATION, durationMs).apply()
    }

    fun getFadeInDuration(): Float = _fadeInDurationMs.value / 1000f

    fun setFadeOutDuration(durationSeconds: Float) {
        val durationMs = (durationSeconds * 1000).toLong()
        _fadeOutDurationMs.value = durationMs
        prefs.edit().putLong(KEY_FADE_OUT_DURATION, durationMs).apply()
    }

    fun getFadeOutDuration(): Float = _fadeOutDurationMs.value / 1000f

    fun setShowDebugOverlay(show: Boolean) {
        _showDebugOverlay.value = show
        prefs.edit().putBoolean(KEY_SHOW_DEBUG, show).apply()
    }

    fun setStartFromA(startFromA: Boolean) {
        _startFromA.value = startFromA
        prefs.edit().putBoolean(KEY_START_FROM_A, startFromA).apply()
    }

    fun setUseFlats(useFlats: Boolean) {
        _useFlats.value = useFlats
        prefs.edit().putBoolean(KEY_USE_FLATS, useFlats).apply()
    }

    fun getPlaybackInfo(): PlaybackInfo? {
        // Gather all active player states across all keys and modes
        val allPlayerStates = mutableListOf<PlayerState>()

        majorPlayers.forEach { (key, p) ->
            if (p.isActive()) {
                p.getPlayerStates().forEach { state ->
                    val suffix = if (state.label.isNotEmpty()) " ${state.label}" else ""
                    allPlayerStates.add(state.copy(
                        label = "${key.noteName}$suffix"
                    ))
                }
            }
        }
        minorPlayers.forEach { (key, p) ->
            if (p.isActive()) {
                p.getPlayerStates().forEach { state ->
                    val suffix = if (state.label.isNotEmpty()) " ${state.label}" else ""
                    allPlayerStates.add(state.copy(
                        label = "${key.noteName}m$suffix"
                    ))
                }
            }
        }

        // Return null only if no players are active
        if (allPlayerStates.isEmpty()) return null

        // Get duration from any active player
        val anyActivePlayer = majorPlayers.values.find { it.isActive() }
            ?: minorPlayers.values.find { it.isActive() }

        return PlaybackInfo(
            currentPosition = anyActivePlayer?.getCurrentPosition() ?: 0,
            duration = anyActivePlayer?.getDuration() ?: 0,
            isCrossfading = allPlayerStates.any { it.label.contains("(old)") || it.label.contains("(new)") },
            playerStates = allPlayerStates
        )
    }

    fun setMinorMode(minor: Boolean) {
        if (_isMinor.value == minor) return

        val currentPad = _activePad.value
        val wasMinor = _isMinor.value
        _isMinor.value = minor

        // If a pad is playing, crossfade to the same key in the new mode
        if (currentPad != null) {
            currentFadeJob?.cancel()
            // Keep the key in both modes for the crossfade
            stopOrphanedPlayers(setOf(currentPad), setOf(currentPad))
            startForegroundService(currentPad, minor)
            currentFadeJob = scope.launch {
                crossfadeMode(currentPad, wasMinor, minor)
            }
        }
    }

    fun togglePad(key: MusicalKey) {
        val currentPad = _activePad.value
        val minor = _isMinor.value

        currentFadeJob?.cancel()

        if (currentPad == key) {
            // Stopping current pad
            _activePad.value = null
            val keep = setOf(key)
            stopOrphanedPlayers(
                keepMajorKeys = if (!minor) keep else emptySet(),
                keepMinorKeys = if (minor) keep else emptySet()
            )
            currentFadeJob = scope.launch {
                fadeOut(key, minor)
                stopForegroundService()
            }
        } else {
            // Starting or switching to new pad
            _activePad.value = key
            val keep = if (currentPad != null) setOf(currentPad, key) else setOf(key)
            stopOrphanedPlayers(
                keepMajorKeys = if (!minor) keep else emptySet(),
                keepMinorKeys = if (minor) keep else emptySet()
            )
            startForegroundService(key, minor)
            currentFadeJob = scope.launch {
                if (currentPad != null) {
                    crossfade(currentPad, key, minor)
                } else {
                    fadeIn(key, minor)
                }
            }
        }
    }

    private suspend fun fadeIn(key: MusicalKey, minor: Boolean) {
        val player = getPlayers(minor)[key] ?: return
        player.start(_audioPack.value, minor)

        val durationMs = _fadeInDurationMs.value
        val steps = max(1, durationMs / 16)
        val volumeStep = 1f / steps

        repeat(steps.toInt()) {
            if (!coroutineContext.isActive) return
            val currentVolume = (player.getVolume() + volumeStep).coerceAtMost(1f)
            player.setVolume(currentVolume)
            delay(16)
        }
        player.setVolume(1f)
    }

    private suspend fun fadeOut(key: MusicalKey, minor: Boolean) {
        val player = getPlayers(minor)[key] ?: return

        val durationMs = _fadeOutDurationMs.value
        val steps = max(1, durationMs / 16)
        val volumeStep = 1f / steps

        repeat(steps.toInt()) {
            if (!coroutineContext.isActive) return
            val currentVolume = (player.getVolume() - volumeStep).coerceAtLeast(0f)
            player.setVolume(currentVolume)
            delay(16)
        }

        player.stop()
    }

    private suspend fun crossfade(fromKey: MusicalKey, toKey: MusicalKey, minor: Boolean) {
        val players = getPlayers(minor)
        val fromPlayer = players[fromKey] ?: return
        val toPlayer = players[toKey] ?: return

        toPlayer.start(_audioPack.value, minor)

        // Use fade-in duration for crossfades
        val durationMs = _fadeInDurationMs.value
        val steps = max(1, durationMs / 16)

        repeat(steps.toInt()) {
            if (!coroutineContext.isActive) return
            val progress = (it + 1).toFloat() / steps
            fromPlayer.setVolume((1f - progress).coerceAtLeast(0f))
            toPlayer.setVolume(progress.coerceAtMost(1f))
            delay(16)
        }

        fromPlayer.stop()
        toPlayer.setVolume(1f)
    }

    private suspend fun crossfadeMode(key: MusicalKey, fromMinor: Boolean, toMinor: Boolean) {
        val fromPlayer = getPlayers(fromMinor)[key] ?: return
        val toPlayer = getPlayers(toMinor)[key] ?: return

        toPlayer.start(_audioPack.value, toMinor)

        // Use fade-in duration for mode crossfades
        val durationMs = _fadeInDurationMs.value
        val steps = max(1, durationMs / 16)

        repeat(steps.toInt()) {
            if (!coroutineContext.isActive) return
            val progress = (it + 1).toFloat() / steps
            fromPlayer.setVolume((1f - progress).coerceAtLeast(0f))
            toPlayer.setVolume(progress.coerceAtMost(1f))
            delay(16)
        }

        fromPlayer.stop()
        toPlayer.setVolume(1f)
    }

    fun pause() {
        majorPlayers.values.forEach { it.pause() }
        minorPlayers.values.forEach { it.pause() }
    }

    fun resume() {
        majorPlayers.values.forEach { it.resume() }
        minorPlayers.values.forEach { it.resume() }
    }

    fun seekTo(positionMs: Int) {
        val activePad = _activePad.value ?: return
        val minor = _isMinor.value
        val player = getPlayers(minor)[activePad] ?: return
        player.seekTo(positionMs)
    }

    fun cleanup() {
        currentFadeJob?.cancel()
        scope.cancel()
        stopForegroundService()
        majorPlayers.values.forEach { it.cleanup() }
        minorPlayers.values.forEach { it.cleanup() }
    }

    companion object {
        private const val KEY_FADE_IN_DURATION = "fade_in_duration_ms"
        private const val KEY_FADE_OUT_DURATION = "fade_out_duration_ms"
        private const val KEY_SHOW_DEBUG = "show_debug_overlay"
        private const val KEY_START_FROM_A = "start_from_a"
        private const val KEY_USE_FLATS = "use_flats"
    }
}

data class PlaybackInfo(
    val currentPosition: Int,
    val duration: Int,
    val isCrossfading: Boolean = false,
    val playerStates: List<PlayerState> = emptyList()
) {
    val remaining: Int get() = (duration - currentPosition).coerceAtLeast(0)

    fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
