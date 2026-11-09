package com.stellarite.cyberskinos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Two small pieces of motion, kept light on purpose: now and then the hologram panels shimmer, as if the projection
 * wavered, and a scan line sweeps down the screen when you change page. Both stop when Motion is off.
 */
object StyleMotion {
    /** 1 = steady. Panels read this while drawing, so a shimmer repaints frames without recomposing anything. */
    var flicker by mutableFloatStateOf(1f)
}

/** Runs the panel shimmer while the home screen is showing and motion is allowed. */
@Composable
fun HoloShimmer(active: Boolean, animations: Boolean) {
    LaunchedEffect(active, animations) {
        StyleMotion.flicker = 1f
        if (!active || !animations) return@LaunchedEffect
        while (true) {
            delay((8000L..18000L).random())
            for (v in listOf(0.55f, 1f, 0.7f, 1f, 0.45f, 0.9f, 1f)) {
                StyleMotion.flicker = v
                delay(45L + (0..40).random())
            }
        }
    }
}

/** The scan line. Sweeps once from top to bottom each time [trigger] changes (not on the first draw). */
@Composable
fun ScanLine(trigger: Int) {
    val animate = LocalAnimations.current
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val v = progress.value
    if (v >= 1f) return
    val glow = Holo.signal.glow
    val lit = Holo.signal.lit
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * v
        val trail = size.height * 0.14f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, glow.copy(alpha = 0.16f * (1f - v))), startY = y - trail, endY = y),
            Offset(0f, y - trail), Size(size.width, trail),
        )
        drawLine(lit.copy(alpha = 0.85f * (1f - v)), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
    }
}
