package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SoundWaveCyan
import com.example.ui.theme.SoundWavePurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SoundWaveVisualizer(
    isPlaying: Boolean,
    isBuffering: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    height: Dp = 68.dp
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "sound_wave_transition")

    // Phases for smooth multi-harmonic wave oscillation at 60/120fps
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Smooth transition between playing, buffering, and idle states
    val playProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "play_progress"
    )

    val bufferProgress by animateFloatAsState(
        targetValue = if (isBuffering && !isPlaying) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "buffer_progress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Sizing calculations ensuring bars have a proper pill shape and clean spacing
        val totalSpacingRatio = 0.42f
        val availableBarWidth = (canvasWidth * (1f - totalSpacingRatio)) / barCount
        val barWidth = availableBarWidth.coerceIn(5f, 16f)
        val totalBarsWidth = barWidth * barCount
        val spacing = (canvasWidth - totalBarsWidth) / (barCount + 1)

        val brush = Brush.verticalGradient(
            colors = listOf(
                SoundWaveCyan,
                primaryColor,
                SoundWavePurple
            )
        )

        val minBarHeight = (canvasHeight * 0.14f).coerceAtLeast(10f)

        for (i in 0 until barCount) {
            val normalizedI = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            // Frequency profile: higher in the mid-bass to mid-treble, tapering gracefully at extremes
            val spectrumWeight = sin(normalizedI * PI).toFloat()
            val baseProfile = 0.35f + 0.65f * spectrumWeight

            // Live playing wave dynamics: mix of traveling waves and standing harmonics
            val wave1 = sin(phase1 + i * 0.42f)
            val wave2 = cos(phase2 - i * 0.31f)
            val wave3 = sin(phase3 + i * 0.75f)
            val combinedLive = ((wave1 * 0.42f + wave2 * 0.38f + wave3 * 0.20f + 1f) / 2f).toFloat()
            val liveHeight = minBarHeight + (canvasHeight - minBarHeight) * (combinedLive * baseProfile).coerceIn(0.08f, 0.96f)

            // Buffering wave dynamics: smooth scanning wave across all bars
            val bufferWave = ((sin(phase1 * 1.5f - i * 0.45f) + 1f) / 2f).toFloat()
            val bufferHeight = minBarHeight + (canvasHeight * 0.48f) * bufferWave

            // Idle resting height: elegant calm baseline curve with clean pills (no dots)
            val idleHeight = minBarHeight + (canvasHeight * 0.10f) * spectrumWeight

            // Interpolate according to current playback state
            val finalBarHeight = when {
                playProgress > 0f -> androidx.compose.ui.util.lerp(idleHeight, liveHeight, playProgress)
                bufferProgress > 0f -> androidx.compose.ui.util.lerp(idleHeight, bufferHeight, bufferProgress)
                else -> idleHeight
            }

            val x = spacing + i * (barWidth + spacing)
            val y = (canvasHeight - finalBarHeight) / 2f

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, finalBarHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
