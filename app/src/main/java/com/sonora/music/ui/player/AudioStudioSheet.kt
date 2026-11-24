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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.sonora.music.ui.components.SonoraSlimSlider
import com.sonora.music.ui.theme.EmeraldAccent
import com.sonora.music.ui.theme.ObsidianBg
import com.sonora.music.ui.theme.TextMuted
import com.sonora.music.ui.theme.TextPrimary
import com.sonora.music.ui.theme.TextSecondary
import kotlin.math.roundToInt

private val EqBandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
private const val MIN_MB = -1200f
private const val MAX_MB = 1200f
private val SpeedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val SleepTimerMinutes = listOf(5, 15, 30, 45, 60)
private val StudioSheetBg = Color(0xFF161618)
private val StudioCardBg = Color(0xFF1E1E22)
private val StudioCardBorder = Color(0xFF2A2A30)
private val StudioDivider = Color(0xFF26262A)
private val ChipUnselectedBg = Color(0xFF2A2A2F)
private val SliderInactiveTrack = Color(0xFF4D4D52)

/**
 * Matte #161618 Audio Studio sheet featuring a 5-band Equalizer with #1DB954
 * frequency curve, crisp dividers, smooth typography, dynamics controls, Sleep Timer,
 * Playback Speed, Private Session, and Output Devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioSheet(
    audioSettings: AudioSettings,
    sleepTimer: SleepTimerState,
    playbackSpeed: Float,
    outputDevices: List<AudioOutputDevice>,
    @Suppress("UNUSED_PARAMETER") accentColor: Color,
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
        containerColor = StudioSheetBg,
        contentColor = TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34343A))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ChipUnselectedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Audio Studio",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
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

            HorizontalDivider(color = StudioDivider, thickness = 1.dp)

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
                            color = if (audioSettings.eqEnabled) EmeraldAccent else TextMuted
                        )
                    }

                    Switch(
                        checked = audioSettings.eqEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(eqEnabled = enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ChipUnselectedBg
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 9 flat EqPreset chips
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
                            onClick = { onApplyPreset(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Muted #1DB954 EQ Frequency Curve Canvas
                EqCurveCanvas(
                    bandLevelsMb = bands,
                    enabled = audioSettings.eqEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, StudioCardBorder, RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 5 Clean Vertical Frequency Faders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    bands.forEachIndexed { index, levelMb ->
                        VerticalEqSlider(
                            frequencyLabel = EqBandLabels[index],
                            levelMb = levelMb,
                            enabled = audioSettings.eqEnabled,
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

            // Bass Boost, Virtualizer & Crossfade Card with SonoraSlimSlider
            StudioCard {
                Text(
                    text = "Dynamics & Spatial",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LabeledSlimSlider(
                    title = "Bass Boost",
                    valueLabel = "${(audioSettings.bassBoostStrength / 10f).roundToInt()}%",
                    value = audioSettings.bassBoostStrength.toFloat(),
                    valueRange = 0f..1000f,
                    onValueChange = { v ->
                        onUpdateSettings { it.copy(bassBoostStrength = v.roundToInt()) }
                    }
                )

                HorizontalDivider(
                    color = StudioDivider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LabeledSlimSlider(
                    title = "Spatial Virtualizer",
                    valueLabel = "${(audioSettings.virtualizerStrength / 10f).roundToInt()}%",
                    value = audioSettings.virtualizerStrength.toFloat(),
                    valueRange = 0f..1000f,
                    onValueChange = { v ->
                        onUpdateSettings { it.copy(virtualizerStrength = v.roundToInt()) }
                    }
                )

                HorizontalDivider(
                    color = StudioDivider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LabeledSlimSlider(
                    title = "Crossfade Transitions",
                    valueLabel = if (audioSettings.crossfadeSeconds == 0) "Off" else "${audioSettings.crossfadeSeconds}s",
                    value = audioSettings.crossfadeSeconds.toFloat(),
                    valueRange = 0f..12f,
                    onValueChange = { v ->
                        onUpdateSettings { it.copy(crossfadeSeconds = v.roundToInt().coerceIn(0, 12)) }
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
                        tint = TextSecondary,
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
                            tint = TextSecondary,
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
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = EmeraldAccent
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
                            onClick = { onSetSleepTimer(mins, false) }
                        )
                    }
                    StudioChip(
                        label = "End of Track",
                        selected = sleepTimer.active && sleepTimer.endOfTrack,
                        onClick = { onSetSleepTimer(0, true) }
                    )
                    if (sleepTimer.active) {
                        StudioChip(
                            label = "Off",
                            selected = false,
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
                            tint = TextSecondary,
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
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ChipUnselectedBg
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
                            tint = TextSecondary,
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (device.isActive) ChipUnselectedBg else ObsidianBg)
                                    .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp))
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
                                            .background(EmeraldAccent)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = Color.Black
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
                        containerColor = EmeraldAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(50),
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

        // Subtle horizontal dB guides
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

            val lineTint = if (enabled) EmeraldAccent else TextMuted
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineTint.copy(alpha = if (enabled) 0.18f else 0.06f),
                        Color.Transparent
                    ),
                    startY = padY,
                    endY = h - padY
                )
            )

            drawPath(
                path = linePath,
                color = lineTint,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEach { pt ->
                drawCircle(
                    color = if (enabled) Color.White else TextMuted,
                    radius = 3.5.dp.toPx(),
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
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dbValue = (levelMb / 100f).roundToInt()
    val dbText = if (dbValue > 0) "+${dbValue}dB" else "${dbValue}dB"
    val activeColor = if (enabled) EmeraldAccent else TextMuted

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dbText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = if (enabled && dbValue != 0) activeColor else TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(124.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun updateFromY(posY: Float) {
                        val pad = 8.dp.toPx()
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
                val topY = 8.dp.toPx()
                val bottomY = size.height - 8.dp.toPx()
                val usableH = (bottomY - topY).coerceAtLeast(1f)
                val norm = ((levelMb.toFloat() - MIN_MB) / (MAX_MB - MIN_MB)).coerceIn(0f, 1f)
                val thumbY = topY + (1f - norm) * usableH
                val zeroY = topY + 0.5f * usableH

                drawLine(
                    color = SliderInactiveTrack,
                    start = Offset(trackX, topY),
                    end = Offset(trackX, bottomY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = activeColor,
                    start = Offset(trackX, zeroY),
                    end = Offset(trackX, thumbY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = if (enabled) Color.White else TextSecondary,
                    radius = 5.5.dp.toPx(),
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
private fun LabeledSlimSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
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
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        SonoraSlimSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            activeColor = EmeraldAccent,
            inactiveColor = SliderInactiveTrack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EmeraldAccent else ChipUnselectedBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontFeatureSettings = "tnum"
            ),
            color = if (selected) Color.Black else TextPrimary
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
            .clip(RoundedCornerShape(14.dp))
            .background(StudioCardBg)
            .border(1.dp, StudioCardBorder, RoundedCornerShape(14.dp))
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
