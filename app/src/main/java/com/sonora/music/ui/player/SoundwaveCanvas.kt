package com.sonora.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.VioletPulse
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom Compose Canvas rendering smooth multi-harmonic radial + bar spectrum animations
 * driven solely by playback state and dynamic accent color (zero microphone permission needed).
 */
@Composable
fun SoundwaveCanvas(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    secondaryColor: Color = CyanGlow,
    tertiaryColor: Color = VioletPulse,
    innerRadiusFraction: Float = 0.28f,
    barCount: Int = 64
) {
    val transition = rememberInfiniteTransition(label = "soundwave_transition")

    val primaryPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primary_phase"
    )

    val secondaryPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_phase"
    )

    val pulsePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.14f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "wave_energy"
    )

    Canvas(modifier = modifier) {
        val minDim = min(size.width, size.height)
        if (minDim <= 0f) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = minDim * 0.48f
        val baseRadius = minDim * innerRadiusFraction

        // 1. Breathing radial ambient aura
        val pulseFactor = 0.92f + 0.08f * sin(pulsePhase) * energy
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.28f * energy + 0.05f),
                    secondaryColor.copy(alpha = 0.12f * energy),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius * 1.08f * pulseFactor
            ),
            radius = maxRadius * 1.08f * pulseFactor,
            center = center
        )

        // 2. Outer multi-harmonic wave ring
        val ringSamples = 84
        val outerRingPath = Path()
        val innerWavePath = Path()
        val maxWaveReach = (maxRadius - baseRadius) * 0.42f

        for (i in 0..ringSamples) {
            val fraction = i.toFloat() / ringSamples
            val angle = fraction * (2f * PI).toFloat()

            val h1 = sin(angle * 3f + primaryPhase)
            val h2 = cos(angle * 5f - secondaryPhase * 2f) * 0.55f
            val h3 = sin(angle * 8f + pulsePhase * 2f) * 0.25f
            val harmonic = ((h1 + h2 + h3) / 1.8f) * maxWaveReach * energy

            val rOuter = baseRadius + (maxRadius - baseRadius) * 0.52f + harmonic
            val xOuter = center.x + rOuter * cos(angle)
            val yOuter = center.y + rOuter * sin(angle)
            if (i == 0) outerRingPath.moveTo(xOuter, yOuter) else outerRingPath.lineTo(xOuter, yOuter)

            val innerHarmonic = ((sin(angle * 4f - primaryPhase * 2f) +
                cos(angle * 6f + secondaryPhase) * 0.6f) / 1.6f) * (maxWaveReach * 0.65f) * energy
            val rInner = baseRadius * 1.08f + innerHarmonic
            val xInner = center.x + rInner * cos(angle)
            val yInner = center.y + rInner * sin(angle)
            if (i == 0) innerWavePath.moveTo(xInner, yInner) else innerWavePath.lineTo(xInner, yInner)
        }
        outerRingPath.close()
        innerWavePath.close()

        drawPath(
            path = outerRingPath,
            brush = Brush.sweepGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.55f),
                    secondaryColor.copy(alpha = 0.50f),
                    tertiaryColor.copy(alpha = 0.45f),
                    accentColor.copy(alpha = 0.55f)
                ),
                center = center
            ),
            style = Stroke(width = minDim * 0.008f)
        )

        drawPath(
            path = innerWavePath,
            color = secondaryColor.copy(alpha = 0.35f + 0.25f * energy),
            style = Stroke(width = minDim * 0.005f)
        )

        // 3. Radial spectrum bars around the inner disc
        val availableBarSpan = (maxRadius - baseRadius) * 0.88f
        val strokeW = (minDim * 0.011f).coerceIn(2.5f, 7f)

        for (i in 0 until barCount) {
            val angle = (i.toFloat() / barCount) * (2f * PI).toFloat() - (PI / 2f).toFloat()
            val bandWaveA = (sin(angle * 4f + primaryPhase * 2f) + 1f) * 0.5f
            val bandWaveB = (cos(angle * 7f - secondaryPhase * 3f) + 1f) * 0.5f
            val bandBeat = (sin(pulsePhase * 2f + i * 0.42f) + 1f) * 0.5f
            val combined = (0.45f * bandWaveA + 0.35f * bandWaveB + 0.20f * bandBeat)
            val barLength = (minDim * 0.018f) + availableBarSpan * (0.14f + 0.86f * combined) * energy

            val startR = baseRadius * 1.04f
            val endR = startR + barLength

            val start = Offset(
                x = center.x + startR * cos(angle),
                y = center.y + startR * sin(angle)
            )
            val end = Offset(
                x = center.x + endR * cos(angle),
                y = center.y + endR * sin(angle)
            )

            val blend = i.toFloat() / barCount
            val barColor = when {
                blend < 0.33f -> lerpColor(accentColor, secondaryColor, blend * 3f)
                blend < 0.66f -> lerpColor(secondaryColor, tertiaryColor, (blend - 0.33f) * 3f)
                else -> lerpColor(tertiaryColor, accentColor, (blend - 0.66f) * 3f)
            }

            drawLine(
                color = barColor.copy(alpha = 0.45f + 0.50f * combined * energy),
                start = start,
                end = end,
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}
