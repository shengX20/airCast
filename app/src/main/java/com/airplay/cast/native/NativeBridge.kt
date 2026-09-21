package com.airplay.cast.native

import android.util.Log

/**
 * JNI bridge to the C++ RAOP/AirPlay2 sender.
 * All methods are thread-safe; connect/disconnect should be called from a single thread.
 */
object NativeBridge {

    private const val TAG = "AirPlayCast-Native"

    interface Callbacks {
        fun onLaunched(ok: Boolean, error: String?)
        fun onClosed()
        fun onPinRequired(deviceName: String)
        fun onCredentialsObtained(deviceId: String, credsJson: String)
    }

    init {
        System.loadLibrary("airplay_jni")
    }

    @Volatile
    private var callbacks: Callbacks? = null

    fun init(cb: Callbacks) {
        callbacks = cb
        nativeInit(cb)
    }

    /**
     * @param authType 0=None, 1=Password, 2=AuthSetup, 4=HapTransient(HomePod), 5=HapPin(AppleTV)
     */
    fun connect(
        ip: String, port: Int,
        deviceId: String,
        authType: Int,
        airplay2: Boolean,
        credsJson: String?,
        deviceName: String
    ): Boolean {
        return nativeConnect(ip, port, deviceId, authType, airplay2, credsJson, deviceName)
    }

    fun disconnect() = nativeDisconnect()
    fun feedAudio(pcm: ShortArray, samples: Int): Int = nativeFeedAudio(pcm, samples)
    fun submitPin(pin: String) = nativeSubmitPin(pin)
    fun setVolume(pct: Double) = nativeSetVolume(pct)
    fun isActive(): Boolean = nativeIsActive()
    fun setNowPlaying(title: String, artist: String, album: String) =
        nativeSetNowPlaying(title, artist, album)

    // --- JNI methods ---
    @JvmStatic private external fun nativeInit(callbacks: Callbacks)
    @JvmStatic private external fun nativeConnect(
        ip: String, port: Int, deviceId: String,
        authType: Int, airplay2: Boolean,
        credsJson: String?, deviceName: String
    ): Boolean
    @JvmStatic private external fun nativeDisconnect()
    @JvmStatic private external fun nativeFeedAudio(pcm: ShortArray, samples: Int): Int
    @JvmStatic private external fun nativeSubmitPin(pin: String)
    @JvmStatic private external fun nativeSetVolume(pct: Double)
    @JvmStatic private external fun nativeIsActive(): Boolean
    @JvmStatic private external fun nativeSetNowPlaying(title: String, artist: String, album: String)
}
