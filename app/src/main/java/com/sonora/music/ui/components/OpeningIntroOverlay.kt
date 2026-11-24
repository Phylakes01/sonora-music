package com.sonora.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kinetic dark obsidian launch splash with expanding emerald resonance rings and
 * animated "SONORA" reveal (~1.1s total, tap anywhere to skip immediately).
 */
@Composable
fun OpeningIntroOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }
    val ring3 = remember { Animatable(0f) }
    val wordReveal = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            ring1.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(140)
            ring2.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(280)
            ring3.animateTo(1f, animationSpec = tween(820, easing = FastOutSlowInEasing))
        }
        launch {
            delay(180)
            wordReveal.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }
        delay(1100)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 280)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxR = size.minDimension * 0.56f
                val rings = listOf(
                    ring1.value to EmeraldAccent,
                    ring2.value to CyanGlow,
                    ring3.value to EmeraldAccent
                )
                rings.forEach { (progress, color) ->
                    if (progress > 0f && progress < 1f) {
                        val radius = maxR * (0.14f + progress * 0.86f)
                        drawCircle(
                            color = color.copy(alpha = (1f - progress) * 0.55f),
                            radius = radius,
                            style = Stroke(width = (3.5f * (1f - progress * 0.6f)).dp.toPx())
                        )
                    }
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EmeraldAccent.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    ),
                    radius = maxR * 0.48f
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(logoScale.value)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldAccent, CyanGlow)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = ObsidianBg,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "SONORA",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        letterSpacing = (12f - 6f * wordReveal.value).sp
                    ),
                    color = TextPrimary,
                    modifier = Modifier
                        .alpha(wordReveal.value)
                        .scale(0.92f + 0.08f * wordReveal.value)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "SOUND IN MOTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextSecondary,
                    modifier = Modifier.alpha(wordReveal.value * 0.85f)
                )
            }
        }
    }
}
