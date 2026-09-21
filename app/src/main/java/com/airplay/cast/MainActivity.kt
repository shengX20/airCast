package com.airplay.cast

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.airplay.cast.discovery.DeviceDiscovery
import com.airplay.cast.native.NativeBridge
import com.airplay.cast.service.CastService
import com.airplay.cast.ui.audio.ClickSoundGenerator
import com.airplay.cast.ui.components.DeviceGlyph
import com.airplay.cast.ui.components.DeviceGlyphData
import com.airplay.cast.ui.components.EmptyRadarState
import com.airplay.cast.ui.components.NeumorphicCircleButton
import com.airplay.cast.ui.components.NeumorphicPillButton
import com.airplay.cast.ui.components.ScanningSkeletonView
import com.airplay.cast.ui.components.VintageKnobSheet
import com.airplay.cast.ui.components.neumorphic
import com.airplay.cast.ui.components.NeumorphShape
import com.airplay.cast.ui.theme.AirCastTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), NativeBridge.Callbacks {

    private lateinit var discovery: DeviceDiscovery
    private var pendingDevice: DeviceDiscovery.AirPlayDevice? = null
    private var clickSound: ClickSoundGenerator? = null

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val device = pendingDevice ?: return@registerForActivityResult
            val creds = getSharedPreferences("airplay", MODE_PRIVATE)
                .getString("creds_${device.deviceId}", null)
            val intent = Intent(this, CastService::class.java).apply {
                putExtra(CastService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CastService.EXTRA_RESULT_DATA, result.data)
                putExtra(CastService.EXTRA_HOST, device.host)
                putExtra(CastService.EXTRA_PORT, device.port)
                putExtra(CastService.EXTRA_DEVICE_ID, device.deviceId)
                putExtra(CastService.EXTRA_AUTH_TYPE, device.authType)
                putExtra(CastService.EXTRA_AIRPLAY2, device.isAirPlay2)
                putExtra(CastService.EXTRA_CREDS, creds)
                putExtra(CastService.EXTRA_DEVICE_NAME, "AirCast")
            }
            ContextCompat.startForegroundService(this, intent)
            _isCasting.value = true
            _connectedDevice.value = device.name
            _connectedModel.value = device.model
            _connectingName.value = ""
            getSharedPreferences("airplay", MODE_PRIVATE)
                .edit().putString("last_device", device.name)
                .putString("last_model", device.model).apply()
        } else {
            _connectingName.value = ""
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // UI state
    private val _devices = mutableStateListOf<DeviceDiscovery.AirPlayDevice>()
    private val _isCasting = mutableStateOf(false)
    private val _connectedDevice = mutableStateOf("")
    private val _connectedModel = mutableStateOf("")
    private val _statusMessage = mutableStateOf("")
    private val _showPinDialog = mutableStateOf(false)
    private val _pinText = mutableStateOf("")
    private val _scanning = mutableStateOf(false)
    private val _connectingName = mutableStateOf("")
    private val _volume = mutableDoubleStateOf(42.0)
    private val _showVolumeKnob = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        clickSound = ClickSoundGenerator(this)
        NativeBridge.init(this)

        discovery = DeviceDiscovery(this)
        discovery.onDeviceFound = { device ->
            runOnUiThread {
                val idx = _devices.indexOfFirst { it.name == device.name }
                if (idx >= 0) _devices[idx] = device else _devices.add(device)
                _scanning.value = false
            }
        }
        discovery.onDeviceLost = { name ->
            runOnUiThread { _devices.removeAll { it.name == name } }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {}
        }

        setContent {
            AirCastAppTheme {
                Surface(Modifier.fillMaxSize(), color = AirCastTheme.BgBase) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (NativeBridge.isActive()) {
            val prefs = getSharedPreferences("airplay", MODE_PRIVATE)
            _isCasting.value = true
            _connectedDevice.value = prefs.getString("last_device", "") ?: ""
            _connectedModel.value = prefs.getString("last_model", "") ?: ""
        }
        triggerScan()
    }

    override fun onPause() {
        super.onPause()
        if (!_isCasting.value) discovery.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        clickSound?.release()
    }

    override fun onLaunched(ok: Boolean, error: String?) {
        runOnUiThread {
            _statusMessage.value = if (ok) "" else "Failed: $error"
            _connectingName.value = ""
        }
    }

    override fun onClosed() {
        runOnUiThread {
            _isCasting.value = false
            _connectedDevice.value = ""
            _connectedModel.value = ""
            _statusMessage.value = ""
            getSharedPreferences("airplay", MODE_PRIVATE)
                .edit().remove("last_device").remove("last_model").apply()
            startService(Intent(this, CastService::class.java).setAction(CastService.ACTION_STOP))
        }
    }

    override fun onPinRequired(deviceName: String) {
        runOnUiThread { _showPinDialog.value = true }
    }

    override fun onCredentialsObtained(deviceId: String, credsJson: String) {
        getSharedPreferences("airplay", MODE_PRIVATE)
            .edit().putString("creds_$deviceId", credsJson).apply()
    }

    private fun triggerScan() {
        _scanning.value = true
        discovery.start()
    }

    @Composable
    private fun AirCastAppTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = AirCastTheme.Accent,
                onPrimary = Color.Black,
                background = AirCastTheme.BgBase,
                onBackground = AirCastTheme.TextPrimary,
                surface = AirCastTheme.BgSurface,
                onSurface = AirCastTheme.TextPrimary,
                surfaceVariant = AirCastTheme.BgElevated,
                onSurfaceVariant = AirCastTheme.TextSecondary
            )
        ) {
            content()
        }
    }

    @Composable
    private fun MainScreen() {
        val isScanning = _scanning.value

        // Spinning animation for refresh button when scanning (900ms linear)
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
        val spinAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin_angle"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Appbar: "AirCast" (left) + Refresh Neumorphic Button (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AirCast",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirCastTheme.TextPrimary
                    )

                    NeumorphicCircleButton(
                        onClick = {
                            if (!isScanning) {
                                clickSound?.playClick(vibrate = false)
                                triggerScan()
                            }
                        },
                        size = 44.dp,
                        enabled = !isScanning
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新设备列表",
                            tint = if (isScanning) AirCastTheme.Accent else AirCastTheme.TextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (isScanning) spinAngle else 0f)
                        )
                    }
                }

                // Currently Casting Card (Sticky top feel when casting)
                if (_isCasting.value) {
                    NowPlayingCard(
                        deviceName = _connectedDevice.value,
                        model = _connectedModel.value,
                        volume = _volume.value,
                        onStepVolume = { delta ->
                            val newVol = (_volume.value + delta).coerceIn(0.0, 100.0)
                            _volume.value = newVol
                            NativeBridge.setVolume(newVol)
                            clickSound?.playClick(vibrate = true)
                        },
                        onOpenKnob = {
                            _showVolumeKnob.value = true
                        },
                        onDisconnect = {
                            clickSound?.playClick(vibrate = true)
                            stopCasting()
                        }
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // Section Header: "附近的设备"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "附近的设备",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AirCastTheme.TextTertiary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Device List / Scanning Skeletons / Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isScanning && _devices.isEmpty() -> {
                            ScanningSkeletonView(
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                        _devices.isEmpty() -> {
                            EmptyRadarState(
                                onRetry = {
                                    clickSound?.playClick(vibrate = false)
                                    triggerScan()
                                }
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // If scanning while having existing devices, show scanning tip at top
                                if (isScanning) {
                                    item {
                                        Row(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = AirCastTheme.Accent,
                                                strokeWidth = 2.dp,
                                                trackColor = AirCastTheme.Border
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "正在扫描附近的 AirPlay 设备…",
                                                fontSize = 13.sp,
                                                color = AirCastTheme.TextSecondary
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = _devices.sortedWith(
                                        compareByDescending<DeviceDiscovery.AirPlayDevice> {
                                            _isCasting.value && _connectedDevice.value == it.name
                                        }
                                    ),
                                    key = { it.name }
                                ) { device ->
                                    val isCurrentConnected = _isCasting.value && _connectedDevice.value == device.name
                                    val isConnecting = _connectingName.value == device.name

                                    DeviceRowItem(
                                        device = device,
                                        isConnected = isCurrentConnected,
                                        isConnecting = isConnecting,
                                        volume = _volume.value.roundToInt(),
                                        onConnect = {
                                            if (!isConnecting && !isCurrentConnected) {
                                                _connectingName.value = device.name
                                                clickSound?.playClick(vibrate = true)
                                                connectTo(device)
                                            }
                                        },
                                        onDisconnect = {
                                            clickSound?.playClick(vibrate = true)
                                            stopCasting()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Volume Knob Bottom Sheet Overlay
            if (_showVolumeKnob.value) {
                VintageKnobSheet(
                    volume = _volume.value,
                    deviceName = _connectedDevice.value,
                    clickSoundGenerator = clickSound,
                    onVolumeChange = { v ->
                        _volume.value = v
                        NativeBridge.setVolume(v)
                    },
                    onDismiss = {
                        _showVolumeKnob.value = false
                    }
                )
            }

            // PIN Input Dialog
            if (_showPinDialog.value) {
                AlertDialog(
                    onDismissRequest = { _showPinDialog.value = false },
                    title = { Text("输入 PIN", color = AirCastTheme.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = _pinText.value,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) _pinText.value = it },
                            label = { Text("4 位 PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            NativeBridge.submitPin(_pinText.value)
                            _showPinDialog.value = false
                            _pinText.value = ""
                        }) { Text("配对", color = AirCastTheme.Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { _showPinDialog.value = false }) { Text("取消", color = AirCastTheme.TextSecondary) }
                    },
                    containerColor = AirCastTheme.BgElevated
                )
            }
        }
    }

    /**
     * Now Playing Card with Neumorphic buttons and quick volume adjust bar
     */
    @Composable
    private fun NowPlayingCard(
        deviceName: String,
        model: String,
        volume: Double,
        onStepVolume: (Double) -> Unit,
        onOpenKnob: () -> Unit,
        onDisconnect: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(AirCastTheme.RadiusCard))
                .background(AirCastTheme.BgSurface)
                .border(
                    width = 1.dp,
                    color = AirCastTheme.Border,
                    shape = RoundedCornerShape(AirCastTheme.RadiusCard)
                )
                .padding(16.dp)
        ) {
            Column {
                // Top row: Glyph tile + Device Name/State + Neumorphic Disconnect Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(AirCastTheme.RadiusIconTile))
                            .background(AirCastTheme.BgElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        DeviceGlyph(
                            model = model,
                            size = 28.dp,
                            tint = AirCastTheme.Accent
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AirCastTheme.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AirCastTheme.Success)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "正在投放 · 音量 ${volume.roundToInt()}%",
                                fontSize = 12.sp,
                                color = AirCastTheme.Success
                            )
                        }
                    }

                    // Neumorphic circular disconnect button
                    NeumorphicCircleButton(
                        onClick = onDisconnect,
                        size = 44.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "断开连接",
                            tint = AirCastTheme.Danger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Volume Bar Row: [-] [ ===== progress bar ===== ] [+]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NeumorphicCircleButton(
                        onClick = { onStepVolume(-5.0) },
                        size = 40.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "音量减",
                            tint = AirCastTheme.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Inset progress bar (tap to open volume knob bottom sheet)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .neumorphic(
                                pressed = true,
                                shape = NeumorphShape.RoundedRect,
                                cornerRadius = 5.dp,
                                elevation = 3.dp
                            )
                            .clip(RoundedCornerShape(5.dp))
                            .background(AirCastTheme.BgElevated)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenKnob
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((volume / 100.0).toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(5.dp))
                                .background(AirCastTheme.Accent)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    NeumorphicCircleButton(
                        onClick = { onStepVolume(5.0) },
                        size = 40.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "音量加",
                            tint = AirCastTheme.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    /**
     * Individual Device Item Row
     */
    @Composable
    private fun DeviceRowItem(
        device: DeviceDiscovery.AirPlayDevice,
        isConnected: Boolean,
        isConnecting: Boolean,
        volume: Int,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit
    ) {
        val rowBg = if (isConnected) {
            AirCastTheme.BgSurface
        } else {
            AirCastTheme.BgSurface
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AirCastTheme.RadiusCard))
                .background(rowBg)
                .then(
                    if (isConnected) {
                        Modifier
                            .background(AirCastTheme.AccentSubtle)
                            .border(1.dp, AirCastTheme.AccentGlow, RoundedCornerShape(AirCastTheme.RadiusCard))
                    } else {
                        Modifier.border(1.dp, AirCastTheme.Border, RoundedCornerShape(AirCastTheme.RadiusCard))
                    }
                )
                .clickable(
                    enabled = !isConnected && !isConnecting,
                    onClick = onConnect
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Device Icon Tile (48dp x 48dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(AirCastTheme.RadiusIconTile))
                        .background(AirCastTheme.BgElevated),
                    contentAlignment = Alignment.Center
                ) {
                    DeviceGlyph(
                        model = device.model,
                        size = 28.dp,
                        tint = if (isConnected) AirCastTheme.Accent else AirCastTheme.DeviceIconGray
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Device Name and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AirCastTheme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val subText = when {
                        isConnected -> "已连接 · 音量 $volume%"
                        isConnecting -> "正在连接…"
                        else -> DeviceGlyphData.resolveLabel(device.model)
                    }
                    Text(
                        text = subText,
                        fontSize = 12.sp,
                        color = if (isConnected) AirCastTheme.Success else AirCastTheme.TextSecondary
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Action area on the right: Spinner / Check+Disconnect / "连接" button
                when {
                    isConnecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AirCastTheme.Accent,
                            trackColor = AirCastTheme.Border
                        )
                    }
                    isConnected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(AirCastTheme.Accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已连接",
                                    tint = AirCastTheme.BgBase,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "断开",
                                fontSize = 12.sp,
                                color = AirCastTheme.Danger,
                                modifier = Modifier
                                    .clickable(onClick = onDisconnect)
                                    .padding(4.dp)
                            )
                        }
                    }
                    else -> {
                        NeumorphicPillButton(
                            text = "连接",
                            onClick = onConnect,
                            cornerRadius = AirCastTheme.RadiusButton
                        )
                    }
                }
            }
        }
    }

    private fun connectTo(device: DeviceDiscovery.AirPlayDevice) {
        pendingDevice = device
        @Suppress("DEPRECATION")
        val intent = projectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }

    private fun stopCasting() {
        startService(Intent(this, CastService::class.java).setAction(CastService.ACTION_STOP))
        _isCasting.value = false
        _connectedDevice.value = ""
        _connectedModel.value = ""
        getSharedPreferences("airplay", MODE_PRIVATE)
            .edit().remove("last_device").remove("last_model").apply()
    }
}
