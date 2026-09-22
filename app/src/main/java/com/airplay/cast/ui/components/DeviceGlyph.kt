package com.airplay.cast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airplay.cast.ui.theme.AirCastTheme

enum class DeviceType {
    HomePodMini,
    HomePod,
    AppleTV,
    Speaker
}

object DeviceGlyphData {
    const val PATH_MINI = "M 7.46 21.40 C 3.12 20.18 0.09 14.66 1.00 9.63 C 1.64 6.10 3.94 2.97 6.40 2.31 C 9.37 1.51 14.61 1.51 17.57 2.31 C 20.04 2.98 22.34 6.13 23.00 9.76 C 23.78 14.05 21.34 19.19 17.67 20.97 C 16.53 21.53 15.84 21.63 12.55 21.69 C 10.09 21.74 8.29 21.64 7.46 21.40 Z M 17.23 5.82 C 18.42 5.24 18.68 4.44 17.85 3.92 C 17.24 3.53 16.87 3.87 17.32 4.41 C 17.83 5.03 16.87 5.43 14.22 5.69 C 11.76 5.94 7.86 5.65 6.92 5.15 C 6.43 4.88 6.38 4.74 6.66 4.41 C 7.10 3.87 6.74 3.53 6.13 3.92 C 4.89 4.69 5.79 5.79 8.08 6.27 C 8.76 6.41 10.84 6.49 12.72 6.44 C 15.37 6.38 16.37 6.24 17.23 5.82 Z M 13.05 4.09 C 13.13 3.84 12.81 3.73 12.01 3.73 C 10.92 3.73 10.61 3.95 11.06 4.41 C 11.38 4.72 12.92 4.48 13.05 4.09 Z"
    const val PATH_HOMEPOD = "M 6.69 22.99 C 5.27 22.37 4.27 21.42 3.54 19.98 C 3.01 18.95 2.94 18.10 2.94 12.24 C 2.94 6.39 3.01 5.53 3.54 4.50 C 4.99 1.66 6.60 1.00 12.00 1.00 C 17.40 1.00 19.01 1.66 20.46 4.50 C 20.99 5.53 21.06 6.39 21.06 12.24 C 21.06 18.10 20.99 18.95 20.46 19.98 C 19.71 21.45 18.71 22.38 17.24 23.00 C 15.63 23.67 8.25 23.66 6.69 22.99 Z M 14.50 8.47 C 16.15 8.09 17.31 7.11 17.31 6.10 C 17.31 5.16 16.61 5.16 15.66 6.11 C 15.21 6.56 14.37 6.93 13.46 7.06 C 11.35 7.38 9.21 6.98 8.34 6.11 C 7.39 5.16 6.69 5.16 6.69 6.10 C 6.69 8.14 10.67 9.35 14.50 8.47 Z"
    const val PATH_APPLETV = "M 3.46 22.38 C 2.57 21.93 2.07 21.43 1.62 20.54 C 1.02 19.35 1.00 19.03 1.00 12.00 C 1.00 4.97 1.02 4.65 1.62 3.46 C 2.07 2.57 2.57 2.07 3.46 1.62 C 4.65 1.02 4.97 1.00 12.00 1.00 C 19.03 1.00 19.35 1.02 20.54 1.62 C 21.43 2.07 21.93 2.57 22.38 3.46 C 22.98 4.65 23.00 4.97 23.00 12.00 C 23.00 19.03 22.98 19.35 22.38 20.54 C 21.93 21.43 21.43 21.93 20.54 22.38 C 19.35 22.98 19.03 23.00 12.00 23.00 C 4.97 23.00 4.65 22.98 3.46 22.38 Z M 9.97 14.33 C 10.39 13.69 10.39 13.60 9.97 13.14 C 9.45 12.57 9.39 11.70 9.83 11.25 C 10.41 10.67 9.79 10.40 7.86 10.40 C 6.12 10.40 5.89 10.46 5.49 11.07 C 4.99 11.84 5.13 13.33 5.81 14.37 C 6.18 14.94 6.45 15.03 7.88 15.03 C 9.32 15.03 9.57 14.94 9.97 14.33 Z M 14.15 14.54 C 14.10 14.28 13.87 14.06 13.65 14.06 C 13.35 14.06 13.21 13.69 13.15 12.76 C 13.09 11.72 13.16 11.45 13.50 11.45 C 13.73 11.45 13.93 11.26 13.93 11.04 C 13.93 10.81 13.74 10.63 13.51 10.63 C 13.26 10.63 13.10 10.37 13.10 9.94 C 13.10 9.39 12.96 9.25 12.41 9.25 C 11.86 9.25 11.73 9.39 11.73 9.94 C 11.73 10.32 11.60 10.63 11.45 10.63 C 11.30 10.63 11.18 10.81 11.18 11.04 C 11.18 11.26 11.30 11.45 11.45 11.45 C 11.60 11.45 11.73 12.02 11.73 12.71 C 11.73 14.48 12.09 15.03 13.27 15.03 C 14.05 15.03 14.22 14.93 14.15 14.54 Z M 18.15 12.99 C 19.01 10.57 19.01 10.63 18.38 10.63 C 18.01 10.63 17.74 11.07 17.33 12.34 L 16.77 14.06 L 16.22 12.41 C 15.80 11.14 15.53 10.74 15.07 10.67 C 14.74 10.63 14.48 10.63 14.48 10.67 C 14.48 10.75 14.83 11.70 15.82 14.27 C 16.03 14.80 16.31 15.03 16.77 15.03 C 17.34 15.03 17.53 14.75 18.15 12.99 Z M 8.64 9.75 C 8.83 9.56 8.98 9.25 8.98 9.05 C 8.98 8.77 8.85 8.75 8.43 8.98 C 8.12 9.14 7.88 9.45 7.88 9.67 C 7.88 10.16 8.20 10.19 8.64 9.75 Z"
    const val PATH_SPEAKER = "M 5.36 22.47 L 4.71 21.95 L 4.71 12.00 L 4.71 2.05 L 5.36 1.53 C 5.96 1.04 6.51 1.00 12.00 1.00 C 17.49 1.00 18.04 1.04 18.64 1.53 L 19.29 2.05 L 19.29 12.00 L 19.29 21.95 L 18.64 22.47 C 18.04 22.96 17.49 23.00 12.00 23.00 C 6.51 23.00 5.96 22.96 5.36 22.47 Z M 14.57 19.32 C 16.03 18.52 16.95 16.88 16.95 15.06 C 16.95 12.18 14.86 10.08 12.00 10.08 C 10.08 10.08 8.35 11.14 7.44 12.88 C 6.42 14.83 7.32 18.04 9.20 19.20 C 11.02 20.33 12.66 20.37 14.57 19.32 Z M 10.68 16.67 C 9.43 15.50 9.76 13.69 11.33 13.09 C 12.72 12.57 14.44 14.02 14.09 15.43 C 13.85 16.37 12.86 17.23 12.00 17.23 C 11.60 17.23 11.01 16.97 10.68 16.67 Z M 13.40 7.62 C 13.84 7.18 14.20 6.55 14.20 6.23 C 14.20 5.46 12.77 4.03 12.00 4.03 C 11.23 4.03 9.80 5.46 9.80 6.23 C 9.80 6.55 10.16 7.18 10.60 7.62 C 11.62 8.64 12.38 8.64 13.40 7.62 Z"

    fun resolveType(model: String): DeviceType {
        return when {
            model.contains("AudioAccessory5", ignoreCase = true) ||
            model.contains("AudioAccessory3", ignoreCase = true) ||
            model.contains("mini", ignoreCase = true) -> DeviceType.HomePodMini

            model.contains("AudioAccessory6", ignoreCase = true) ||
            model.contains("AudioAccessory1", ignoreCase = true) ||
            model.contains("HomePod", ignoreCase = true) -> DeviceType.HomePod

            model.contains("AppleTV", ignoreCase = true) ||
            model.contains("TV", ignoreCase = true) -> DeviceType.AppleTV

            else -> DeviceType.Speaker
        }
    }

    fun resolveLabel(model: String): String {
        return when {
            model.contains("AudioAccessory5", ignoreCase = true) ||
            model.contains("AudioAccessory3", ignoreCase = true) -> "HomePod mini"

            model.contains("AudioAccessory6", ignoreCase = true) -> "HomePod 第二代"
            model.contains("AudioAccessory1", ignoreCase = true) -> "HomePod 第一代"
            model.contains("AppleTV", ignoreCase = true) -> "Apple TV"
            model.isNotBlank() -> model
            else -> "AirPlay 2 设备"
        }
    }
}

@Composable
fun DeviceGlyph(
    model: String,
    modifier: Modifier = Modifier,
    tint: Color = AirCastTheme.DeviceIconGray,
    size: Dp = 28.dp
) {
    val deviceType = remember(model) { DeviceGlyphData.resolveType(model) }
    val pathData = when (deviceType) {
        DeviceType.HomePodMini -> DeviceGlyphData.PATH_MINI
        DeviceType.HomePod -> DeviceGlyphData.PATH_HOMEPOD
        DeviceType.AppleTV -> DeviceGlyphData.PATH_APPLETV
        DeviceType.Speaker -> DeviceGlyphData.PATH_SPEAKER
    }

    val parsedPath = remember(pathData) {
        PathParser().parsePathString(pathData).toPath().apply {
            fillType = PathFillType.EvenOdd
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(path = parsedPath, color = tint)
        }
    }
}
