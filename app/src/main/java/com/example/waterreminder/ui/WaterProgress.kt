package com.example.waterreminder.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 水球进度主视觉：圆形内按进度从底部填充水位，水面由两条速度、方向不同的
 * 正弦波持续流动。相位 0→2π 线性循环在 sin 的整周期处无缝衔接，Restart 跳变不可见。
 * 波的流动与进度无关，只承担「水」的意象；水位变化由调用方传入已动画过的 progress。
 */
@Composable
fun WaterProgressCircle(
    progress: Float,
    modifier: Modifier = Modifier,
    waterColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    borderWidth: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "waterWave")
    val phaseFront by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "phaseFront"
    )
    // 反方向 + 不同周期，两条波不同步运动才有层次
    val phaseBack by transition.animateFloat(
        initialValue = (2 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "phaseBack"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val circle = Path().apply {
            addOval(Rect(center = Offset(size.width / 2f, size.height / 2f), radius = radius))
        }

        drawPath(circle, color = trackColor)
        drawPath(circle, color = borderColor, style = Stroke(borderWidth.toPx()))

        val level = size.height * (1f - progress.coerceIn(0f, 1f))
        val twoPi = (2 * PI).toFloat()
        val step = size.width / 48f

        fun wavePath(phase: Float, amplitude: Float, wavelength: Float): Path = Path().apply {
            moveTo(0f, level + amplitude * sin(phase))
            var x = 0f
            while (x < size.width) {
                x = minOf(x + step, size.width)
                lineTo(x, level + amplitude * sin(x / wavelength * twoPi + phase))
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        clipPath(circle) {
            drawPath(
                wavePath(phaseBack, size.height * 0.05f, size.width * 0.42f),
                color = waterColor.copy(alpha = 0.45f)
            )
            drawPath(
                wavePath(phaseFront, size.height * 0.035f, size.width * 0.6f),
                color = waterColor
            )
        }
    }
}
