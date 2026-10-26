package io.embodit.moonphases.platform

import android.media.AudioManager
import android.media.ToneGenerator

/** The one sound of the app: a short beep when a phase is found or an answer is right. No assets. */
class Chime {
    private var generator: ToneGenerator? = null

    fun ding() {
        try {
            val g = generator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 70).also { generator = it }
            g.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (t: Throwable) {
            MoonLog.w("CHIME failed: $t")
        }
    }

    fun release() {
        runCatching { generator?.release() }
        generator = null
    }
}
