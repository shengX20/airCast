package com.airplay.cast.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.cast.ui.theme.AirCastTheme

enum class NeumorphShape {
    Circle,
    RoundedRect
}

/**
 * Custom modifier providing Neumorphic dual lighting (emboss-out / emboss-in)
 */
fun Modifier.neumorphic(
    pressed: Boolean = false,
    shape: NeumorphShape = NeumorphShape.Circle,
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 6.dp
): Modifier = this.drawBehind {
    val crPx = if (shape == NeumorphShape.Circle) size.minDimension / 2f else cornerRadius.toPx()
    val elevPx = elevation.toPx()

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        if (!pressed) {
            // Convex (--emboss-out):
            // 1. Top-left soft specular highlight
            val lightPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(20, 255, 255, 255)
                maskFilter = BlurMaskFilter(elevPx * 1.5f, BlurMaskFilter.Blur.NORMAL)
            }
            native.save()
            native.translate(-elevPx * 0.45f, -elevPx * 0.45f)
            if (shape == NeumorphShape.Circle) {
                native.drawCircle(size.width / 2f, size.height / 2f, crPx, lightPaint)
            } else {
                native.drawRoundRect(0f, 0f, size.width, size.height, crPx, crPx, lightPaint)
            }
            native.restore()

            // 2. Bottom-right deep ambient drop shadow
            val darkPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(130, 0, 0, 0)
                maskFilter = BlurMaskFilter(elevPx * 2.2f, BlurMaskFilter.Blur.NORMAL)
            }
            native.save()
            native.translate(elevPx * 0.7f, elevPx * 0.9f)
            if (shape == NeumorphShape.Circle) {
                native.drawCircle(size.width / 2f, size.height / 2f, crPx, darkPaint)
            } else {
                native.drawRoundRect(0f, 0f, size.width, size.height, crPx, crPx, darkPaint)
            }
            native.restore()
        } else {
            // Concave (--emboss-in):
            // Inner dark shadow at top-left
            val innerDark = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(160, 0, 0, 0)
                maskFilter = BlurMaskFilter(elevPx * 1.2f, BlurMaskFilter.Blur.NORMAL)
            }
            native.save()
            native.translate(elevPx * 0.5f, elevPx * 0.6f)
            if (shape == NeumorphShape.Circle) {
                native.drawCircle(size.width / 2f, size.height / 2f, crPx, innerDark)
            } else {
                native.drawRoundRect(0f, 0f, size.width, size.height, crPx, crPx, innerDark)
            }
            native.restore()
        }
    }
}

/**
 * 44dp or 40dp Circular Neumorphic Button
 */
@Composable
fun NeumorphicCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    pressedOverride: Boolean? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isUserPressed by interactionSource.collectIsPressedAsState()
    val isPressed = pressedOverride ?: (isUserPressed && enabled)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "nbtn_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .neumorphic(
                pressed = isPressed,
                shape = NeumorphShape.Circle,
                elevation = if (enabled) 6.dp else 2.dp
            )
            .clip(CircleShape)
            .background(if (isPressed) Color(0xFF16181F) else AirCastTheme.BgElevated)
            .border(
                width = 1.dp,
                color = if (isPressed) Color(0x0AFFFFFF) else AirCastTheme.BorderSubtle,
                shape = CircleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Pill-shaped Neumorphic Button (e.g. "连接" or "重新扫描")
 */
@Composable
fun NeumorphicPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = AirCastTheme.RadiusButton,
    textColor: Color = AirCastTheme.TextPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "pill_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .neumorphic(
                pressed = isPressed,
                shape = NeumorphShape.RoundedRect,
                cornerRadius = cornerRadius,
                elevation = 5.dp
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isPressed) Color(0xFF16181F) else AirCastTheme.BgElevated)
            .border(
                width = 1.dp,
                color = if (isPressed) Color(0x0AFFFFFF) else AirCastTheme.BorderSubtle,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPressed) AirCastTheme.Accent else textColor
        )
    }
}
