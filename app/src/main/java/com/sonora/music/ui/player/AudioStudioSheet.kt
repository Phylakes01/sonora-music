package com.sonora.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.model.AudioOutputDevice
import com.sonora.music.model.AudioSettings
import com.sonora.music.model.EqPreset
import com.sonora.music.model.SleepTimerState
import com.sonora.music.ui.theme.CyanGlow
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.ObsidianCard
import com.sonora.music.ui.theme.ObsidianCardElevated
import com.sonora.music.ui.theme.ObsidianStroke
import com.sonora.music.ui.theme.ObsidianSurface
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import com.sonora.music.ui.theme.VioletPulse
import kotlin.math.roundToInt

private val EqBandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
private const val MIN_MB = -1200f
private const val MAX_MB = 1200f
private val SpeedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val SleepTimerMinutes = listOf(5, 15, 30, 45, 60)

/**
 * Full Audio Studio sheet featuring a 5-band Equalizer with interactive Bezier frequency
 * curve, 9 EqPresets, Bass Boost & Spatial Virtualizer, Crossfade (0..12s), Sleep Timer
 * with live countdown, Playback Speed chips, Private Session toggle, and Output Device switcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioSheet(
    audioSettings: AudioSettings,
    sleepTimer: SleepTimerState,
    playbackSpeed: Float,
    outputDevices: List<AudioOutputDevice>,
    accentColor: Color,
    onUpdateSettings: ((AudioSettings) -> AudioSettings) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onSetSleepTimer: (minutes: Int, endOfTrack: Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onRefreshOutputDevices: () -> Unit,
    onOpenOutputPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bands = List(5) { idx -> audioSettings.bandLevelsMb.getOrElse(idx) { 0 } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = accentColor
                        )
                    }
                    Column {
                        Text(
                            text = "Audio Studio",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = audioSettings.streamingQualityLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Audio Studio",
                        tint = TextSecondary
                    )
                }
            }

            // 5-Band Equalizer Card
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "5-Band Equalizer",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (audioSettings.eqEnabled) audioSettings.eqPreset.label else "Bypassed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (audioSettings.eqEnabled) accentColor else TextMuted
                        )
                    }

                    Switch(
                        checked = audioSettings.eqEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(eqEnabled = enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = accentColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 9 EqPreset chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EqPreset.entries.forEach { preset ->
                        val selected = audioSettings.eqPreset == preset
                        StudioChip(
                            label = preset.label,
                            selected = selected,
                            accentColor = accentColor,
                            onClick = { onApplyPreset(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive EQ Frequency Curve Canvas
                EqCurveCanvas(
                    bandLevelsMb = bands,
                    enabled = audioSettings.eqEnabled,
                    accentColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianBg.copy(alpha = 0.65f))
                        .border(1.dp, ObsidianStroke, RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 5 Vertical Frequency Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    bands.forEachIndexed { index, levelMb ->
                        VerticalEqSlider(
                            frequencyLabel = EqBandLabels[index],
                            levelMb = levelMb,
                            enabled = audioSettings.eqEnabled,
                            accentColor = accentColor,
                            onLevelChange = { updatedMb ->
                                onUpdateSettings { current ->
                                    val updated = current.bandLevelsMb.toMutableList()
                                    while (updated.size < 5) updated.add(0)
                                    updated[index] = updatedMb
                                    current.copy(
                                        eqEnabled = true,
                                        eqPreset = EqPreset.CUSTOM,
                                        bandLevelsMb = updated
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bass Boost, Virtualizer & Crossfade Card
            StudioCard {
                Text(
                    text = "Dynamics & Spatial",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LabeledSlider(
                    title = "Bass Boost",
                    valueLabel = "${(audioSettings.bassBoostStrength / 10f).roundToInt()}%",
                    value = audioSettings.bassBoostStrength.toFloat(),
                    valueRange = 0f..1000f,
                    accentColor = accentColor,
                    onValueChange = { v ->
                        onUpdateSettings { it.copy(bassBoostStrength = v.roundToInt()) }
                    }
                )

                LabeledSlider(
                    title = "Spatial Virtualizer",
                    valueLabel = "${(audioSettings.virtualizerStrength / 10f).roundToInt()}%",
                    value = audioSettings.virtualizerStrength.toFloat(),
                    valueRange = 0f..1000f,
                    accentColor = CyanGlow,
                    onValueChange = { v ->
                        onUpdateSettings { it.copy(virtualizerStrength = v.roundToInt()) }
                    }
                )

                LabeledSlider(
                    title = "Crossfade Transitions",
                    valueLabel = if (audioSettings.crossfadeSeconds == 0) "Off" else "${audioSettings.crossfadeSeconds}s",
                    value = audioSettings.crossfadeSeconds.toFloat(),
                    valueRange = 0f..12f,
                    steps = 11,
                    accentColor = VioletPulse,
                    onValueChange = { v ->
                        onUpdateSettings { it.copy(crossfadeSeconds = v.roundToInt()) }
                    }
                )
            }

            // Playback Speed Card
            StudioCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedOptions.forEach { speed ->
                        val active = kotlin.math.abs(playbackSpeed - speed) < 0.02f
                        StudioChip(
                            label = "${speed}x",
                            selected = active,
                            accentColor = accentColor,
                            onClick = {
                                onUpdateSettings { it.copy(playbackSpeed = speed) }
                            }
                        )
                    }
                }
            }

            // Sleep Timer Card
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    if (sleepTimer.active) {
                        Text(
                            text = formatSleepCountdown(sleepTimer),
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanGlow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SleepTimerMinutes.forEach { mins ->
                        StudioChip(
                            label = "${mins}m",
                            selected = sleepTimer.active && !sleepTimer.endOfTrack && sleepTimer.label == "${mins}m",
                            accentColor = CyanGlow,
                            onClick = { onSetSleepTimer(mins, false) }
                        )
                    }
                    StudioChip(
                        label = "End of Track",
                        selected = sleepTimer.active && sleepTimer.endOfTrack,
                        accentColor = CyanGlow,
                        onClick = { onSetSleepTimer(0, true) }
                    )
                    if (sleepTimer.active) {
                        StudioChip(
                            label = "Off",
                            selected = false,
                            accentColor = accentColor,
                            onClick = onCancelSleepTimer
                        )
                    }
                }
            }

            // Private Session Switch
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = VioletPulse,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Private Session",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Pause listening history & Wrapped tracking",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = audioSettings.privateSession,
                        onCheckedChange = { isPrivate ->
                            onUpdateSettings { it.copy(privateSession = isPrivate) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = VioletPulse
                        )
                    )
                }
            }

            // Audio Output Devices Card
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SpeakerGroup,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Audio Output Devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onRefreshOutputDevices,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh output devices",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (outputDevices.isEmpty()) {
                    Text(
                        text = "Phone Speaker (Default)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        outputDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (device.isActive) accentColor.copy(alpha = 0.14f)
                                        else ObsidianBg.copy(alpha = 0.45f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (device.isActive) accentColor.copy(alpha = 0.5f) else ObsidianStroke,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = device.typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                if (device.isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(accentColor)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = ObsidianBg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onOpenOutputPicker,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Switch System Output",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EqCurveCanvas(
    bandLevelsMb: List<Int>,
    enabled: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 24.dp.toPx()
        val padY = 16.dp.toPx()
        val usableW = (w - padX * 2f).coerceAtLeast(1f)
        val usableH = (h - padY * 2f).coerceAtLeast(1f)
        val centerY = padY + usableH / 2f

        // Zero-dB center line & subtle dB guides
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(padX, centerY),
            end = Offset(w - padX, centerY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(padX, padY),
            end = Offset(w - padX, padY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(padX, h - padY),
            end = Offset(w - padX, h - padY),
            strokeWidth = 1.dp.toPx()
        )

        val points = bandLevelsMb.mapIndexed { idx, mb ->
            val x = padX + (idx.toFloat() / 4f) * usableW
            val normalized = ((mb.toFloat() - MIN_MB) / (MAX_MB - MIN_MB)).coerceIn(0f, 1f)
            val y = padY + (1f - normalized) * usableH
            Offset(x, y)
        }

        if (points.size >= 2) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.lastIndex) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, h - padY)
                lineTo(points.first().x, h - padY)
                close()
            }

            val activeTint = if (enabled) accentColor else TextMuted
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        activeTint.copy(alpha = if (enabled) 0.34f else 0.12f),
                        Color.Transparent
                    ),
                    startY = padY,
                    endY = h - padY
                )
            )

            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = if (enabled) listOf(accentColor, CyanGlow, VioletPulse)
                    else listOf(TextMuted, TextMuted)
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEach { pt ->
                drawCircle(
                    color = activeTint.copy(alpha = 0.32f),
                    radius = 8.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = TextPrimary,
                    radius = 4.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

@Composable
private fun VerticalEqSlider(
    frequencyLabel: String,
    levelMb: Int,
    enabled: Boolean,
    accentColor: Color,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dbValue = (levelMb / 100f).roundToInt()
    val dbText = if (dbValue > 0) "+${dbValue}dB" else "${dbValue}dB"
    val activeColor = if (enabled) accentColor else TextMuted

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dbText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (enabled && dbValue != 0) activeColor else TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .width(42.dp)
                .height(126.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun updateFromY(posY: Float) {
                        val pad = 10.dp.toPx()
                        val usable = (size.height - pad * 2f).coerceAtLeast(1f)
                        val fraction = (1f - ((posY - pad) / usable)).coerceIn(0f, 1f)
                        val newMb = (MIN_MB + fraction * (MAX_MB - MIN_MB)).roundToInt()
                        onLevelChange(newMb)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        updateFromY(down.position.y)
                        drag(down.id) { change ->
                            change.consume()
                            updateFromY(change.position.y)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackX = size.width / 2f
                val topY = 10.dp.toPx()
                val bottomY = size.height - 10.dp.toPx()
                val usableH = (bottomY - topY).coerceAtLeast(1f)
                val norm = ((levelMb.toFloat() - MIN_MB) / (MAX_MB - MIN_MB)).coerceIn(0f, 1f)
                val thumbY = topY + (1f - norm) * usableH
                val zeroY = topY + 0.5f * usableH

                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(trackX, topY),
                    end = Offset(trackX, bottomY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = activeColor,
                    start = Offset(trackX, zeroY),
                    end = Offset(trackX, thumbY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = activeColor.copy(alpha = 0.30f),
                    radius = 12.dp.toPx(),
                    center = Offset(trackX, thumbY)
                )
                drawCircle(
                    color = TextPrimary,
                    radius = 6.5.dp.toPx(),
                    center = Offset(trackX, thumbY)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSecondary
        )
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            )
        )
    }
}

@Composable
private fun StudioChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accentColor else ObsidianCardElevated)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else ObsidianStroke,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = if (selected) ObsidianBg else TextPrimary
        )
    }
}

@Composable
private fun StudioCard(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianCard.copy(alpha = 0.78f))
            .border(1.dp, ObsidianStroke, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

private fun formatSleepCountdown(state: SleepTimerState): String {
    if (state.endOfTrack) return "End of Track"
    val totalSec = (state.remainingMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}
