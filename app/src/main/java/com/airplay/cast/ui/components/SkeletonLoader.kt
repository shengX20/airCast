package com.airplay.cast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.cast.ui.theme.AirCastTheme

@Composable
fun ScanningSkeletonView(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tip row: Spinner + Text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = AirCastTheme.Accent,
                strokeWidth = 2.dp,
                trackColor = AirCastTheme.Border
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "正在扫描附近的 AirPlay 设备…",
                fontSize = 13.sp,
                color = AirCastTheme.TextSecondary
            )
        }

        // 3 Skeleton cards
        repeat(3) {
            SkeletonCard(shimmerProgress = translateAnim)
        }
    }
}

@Composable
private fun SkeletonCard(
    shimmerProgress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(AirCastTheme.RadiusCard))
            .border(
                width = 1.dp,
                color = AirCastTheme.Border,
                shape = RoundedCornerShape(AirCastTheme.RadiusCard)
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AirCastTheme.BgSurface,
                        Color(0xFF1E2129),
                        AirCastTheme.BgSurface
                    ),
                    start = Offset(shimmerProgress * 1000f, 0f),
                    end = Offset((shimmerProgress + 1f) * 1000f, 0f)
                )
            )
    )
}
