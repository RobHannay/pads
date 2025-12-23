package com.worshippads.audio

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class AudioEngine(context: Context) {
    private val majorPlayers = mutableMapOf<MusicalKey, PadPlayer>()
    private val minorPlayers = mutableMapOf<MusicalKey, PadPlayer>()

    private val _activePad = MutableStateFlow<MusicalKey?>(null)
    val activePad: StateFlow<MusicalKey?> = _activePad.asStateFlow()

    private val _isMinor = MutableStateFlow(false)
    val isMinor: StateFlow<Boolean> = _isMinor.asStateFlow()

    private val prefs = context.getSharedPreferences("worship_pads_prefs", Context.MODE_PRIVATE)
    private val _fadeDurationMs = MutableStateFlow(prefs.getLong(KEY_FADE_DURATION, 2000L))

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentFadeJob: Job? = null

    init {
        MusicalKey.entries.forEach { key ->
            majorPlayers[key] = PadPlayer(context, key)
            minorPlayers[key] = PadPlayer(context, key)
        }
    }

    private fun getPlayers(minor: Boolean) = if (minor) minorPlayers else majorPlayers

    fun setFadeDuration(durationSeconds: Float) {
        val durationMs = (durationSeconds * 1000).toLong()
        _fadeDurationMs.value = durationMs
        prefs.edit().putLong(KEY_FADE_DURATION, durationMs).apply()
    }

    fun getFadeDuration(): Float = _fadeDurationMs.value / 1000f

    fun setMinorMode(minor: Boolean) {
        if (_isMinor.value == minor) return

        val currentPad = _activePad.value
        val wasMinor = _isMinor.value
        _isMinor.value = minor

        // If a pad is playing, crossfade to the same key in the new mode
        if (currentPad != null) {
            currentFadeJob?.cancel()
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
            _activePad.value = null
            currentFadeJob = scope.launch {
                fadeOut(key, minor)
            }
        } else {
            _activePad.value = key
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
        player.start(minor)

        val fadeDurationMs = _fadeDurationMs.value
        val steps = max(1, fadeDurationMs / 16)
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

        val fadeDurationMs = _fadeDurationMs.value
        val steps = max(1, fadeDurationMs / 16)
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

        toPlayer.start(minor)

        val fadeDurationMs = _fadeDurationMs.value
        val steps = max(1, fadeDurationMs / 16)

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

        toPlayer.start(toMinor)

        val fadeDurationMs = _fadeDurationMs.value
        val steps = max(1, fadeDurationMs / 16)

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

    fun cleanup() {
        currentFadeJob?.cancel()
        scope.cancel()
        majorPlayers.values.forEach { it.stop() }
        minorPlayers.values.forEach { it.stop() }
    }

    companion object {
        private const val KEY_FADE_DURATION = "fade_duration_ms"
    }
}
