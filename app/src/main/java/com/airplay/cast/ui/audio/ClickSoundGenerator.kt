package com.airplay.cast.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * Generates and plays the vintage radio "tick/click" sound effect.
 * Fully offline, zero dependencies. Synthesizes an 8ms noise pulse with 3kHz bandpass
 * filter and exponential decay envelope, matching the design prototype's WebAudio clickTick().
 */
class ClickSoundGenerator(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        try {
            initAudioTrack()
        } catch (e: Exception) {
            Log.w("ClickSound", "Failed to init click sound: ${e.message}")
        }
    }

    private fun initAudioTrack() {
        val sampleRate = 44100
        val totalDurationMs = 35
        val totalSamples = (sampleRate * totalDurationMs / 1000)
        val noiseDurationSamples = (sampleRate * 0.008).toInt()

        // 1. Generate noise pulse with quadratic decay (first 8ms)
        val raw = DoubleArray(totalSamples)
        val rnd = Random(42)
        for (i in 0 until totalSamples) {
            if (i < noiseDurationSamples) {
                val decay = (1.0 - i.toDouble() / noiseDurationSamples).pow(2.0)
                raw[i] = (rnd.nextDouble() * 2.0 - 1.0) * decay
            } else {
                raw[i] = 0.0
            }
        }

        // 2. Biquad Bandpass Filter at 3000 Hz, Q = 1.2
        val f0 = 3000.0
        val q = 1.2
        val w0 = 2.0 * Math.PI * f0 / sampleRate
        val alpha = sin(w0) / (2.0 * q)

        val b0 = alpha
        val b1 = 0.0
        val b2 = -alpha
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cos(w0)
        val a2 = 1.0 - alpha

        val filtered = DoubleArray(totalSamples)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (i in 0 until totalSamples) {
            val x0 = raw[i]
            val y0 = (b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0

            // 3. Exponential gain ramp down from 0.15 to 0.001
            val t = i.toDouble() / sampleRate
            val gain = 0.15 * exp(-t / 0.006)
            filtered[i] = y0 * gain
        }

        // 4. Convert to 16-bit PCM
        val pcm = ShortArray(totalSamples)
        var maxVal = 0.0001
        for (v in filtered) {
            if (abs(v) > maxVal) maxVal = abs(v)
        }
        val scale = 0.75 * Short.MAX_VALUE / maxVal
        for (i in 0 until totalSamples) {
            pcm[i] = (filtered[i] * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufferSize = totalSamples * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, totalSamples)
        audioTrack = track
    }

    fun playClick(vibrate: Boolean = true) {
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.reloadStaticData()
                it.play()
            }
        } catch (_: Exception) {}

        if (vibrate) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(8)
                }
            } catch (_: Exception) {}
        }
    }

    fun release() {
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
    }
}
