package com.opuscube.munshi.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// The same two-note station ident as the web Soundbox (587.33 Hz then 880 Hz), and a soft tick.
object Chime {
    private const val RATE = 44_100

    private fun tone(notes: List<Pair<Double, Double>>, lengthS: Double, gain: Double): ShortArray {
        val n = (RATE * lengthS).toInt()
        val out = ShortArray(n)
        for ((freq, start) in notes) {
            val s0 = (start * RATE).toInt()
            for (i in s0 until n) {
                val t = (i - s0).toDouble() / RATE
                val env = if (t < 0.03) t / 0.03 else exp(-(t - 0.03) * 7)
                out[i] = (out[i] + sin(2 * PI * freq * t) * env * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return out
    }

    private fun play(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.release() }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }

    fun payment() { runCatching { play(tone(listOf(587.33 to 0.0, 880.0 to 0.22), 0.9, 0.18)) } }
    fun tick() { runCatching { play(tone(listOf(1200.0 to 0.0), 0.08, 0.08)) } }
}
