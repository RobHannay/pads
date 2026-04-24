package com.worshippads.audio

/**
 * Shared mutable EQ state. Mutated by [AudioEngine] when the user moves a
 * slider; read by every live [EqAudioProcessor] on each audio block. A single
 * instance is owned by the engine and handed to every PadPlayer.
 *
 * `bassDb` / `presenceDb` / `trebleDb` are in decibels (−6..+6).
 * `lowCutHz` is a high-pass cutoff in hertz; 0 means off.
 */
class EqConfig {
    @Volatile var bassDb: Float = 0f
    @Volatile var presenceDb: Float = 0f
    @Volatile var trebleDb: Float = 0f
    @Volatile var lowCutHz: Int = 0

    fun applyPreset(preset: EqPreset) {
        bassDb = preset.bassDb
        presenceDb = preset.presenceDb
        trebleDb = preset.trebleDb
        lowCutHz = preset.lowCutHz
    }

    /** True when this EQ is a no-op and the processor can bypass. */
    val isFlat: Boolean
        get() = bassDb == 0f && presenceDb == 0f && trebleDb == 0f && lowCutHz == 0
}

/**
 * Named slider configurations. The EQ screen shows these as chips; tapping
 * one sets all four sliders. The chip is highlighted whenever the current
 * slider values match it exactly (otherwise the UI reads "Custom").
 */
enum class EqPreset(
    val label: String,
    val blurb: String,
    val bassDb: Float,
    val presenceDb: Float,
    val trebleDb: Float,
    val lowCutHz: Int,
) {
    FLAT("Flat", "No change", 0f, 0f, 0f, 0),
    WARM("Warm", "Cosy, darker — good under a lead vocal", 1f, 0f, -3f, 0),
    BRIGHT("Bright", "Airy, sits on top of a mix", 0f, 0f, 3f, 0),
    UNDER_VOCAL("Under vocal", "Carves space for vocal presence", 0f, -4f, 0f, 0),
    SMALL_SPEAKER("Small speaker", "Tight for phone / laptop speakers", 0f, 0f, -2f, 120);

    companion object {
        /** Return the preset whose values match the given configuration, or null. */
        fun match(bassDb: Float, presenceDb: Float, trebleDb: Float, lowCutHz: Int): EqPreset? =
            entries.firstOrNull {
                it.bassDb == bassDb &&
                    it.presenceDb == presenceDb &&
                    it.trebleDb == trebleDb &&
                    it.lowCutHz == lowCutHz
            }
    }
}
