package com.airplay.cast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.cast.ui.theme.AirCastTheme

@Composable
fun EmptyRadarState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_angle"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Radar Canvas (132dp x 132dp)
        Canvas(modifier = Modifier.size(132.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f
            val midRadius = outerRadius - 22.dp.toPx()
            val innerRadius = outerRadius - 44.dp.toPx()

            // 1. Concentric circles
            drawCircle(
                color = AirCastTheme.Border,
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = AirCastTheme.Border,
                radius = midRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = AirCastTheme.AccentGlow,
                radius = innerRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Rotating sweep fan
            rotate(sweepAngle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.0f to AirCastTheme.AccentGlow,
                        0.19f to Color.Transparent,
                        1.0f to Color.Transparent,
                        center = center
                    ),
                    radius = outerRadius,
                    center = center
                )
            }

            // 3. Central glowing brass core (8dp diameter -> 4dp radius)
            drawCircle(
                color = AirCastTheme.Accent,
                radius = 4.dp.toPx(),
                center = center
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "未发现附近设备",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AirCastTheme.TextPrimary
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "确保设备与手机处于同一 Wi-Fi",
            fontSize = 13.sp,
            color = AirCastTheme.TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        NeumorphicPillButton(
            text = "重新扫描",
            onClick = onRetry,
            cornerRadius = AirCastTheme.RadiusButton,
            textColor = AirCastTheme.TextPrimary
        )
    }
}
