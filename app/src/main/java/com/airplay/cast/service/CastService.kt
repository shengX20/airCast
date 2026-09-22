package com.airplay.cast.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.os.Build
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.airplay.cast.AirPlayApp
import com.airplay.cast.MainActivity
import com.airplay.cast.native.NativeBridge
import kotlin.concurrent.thread

class CastService : Service() {

    companion object {
        const val TAG = "CastService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_AUTH_TYPE = "auth_type"
        const val EXTRA_AIRPLAY2 = "airplay2"
        const val EXTRA_CREDS = "creds"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val ACTION_STOP = "com.airplay.cast.STOP"
        const val ACTION_VOL_UP = "com.airplay.cast.VOL_UP"
        const val ACTION_VOL_DOWN = "com.airplay.cast.VOL_DOWN"
        const val DEFAULT_VOLUME = 40.0
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var savedVolume = -1
    private var wasMuted = false
    private var audioManager: AudioManager? = null
    private var currentVolume = DEFAULT_VOLUME

    private fun flog(msg: String) {
        Log.i(TAG, msg)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopCast(); stopSelf(); return START_NOT_STICKY }
            ACTION_VOL_UP -> {
                currentVolume = (currentVolume + 10).coerceAtMost(100.0)
                NativeBridge.setVolume(currentVolume)
                updateNotification("Casting - ${currentVolume.toInt()}%")
                return START_STICKY
            }
            ACTION_VOL_DOWN -> {
                currentVolume = (currentVolume - 10).coerceAtLeast(0.0)
                NativeBridge.setVolume(currentVolume)
                updateNotification("Casting - ${currentVolume.toInt()}%")
                return START_STICKY
            }
        }

        if (intent == null) { stopSelf(); return START_NOT_STICKY }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }

        val host = intent.getStringExtra(EXTRA_HOST) ?: run { stopSelf(); return START_NOT_STICKY }
        val port = intent.getIntExtra(EXTRA_PORT, 7000)
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: host
        val authType = intent.getIntExtra(EXTRA_AUTH_TYPE, 4)
        val airplay2 = intent.getBooleanExtra(EXTRA_AIRPLAY2, true)
        val creds = intent.getStringExtra(EXTRA_CREDS)
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "AirPlay Cast"

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirPlayCast::cast").apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) { flog("WakeLock failed: $e") }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Save current volume, then mute local playback so audio only comes from HomePod
        try {
            savedVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            wasMuted = audioManager?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            flog("Local audio muted, saved volume=$savedVolume")
        } catch (e: Exception) { flog("Mute failed: $e") }

        startForeground(1, buildNotification("Casting to $deviceName"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, resultData!!)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCast(); stopSelf()
            }
        }, null)

        flog("Connecting to $host:$port ...")
        val ok = NativeBridge.connect(host, port, deviceId, authType, airplay2, creds, deviceName)
        flog("NativeBridge.connect returned: $ok")
        if (!ok) {
            stopCast(); stopSelf(); return START_NOT_STICKY
        }

        currentVolume = DEFAULT_VOLUME
        NativeBridge.setVolume(currentVolume)
        flog("Starting audio capture...")
        startAudioCapture()

        return START_STICKY
    }

    private fun createAudioRecord(): AudioRecord? {
        val proj = mediaProjection ?: return null
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufSize = maxOf(minBuf * 4, 8820 * 4) // 400ms buffer at 44.1kHz stereo

        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        return AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setContext(this@CastService)
                }
            }
            .build()
    }

    private fun startAudioCapture() {
        stopAudioCapture()

        val record = createAudioRecord() ?: run {
            flog("Failed to build AudioRecord: mediaProjection is null")
            return
        }
        audioRecord = record

        try {
            record.startRecording()
            flog("Audio capture started (state=${record.state})")
        } catch (e: Exception) {
            flog("startRecording failed: $e")
            return
        }

        capturing = true
        captureThread = thread(name = "audio-capture") {
            val chunkSize = 3528  // 40ms @ 44.1kHz stereo (44100 * 2 * 0.04)
            val buf = ShortArray(chunkSize)
            var firstRead = true
            try {
                while (capturing) {
                    val rec = audioRecord ?: break
                    val n = try {
                        rec.read(buf, 0, buf.size)
                    } catch (e: Exception) {
                        Log.w(TAG, "AudioRecord read exception: ${e.message}")
                        -1
                    }

                    if (n > 0) {
                        if (firstRead) {
                            flog("First audio read: $n shorts")
                            firstRead = false
                        }
                        NativeBridge.feedAudio(buf, n)
                    } else if (n == 0) {
                        // Buffer empty or playback paused: sleep briefly to avoid 100% CPU busy-loop
                        try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                    } else {
                        // n < 0: AudioRecord read error
                        Log.w(TAG, "AudioRecord read error: $n")
                        if (n == AudioRecord.ERROR_DEAD_OBJECT && capturing) {
                            Log.e(TAG, "Audio server died, recreating AudioRecord...")
                            try {
                                rec.stop()
                                rec.release()
                            } catch (_: Exception) {}
                            try { Thread.sleep(150) } catch (_: InterruptedException) { break }
                            if (capturing) {
                                try {
                                    val newRec = createAudioRecord()
                                    newRec?.startRecording()
                                    audioRecord = newRec
                                } catch (e: Exception) {
                                    Log.e(TAG, "Recovery startRecording failed: $e")
                                    try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                                }
                            }
                        } else {
                            try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                        }
                    }
                }
            } catch (e: Throwable) {
                flog("Capture thread crashed: $e")
            }
            flog("Audio capture thread ended")
        }
    }

    private fun stopAudioCapture() {
        capturing = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { captureThread?.interrupt() } catch (_: Exception) {}
        try { captureThread?.join(500) } catch (_: Exception) {}
        captureThread = null
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, CastService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val volUpIntent = PendingIntent.getService(this, 0,
            Intent(this, CastService::class.java).setAction(ACTION_VOL_UP),
            PendingIntent.FLAG_IMMUTABLE)
        val volDownIntent = PendingIntent.getService(this, 0,
            Intent(this, CastService::class.java).setAction(ACTION_VOL_DOWN),
            PendingIntent.FLAG_IMMUTABLE)
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, AirPlayApp.CHANNEL_CAST)
            .setContentTitle("AirCast")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_rew, "Vol-", volDownIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .addAction(android.R.drawable.ic_media_ff, "Vol+", volUpIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(1, buildNotification(text))
    }

    private fun stopCast() {
        stopAudioCapture()
        try { NativeBridge.disconnect() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && wasMuted) {
                audioManager?.setStreamMute(AudioManager.STREAM_MUSIC, false)
                wasMuted = false
            }
            if (savedVolume >= 0) {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                savedVolume = -1
            }
        } catch (e: Exception) { flog("Restore volume failed: $e") }

        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        stopCast(); super.onDestroy()
    }
}
