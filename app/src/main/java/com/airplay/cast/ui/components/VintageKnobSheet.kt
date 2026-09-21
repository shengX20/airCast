package com.airplay.cast.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.cast.ui.audio.ClickSoundGenerator
import com.airplay.cast.ui.theme.AirCastTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Vintage Radio Neumorphic Volume Knob Sheet
 * 280dp dial canvas with 200dp dial, 120dp center hub, 0-10 tick markers,
 * audio tick feedback, tactile vibration, tick flash, and dial pulse animation.
 */
@Composable
fun VintageKnobSheet(
    volume: Double,
    deviceName: String,
    clickSoundGenerator: ClickSoundGenerator?,
    onVolumeChange: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var autoCloseJob by remember { mutableStateOf<Job?>(null) }

    // Visual states
    var currentVol by remember(volume) { mutableFloatStateOf(volume.toFloat().coerceIn(0f, 100f)) }
    var flashingTickIndex by remember { mutableIntStateOf(-1) }
    val dialScale = remember { Animatable(1.0f) }

    // Trigger tick feedback
    fun triggerTickFeedback(crossedIndex: Int) {
        clickSoundGenerator?.playClick(vibrate = true)
        flashingTickIndex = crossedIndex
        coroutineScope.launch {
            delay(120)
            if (flashingTickIndex == crossedIndex) {
                flashingTickIndex = -1
            }
        }
        coroutineScope.launch {
            dialScale.animateTo(1.015f, animationSpec = tween(45, easing = LinearEasing))
            dialScale.animateTo(1.0f, animationSpec = tween(45, easing = LinearEasing))
        }
    }

    // Schedule auto dismiss after 1.2s of inactivity
    fun scheduleAutoDismiss() {
        autoCloseJob?.cancel()
        autoCloseJob = coroutineScope.launch {
            delay(1200)
            onDismiss()
        }
    }

    fun cancelAutoDismiss() {
        autoCloseJob?.cancel()
    }

    // Scrim overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                cancelAutoDismiss()
                onDismiss()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* intercept clicks inside sheet */ },
            shape = RoundedCornerShape(topStart = AirCastTheme.RadiusSheet, topEnd = AirCastTheme.RadiusSheet),
            color = AirCastTheme.BgElevated,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top device name
                Text(
                    text = deviceName.ifBlank { "AirCast 设备" },
                    fontSize = 14.sp,
                    color = AirCastTheme.TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(20.dp))

                // 280dp Knob Wrap
                VintageKnobCore(
                    volume = currentVol,
                    flashingTick = flashingTickIndex,
                    dialScale = dialScale.value,
                    onVolumeChanged = { newVol ->
                        val oldBucket = floor(currentVol / 10f).toInt()
                        val newBucket = floor(newVol / 10f).toInt()
                        currentVol = newVol
                        onVolumeChange(newVol.toDouble())

                        if (oldBucket != newBucket) {
                            val crossed = ((oldBucket + newBucket) / 2f).roundToInt().coerceIn(0, 10)
                            triggerTickFeedback(crossed)
                        }
                    },
                    onDragStart = { cancelAutoDismiss() },
                    onDragEnd = { scheduleAutoDismiss() }
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "拖动旋钮 / 轻点任意处关闭",
                    fontSize = 12.sp,
                    color = AirCastTheme.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun VintageKnobCore(
    volume: Float,
    flashingTick: Int,
    dialScale: Float,
    onVolumeChanged: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    var centerOffset by remember { mutableStateOf(Offset.Zero) }
    val isSnapped = (volume.roundToInt() % 10 == 0)

    val density = androidx.compose.ui.platform.LocalDensity.current
    val dialPx = with(density) { 200.dp.toPx() }
    val hubPx = with(density) { 120.dp.toPx() }

    Box(
        modifier = Modifier
            .size(280.dp)
            .onGloballyPositioned { coords ->
                centerOffset = Offset(coords.size.width / 2f, coords.size.height / 2f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDragStart()
                        val dx = offset.x - centerOffset.x
                        val dy = offset.y - centerOffset.y
                        val rad = atan2(dx.toDouble(), -dy.toDouble())
                        var deg = Math.toDegrees(rad).toFloat()
                        deg = deg.coerceIn(-135f, 135f)
                        val v = ((deg + 135f) / 270f * 100f).coerceIn(0f, 100f)
                        onVolumeChanged(v)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val dx = change.position.x - centerOffset.x
                        val dy = change.position.y - centerOffset.y
                        val rad = atan2(dx.toDouble(), -dy.toDouble())
                        var deg = Math.toDegrees(rad).toFloat()
                        deg = deg.coerceIn(-135f, 135f)
                        val v = ((deg + 135f) / 270f * 100f).coerceIn(0f, 100f)
                        onVolumeChanged(v)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Ring: 280dp x 280dp
        // Draws Arc Track, Arc Progress, 11 Major Ticks, 40 Minor Ticks, Numbers 0..10
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arcR = 126.dp.toPx()
            val startAngle = 135f
            val sweepAngle = (volume / 100f) * 270f

            // 1. Arc Track (Background)
            drawArc(
                color = Color(0x10FFFFFF), // rgba(255,255,255, 0.06)
                startAngle = startAngle,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR),
                size = Size(arcR * 2, arcR * 2),
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Arc Progress (Accent Brass)
            if (sweepAngle > 0.5f) {
                // Outer glow
                drawArc(
                    color = AirCastTheme.AccentGlow,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2, arcR * 2),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                // Core bar
                drawArc(
                    color = AirCastTheme.Accent,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2, arcR * 2),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Ticks and Numbers
            val numPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

            for (i in 0..10) {
                val tickDeg = -135f + i * 27f
                val rad = Math.toRadians(tickDeg.toDouble())
                val isLit = (i * 10 <= volume)
                val isFlashing = (flashingTick == i)

                // Major tick line: radius 131dp to 141dp
                val r1 = 131.dp.toPx()
                val r2 = 141.dp.toPx()
                val sinA = sin(rad).toFloat()
                val cosA = -cos(rad).toFloat()

                val tickColor = when {
                    isFlashing -> Color.White
                    isLit -> AirCastTheme.Accent
                    else -> AirCastTheme.TextTertiary
                }
                val tickWidth = if (isFlashing) 3.5.dp.toPx() else 2.dp.toPx()

                drawLine(
                    color = tickColor,
                    start = Offset(cx + r1 * sinA, cy + r1 * cosA),
                    end = Offset(cx + r2 * sinA, cy + r2 * cosA),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )

                // Number at radius 113dp
                val numR = 113.dp.toPx()
                val numX = cx + numR * sinA
                val numY = cy + numR * cosA + (numPaint.textSize / 3f)

                numPaint.color = if (isLit) {
                    android.graphics.Color.argb(255, 0xE0, 0xA4, 0x58)
                } else {
                    android.graphics.Color.argb(255, 0x6C, 0x6C, 0x72)
                }
                drawContext.canvas.nativeCanvas.drawText("$i", numX, numY, numPaint)

                // Minor ticks (4 ticks between this and next major tick)
                if (i < 10) {
                    for (j in 1..4) {
                        val mDeg = tickDeg + j * 5.4f
                        val mRad = Math.toRadians(mDeg.toDouble())
                        val mr1 = 135.dp.toPx()
                        val mr2 = 141.dp.toPx()
                        val mSin = sin(mRad).toFloat()
                        val mCos = -cos(mRad).toFloat()

                        drawLine(
                            color = AirCastTheme.TextTertiary.copy(alpha = 0.45f),
                            start = Offset(cx + mr1 * mSin, cy + mr1 * mCos),
                            end = Offset(cx + mr2 * mSin, cy + mr2 * mCos),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Center Dial: 200dp x 200dp
        val currentAngle = -135f + (volume / 100f) * 270f
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(dialScale)
                .drawBehind {
                    val cr = size.width / 2f
                    // Neumorphic outer soft shadow
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        // Bottom-right deep dark shadow
                        val darkPaint = Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(140, 0, 0, 0)
                            maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
                        }
                        native.save()
                        native.translate(0f, 14f)
                        native.drawCircle(cr, cr, cr, darkPaint)
                        native.restore()

                        // Top-left light specular glow
                        val lightPaint = Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(16, 255, 255, 255)
                            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                        }
                        native.save()
                        native.translate(0f, -6f)
                        native.drawCircle(cr, cr, cr, lightPaint)
                        native.restore()
                    }
                }
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AirCastTheme.BgDialCenter,
                            AirCastTheme.BgDialEdge
                        ),
                        center = Offset(dialPx * 0.38f, dialPx * 0.32f),
                        radius = dialPx * 0.72f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Rotating layer with Brass Indicator
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Outer border ring: 1.5px rgba(255,255,255, 0.08)
                drawCircle(
                    color = Color(0x14FFFFFF),
                    radius = cx - 1f,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Brass indicator line
                rotate(currentAngle, pivot = Offset(cx, cy)) {
                    val startR = 64.dp.toPx()
                    val endR = 92.dp.toPx()
                    val indWidth = if (isSnapped) 8.dp.toPx() else 6.dp.toPx()

                    // Glow behind indicator
                    drawLine(
                        color = AirCastTheme.AccentGlow,
                        start = Offset(cx, cy - startR),
                        end = Offset(cx, cy - endR),
                        strokeWidth = indWidth + 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Solid brass indicator
                    drawLine(
                        color = AirCastTheme.Accent,
                        start = Offset(cx, cy - startR),
                        end = Offset(cx, cy - endR),
                        strokeWidth = indWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Center Inset Hub (120dp x 120dp) - Stationary Info Display
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        val cr = size.width / 2f
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            // Inset dark shadow: top-left 4px 6px
                            val innerDark = Paint().apply {
                                isAntiAlias = true
                                color = android.graphics.Color.argb(160, 0, 0, 0)
                                maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
                            }
                            native.save()
                            native.translate(4f, 6f)
                            native.drawCircle(cr, cr, cr, innerDark)
                            native.restore()
                        }
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AirCastTheme.BgHubCenter,
                                AirCastTheme.BgHubEdge
                            ),
                            center = Offset(hubPx * 0.40f, hubPx * 0.34f),
                            radius = hubPx * 0.78f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "VOLUME",
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = AirCastTheme.TextTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${volume.roundToInt()}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirCastTheme.Accent
                    )
                }
            }
        }
    }
}
