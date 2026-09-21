package com.airplay.cast.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class DeviceDiscovery(private val context: Context) {

    data class AirPlayDevice(
        val name: String,
        val host: String,
        val port: Int,
        val deviceId: String,
        val authType: Int,
        val isAirPlay2: Boolean,
        val model: String = ""
    )

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolvedDevices = mutableMapOf<String, AirPlayDevice>()

    var onDeviceFound: ((AirPlayDevice) -> Unit)? = null
    var onDeviceLost: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start() {
        if (nsdManager != null) return
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started: $serviceType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Each resolve needs its own ResolveListener instance
                val listener = object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val port = info.port
                        val name = info.serviceName
                        val txt = info.attributes.mapValues { it.value.toString(Charsets.UTF_8) }
                        Log.i(TAG, "Resolved: $name @ $host:$port TXT: $txt")
                        val (authType, isAp2, model) = parseTxtRecords(txt)
                        Log.i(TAG, "  -> model=$model authType=$authType ap2=$isAp2")

                        val device = AirPlayDevice(
                            name = name, host = host, port = port,
                            deviceId = name, authType = authType,
                            isAirPlay2 = isAp2, model = model
                        )
                        resolvedDevices[name] = device
                        onDeviceFound?.invoke(device)
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        Log.w(TAG, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                    }
                }
                try {
                    nsdManager?.resolveService(service, listener)
                } catch (e: Exception) {
                    Log.w(TAG, "resolveService failed", e)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                resolvedDevices.remove(service.serviceName)
                onDeviceLost?.invoke(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError?.invoke("Discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError?.invoke("Stop failed: $errorCode")
            }
        }

        nsdManager?.discoverServices("_raop._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun parseTxtRecords(txt: Map<String, String>): Triple<Int, Boolean, String> {
        var authType = 0
        var isAp2 = false
        var model = ""

        txt["pw"]?.let { if (it == "true") authType = 1 }
        txt["am"]?.let { model = it }

        val sfStr = txt["sf"] ?: txt["features"]
        if (sfStr != null) {
            val sf = parseHex(sfStr)
            if (sf and 0x1L > 0L) isAp2 = true
            if (sf and 0x20L > 0L || sf and 0x1L > 0L) isAp2 = true
        }

        authType = when {
            model.startsWith("AudioAccessory") -> { isAp2 = true; 4 }
            model.startsWith("AppleTV") -> { isAp2 = true; 5 }
            model.startsWith("AirPort") -> 2
            txt["pw"] == "true" -> 1
            isAp2 -> 4
            else -> 0
        }
        return Triple(authType, isAp2, model)
    }

    private fun parseHex(s: String): Long =
        s.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L

    fun stop() {
        discoveryListener?.let {
            try { nsdManager?.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
        resolvedDevices.clear()
        nsdManager = null
    }

    fun getDiscovered(): List<AirPlayDevice> = resolvedDevices.values.toList()

    companion object { private const val TAG = "DeviceDiscovery" }
}
