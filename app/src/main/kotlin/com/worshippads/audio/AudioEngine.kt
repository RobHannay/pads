package com.worshippads.audio

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.math.max

private data class PlayerId(
    val key: MusicalKey,
    val isMinor: Boolean,
    val pack: AudioPack,
    val octave: Int,
)

class AudioEngine(private val context: Context) {
    private val padPlayers = mutableMapOf<PlayerId, PadPlayer>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    // Broadcast receiver for stop action from notification
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioService.ACTION_STOP_PLAYBACK) {
                stopPlayback()
            }
        }
    }

    private val _activePad = MutableStateFlow<MusicalKey?>(null)
    val activePad: StateFlow<MusicalKey?> = _activePad.asStateFlow()

    private val _isMinor = MutableStateFlow(false)
    val isMinor: StateFlow<Boolean> = _isMinor.asStateFlow()

    private val prefs = context.getSharedPreferences("worship_pads_prefs", Context.MODE_PRIVATE)

    private val _audioPack = MutableStateFlow(
        runCatching { AudioPack.valueOf(prefs.getString(KEY_AUDIO_PACK, AudioPack.BRIDGE.name)!!) }
            .getOrDefault(AudioPack.BRIDGE)
    )
    val audioPack: StateFlow<AudioPack> = _audioPack.asStateFlow()

    // Live pitch-shift is done via rubberband. On underpowered devices the real-time
    // budget may not be met — in that case we quietly hide the octave control
    // rather than risk playback under-runs.
    val supportsOctave: Boolean = RubberbandSmokeTest.run().isUsable

    private val _octave = MutableStateFlow(
        if (supportsOctave) prefs.getInt(KEY_OCTAVE, 0) else 0
    )
    val octave: StateFlow<Int> = _octave.asStateFlow()

    // EQ: 4 bands + a shared config object every PadPlayer's processor reads from.
    private val eqConfig = EqConfig().apply {
        bassDb = prefs.getFloat(KEY_EQ_BASS, 0f)
        presenceDb = prefs.getFloat(KEY_EQ_PRESENCE, 0f)
        trebleDb = prefs.getFloat(KEY_EQ_TREBLE, 0f)
        lowCutHz = prefs.getInt(KEY_EQ_LOW_CUT, 0)
    }
    private val _eqBass = MutableStateFlow(eqConfig.bassDb)
    val eqBass: StateFlow<Float> = _eqBass.asStateFlow()
    private val _eqPresence = MutableStateFlow(eqConfig.presenceDb)
    val eqPresence: StateFlow<Float> = _eqPresence.asStateFlow()
    private val _eqTreble = MutableStateFlow(eqConfig.trebleDb)
    val eqTreble: StateFlow<Float> = _eqTreble.asStateFlow()
    private val _eqLowCut = MutableStateFlow(eqConfig.lowCutHz)
    val eqLowCut: StateFlow<Int> = _eqLowCut.asStateFlow()

    // Do Not Disturb
    private val _enableDnd = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_DND, false))
    val enableDnd: StateFlow<Boolean> = _enableDnd.asStateFlow()
    private var previousDndFilter: Int? = null
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                stopReceiver,
                IntentFilter(AudioService.ACTION_STOP_PLAYBACK),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                stopReceiver,
                IntentFilter(AudioService.ACTION_STOP_PLAYBACK)
            )
        }
    }

    private fun player(key: MusicalKey, minor: Boolean, pack: AudioPack, octave: Int): PadPlayer =
        padPlayers.getOrPut(PlayerId(key, minor, pack, octave)) { PadPlayer(context, key, eqConfig) }

    // Stop all active players except those in the keep set
    private fun stopOrphanedPlayers(keep: Set<PlayerId> = emptySet()) {
        padPlayers.forEach { (id, p) ->
            if (id !in keep && p.isActive()) p.stop()
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
        val allPlayerStates = mutableListOf<PlayerState>()

        padPlayers.forEach { (id, p) ->
            if (p.isActive()) {
                val modeSuffix = if (id.isMinor) "m" else ""
                val octaveSuffix = when {
                    id.octave < 0 -> "↓"
                    id.octave > 0 -> "↑"
                    else -> ""
                }
                p.getPlayerStates().forEach { state ->
                    val xfadeSuffix = if (state.label.isNotEmpty()) " ${state.label}" else ""
                    allPlayerStates.add(state.copy(
                        label = "${id.key.noteName}$modeSuffix$octaveSuffix$xfadeSuffix"
                    ))
                }
            }
        }

        if (allPlayerStates.isEmpty()) return null

        val anyActivePlayer = padPlayers.values.firstOrNull { it.isActive() }

        return PlaybackInfo(
            currentPosition = anyActivePlayer?.getCurrentPosition() ?: 0,
            duration = anyActivePlayer?.getDuration() ?: 0,
            isCrossfading = allPlayerStates.any { it.label.contains("(old)") || it.label.contains("(new)") },
            playerStates = allPlayerStates
        )
    }

    fun setAudioPack(pack: AudioPack) {
        if (_audioPack.value == pack) return

        val previousPack = _audioPack.value
        _audioPack.value = pack
        prefs.edit().putString(KEY_AUDIO_PACK, pack.name).apply()

        val wasMinor = _isMinor.value
        val newMinor = wasMinor && pack.hasMinor
        if (wasMinor != newMinor) {
            _isMinor.value = newMinor
        }

        val currentPad = _activePad.value ?: return
        val octave = _octave.value
        val fromId = PlayerId(currentPad, wasMinor, previousPack, octave)
        val toId = PlayerId(currentPad, newMinor, pack, octave)
        transitionTo(fromId, toId)
    }

    fun setMinorMode(minor: Boolean) {
        if (_isMinor.value == minor) return

        val wasMinor = _isMinor.value
        _isMinor.value = minor

        val currentPad = _activePad.value ?: return
        val pack = _audioPack.value
        val octave = _octave.value
        val fromId = PlayerId(currentPad, wasMinor, pack, octave)
        val toId = PlayerId(currentPad, minor, pack, octave)
        transitionTo(fromId, toId)
    }

    fun setEqBass(db: Float) {
        val clamped = db.coerceIn(-6f, 6f)
        if (_eqBass.value == clamped) return
        _eqBass.value = clamped
        eqConfig.bassDb = clamped
        prefs.edit().putFloat(KEY_EQ_BASS, clamped).apply()
    }

    fun setEqPresence(db: Float) {
        val clamped = db.coerceIn(-6f, 6f)
        if (_eqPresence.value == clamped) return
        _eqPresence.value = clamped
        eqConfig.presenceDb = clamped
        prefs.edit().putFloat(KEY_EQ_PRESENCE, clamped).apply()
    }

    fun setEqTreble(db: Float) {
        val clamped = db.coerceIn(-6f, 6f)
        if (_eqTreble.value == clamped) return
        _eqTreble.value = clamped
        eqConfig.trebleDb = clamped
        prefs.edit().putFloat(KEY_EQ_TREBLE, clamped).apply()
    }

    fun setEqLowCut(hz: Int) {
        if (_eqLowCut.value == hz) return
        _eqLowCut.value = hz
        eqConfig.lowCutHz = hz
        prefs.edit().putInt(KEY_EQ_LOW_CUT, hz).apply()
    }

    fun applyEqPreset(preset: EqPreset) {
        setEqBass(preset.bassDb)
        setEqPresence(preset.presenceDb)
        setEqTreble(preset.trebleDb)
        setEqLowCut(preset.lowCutHz)
    }

    /**
     * Shift playback pitch by integer octaves (−1 / 0 / +1). A new PadPlayer
     * instance is spun up at the target octave and we crossfade between old
     * and new, exactly like changing pack or mode. Consistent UX with the
     * other musical transitions.
     */
    fun setOctave(octave: Int) {
        if (!supportsOctave) return
        val clamped = octave.coerceIn(-1, 1)
        if (_octave.value == clamped) return

        val previous = _octave.value
        _octave.value = clamped
        prefs.edit().putInt(KEY_OCTAVE, clamped).apply()

        val currentPad = _activePad.value ?: return
        val minor = _isMinor.value
        val pack = _audioPack.value
        val fromId = PlayerId(currentPad, minor, pack, previous)
        val toId = PlayerId(currentPad, minor, pack, clamped)
        transitionTo(fromId, toId)
    }

    fun togglePad(key: MusicalKey) {
        val currentPad = _activePad.value
        val minor = _isMinor.value
        val pack = _audioPack.value
        val octave = _octave.value

        currentFadeJob?.cancel()

        if (currentPad == key) {
            // Stopping current pad
            _activePad.value = null
            val keepId = PlayerId(key, minor, pack, octave)
            stopOrphanedPlayers(setOf(keepId))
            currentFadeJob = scope.launch {
                fadeOut(keepId)
                stopForegroundService()
                restoreDoNotDisturb()
            }
        } else {
            _activePad.value = key
            val fromId = currentPad?.let { PlayerId(it, minor, pack, octave) }
            val toId = PlayerId(key, minor, pack, octave)
            val keep = setOfNotNull(fromId, toId)
            stopOrphanedPlayers(keep)
            startForegroundService(key, minor)
            enableDoNotDisturb()
            currentFadeJob = scope.launch {
                if (fromId != null) {
                    crossfadePlayers(fromId, toId)
                } else {
                    fadeIn(toId)
                }
            }
        }
    }

    private fun transitionTo(fromId: PlayerId, toId: PlayerId) {
        currentFadeJob?.cancel()
        stopOrphanedPlayers(setOf(fromId, toId))
        startForegroundService(toId.key, toId.isMinor)
        currentFadeJob = scope.launch {
            crossfadePlayers(fromId, toId)
        }
    }

    private suspend fun crossfadePlayers(fromId: PlayerId, toId: PlayerId) {
        val fromPlayer = player(fromId.key, fromId.isMinor, fromId.pack, fromId.octave)
        val toPlayer = player(toId.key, toId.isMinor, toId.pack, toId.octave)

        toPlayer.start(toId.pack, toId.isMinor, toId.octave)

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

    private suspend fun fadeIn(id: PlayerId) {
        val p = player(id.key, id.isMinor, id.pack, id.octave)
        p.start(id.pack, id.isMinor, id.octave)

        val durationMs = _fadeInDurationMs.value
        val steps = max(1, durationMs / 16)
        val volumeStep = 1f / steps

        repeat(steps.toInt()) {
            if (!coroutineContext.isActive) return
            val currentVolume = (p.getVolume() + volumeStep).coerceAtMost(1f)
            p.setVolume(currentVolume)
            delay(16)
        }
        p.setVolume(1f)
    }

    private suspend fun fadeOut(id: PlayerId) {
        val p = player(id.key, id.isMinor, id.pack, id.octave)

        val durationMs = _fadeOutDurationMs.value
        val steps = max(1, durationMs / 16)
        val volumeStep = 1f / steps

        repeat(steps.toInt()) {
            if (!coroutineContext.isActive) return
            val currentVolume = (p.getVolume() - volumeStep).coerceAtLeast(0f)
            p.setVolume(currentVolume)
            delay(16)
        }

        p.stop()
    }

    fun pause() {
        padPlayers.values.forEach { it.pause() }
    }

    fun resume() {
        padPlayers.values.forEach { it.resume() }
    }

    fun seekTo(positionMs: Int) {
        val activePad = _activePad.value ?: return
        val id = PlayerId(activePad, _isMinor.value, _audioPack.value, _octave.value)
        padPlayers[id]?.seekTo(positionMs)
    }

    fun cleanup() {
        currentFadeJob?.cancel()
        scope.cancel()
        stopForegroundService()
        padPlayers.values.forEach { it.cleanup() }
        try {
            context.unregisterReceiver(stopReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    /** Stop playback with fade out (called from notification stop button) */
    fun stopPlayback() {
        val currentPad = _activePad.value ?: return
        togglePad(currentPad) // This triggers fade out
    }

    fun setEnableDnd(enable: Boolean) {
        _enableDnd.value = enable
        prefs.edit().putBoolean(KEY_ENABLE_DND, enable).apply()
    }

    fun isDndAccessGranted(): Boolean {
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    private fun enableDoNotDisturb() {
        if (!_enableDnd.value || !isDndAccessGranted()) return

        // Store previous state only if we haven't already (avoid overwriting during crossfades)
        if (previousDndFilter == null) {
            previousDndFilter = notificationManager?.currentInterruptionFilter
        }

        notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
    }

    private fun restoreDoNotDisturb() {
        if (!isDndAccessGranted()) return

        val previous = previousDndFilter
        if (previous != null) {
            notificationManager?.setInterruptionFilter(previous)
            previousDndFilter = null
        }
    }

    companion object {
        private const val KEY_FADE_IN_DURATION = "fade_in_duration_ms"
        private const val KEY_FADE_OUT_DURATION = "fade_out_duration_ms"
        private const val KEY_SHOW_DEBUG = "show_debug_overlay"
        private const val KEY_START_FROM_A = "start_from_a"
        private const val KEY_USE_FLATS = "use_flats"
        private const val KEY_ENABLE_DND = "enable_dnd"
        private const val KEY_AUDIO_PACK = "audio_pack"
        private const val KEY_OCTAVE = "octave"
        private const val KEY_EQ_BASS = "eq_bass_db"
        private const val KEY_EQ_PRESENCE = "eq_presence_db"
        private const val KEY_EQ_TREBLE = "eq_treble_db"
        private const val KEY_EQ_LOW_CUT = "eq_low_cut_hz"
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
