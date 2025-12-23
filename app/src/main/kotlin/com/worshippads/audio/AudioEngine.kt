package com.worshippads.audio

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class AudioEngine(context: Context) {
    private val players = mutableMapOf<MusicalKey, PadPlayer>()
    private val _activePad = MutableStateFlow<MusicalKey?>(null)
    val activePad: StateFlow<MusicalKey?> = _activePad.asStateFlow()

    private val prefs = context.getSharedPreferences("worship_pads_prefs", Context.MODE_PRIVATE)
    private val _fadeDurationMs = MutableStateFlow(prefs.getLong(KEY_FADE_DURATION, 2000L))

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentFadeJob: Job? = null

    init {
        MusicalKey.entries.forEach { key ->
            players[key] = PadPlayer(context, key)
        }
    }

    fun setFadeDuration(durationSeconds: Float) {
        val durationMs = (durationSeconds * 1000).toLong()
        _fadeDurationMs.value = durationMs
        prefs.edit().putLong(KEY_FADE_DURATION, durationMs).apply()
    }

    fun getFadeDuration(): Float = _fadeDurationMs.value / 1000f

    companion object {
        private const val KEY_FADE_DURATION = "fade_duration_ms"
    }

    fun togglePad(key: MusicalKey) {
        val currentPad = _activePad.value

        // Cancel any ongoing fade
        currentFadeJob?.cancel()

        if (currentPad == key) {
            _activePad.value = null
            currentFadeJob = scope.launch {
                fadeOut(key)
            }
        } else {
            _activePad.value = key
            currentFadeJob = scope.launch {
                if (currentPad != null) {
                    crossfade(currentPad, key)
                } else {
                    fadeIn(key)
                }
            }
        }
    }

    private suspend fun fadeIn(key: MusicalKey) {
        val player = players[key] ?: return
        player.start()

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

    private suspend fun fadeOut(key: MusicalKey) {
        val player = players[key] ?: return

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

    private suspend fun crossfade(fromKey: MusicalKey, toKey: MusicalKey) {
        val fromPlayer = players[fromKey] ?: return
        val toPlayer = players[toKey] ?: return

        toPlayer.start()

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
        players.values.forEach { it.pause() }
    }

    fun resume() {
        players.values.forEach { it.resume() }
    }

    fun cleanup() {
        currentFadeJob?.cancel()
        scope.cancel()
        players.values.forEach { it.stop() }
    }
}
