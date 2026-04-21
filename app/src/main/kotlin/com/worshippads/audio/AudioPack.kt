package com.worshippads.audio

enum class AudioPack(
    val displayName: String,
    val resourcePrefix: String,
    val hasMinor: Boolean
) {
    BRIDGE("The Bridge", "bridge", hasMinor = true),
    GUITAR("Guitar Pads", "guitar", hasMinor = false);

    fun getResourceName(key: MusicalKey, isMinor: Boolean, octave: Int = 0): String {
        val useMinor = isMinor && hasMinor
        val keyPart = if (useMinor) key.minorResource else key.majorResource
        val octavePart = when {
            octave < 0 -> "_low"
            octave > 0 -> "_high"
            else -> ""
        }
        return "${resourcePrefix}_${keyPart}${octavePart}"
    }
}
