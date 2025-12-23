package com.worshippads.audio

enum class AudioPack(
    val displayName: String,
    val resourcePrefix: String
) {
    BRIDGE("The Bridge", "bridge");

    fun getResourceName(key: MusicalKey, isMinor: Boolean): String {
        val keyPart = if (isMinor) key.minorResource else key.majorResource
        return "${resourcePrefix}_$keyPart"
    }
}
