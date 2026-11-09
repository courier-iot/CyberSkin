package com.stellarite.cyberskinos

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal fun polar(c: Offset, r: Float, deg: Double) = Offset(
    c.x + r * cos(Math.toRadians(deg)).toFloat(),
    c.y + r * sin(Math.toRadians(deg)).toFloat(),
)

/** A turning angle in degrees, driven frame by frame so a change of motion speed takes effect at once. */
@Composable
private fun turningAngle(periodSecs: Float, reverse: Boolean, animate: Boolean): MutableFloatState {
    val angle = remember { mutableFloatStateOf(0f) }
    val period by rememberUpdatedState(periodSecs)
    val dir by rememberUpdatedState(if (reverse) -1f else 1f)
    if (animate) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val turns = (now - last) / 1_000_000_000f / (period * Holo.spin)
                        angle.floatValue = (angle.floatValue + dir * turns * 360f) % 360f
                    }
                    last = now
                }
            }
        }
    }
    return angle // read it inside drawing code, so turning repaints without recomposing
}

/** A slowly turning ring of projected light: segments on the outside, ticks inside. */
@Composable
fun Rotor(
    modifier: Modifier = Modifier,
    segments: Int = 12,
    speed: Float = 12f, // seconds per full turn
    reverse: Boolean = false,
    animate: Boolean = LocalAnimations.current,
) {
    val angle = turningAngle(speed, reverse, animate)
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val sweep = 360f / segments
        val a = angle.floatValue
        rotate(a, c) {
            for (i in 0 until segments) {
                drawArc(
                    Holo.Line, startAngle = i * sweep + sweep * 0.12f, sweepAngle = sweep * 0.62f, useCenter = false,
                    topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f), size = Size(r * 1.8f, r * 1.8f),
                    style = Stroke(r * 0.1f, cap = StrokeCap.Butt),
                )
            }
        }
        rotate(-a * 0.6f, c) {
            for (i in 0 until 36) {
                val deg = i * 10.0
                val long = i % 3 == 0
                drawLine(Holo.Bright.copy(alpha = if (long) 0.8f else 0.4f), polar(c, r * (if (long) 0.56f else 0.62f), deg), polar(c, r * 0.68f, deg), strokeWidth = r * 0.025f)
            }
        }
        drawCircle(Holo.Dim, r * 0.42f, c, style = Stroke(r * 0.03f))
    }
}

/** A few stars, nebula light and an orbit ring or two. Drawn behind every screen. */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier, wall: ImageBitmap? = Holo.wallpaper) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Holo.Hull, Holo.Void)))) {
        if (wall != null) {
            Image(wall, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Holo.wallDim)))
        } else {
            val glowA = Holo.signal.glow
            val glowB = Holo.Ok
            val stars = remember {
                val r = java.util.Random(7)
                List(90) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
            }
            Canvas(Modifier.fillMaxSize()) {
                // two soft nebula clouds in the signal colour and the secondary light
                drawRect(Brush.radialGradient(listOf(glowA.copy(alpha = 0.14f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.08f), size.height * 0.45f))
                drawRect(Brush.radialGradient(listOf(glowB.copy(alpha = 0.06f), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.8f), size.height * 0.4f))
                // a faint navigation grid
                val step = 40.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(Holo.Line.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(Holo.Line.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                // stars: mostly tiny, a few brighter ones with a cross of light
                for (s in stars) {
                    val p = Offset(s[0] * size.width, s[1] * size.height)
                    val b = s[2]
                    drawCircle(Color.White.copy(alpha = 0.15f + 0.5f * b * b), (0.6f + 1.4f * b * b).dp.toPx() / 2f, p)
                    if (b > 0.93f) {
                        val l = 5.dp.toPx()
                        drawLine(Holo.Bright.copy(alpha = 0.35f), p - Offset(l, 0f), p + Offset(l, 0f), strokeWidth = 1f)
                        drawLine(Holo.Bright.copy(alpha = 0.35f), p - Offset(0f, l), p + Offset(0f, l), strokeWidth = 1f)
                    }
                }
            }
            OrbitRing(Modifier.size(320.dp).align(Alignment.TopEnd).offset(120.dp, (-80).dp).alpha(0.16f), speed = 60f)
            Rotor(Modifier.size(220.dp).align(Alignment.BottomStart).offset((-90).dp, 70.dp).alpha(0.07f), segments = 16, speed = 48f, reverse = true)
        }
    }
}

/** A tilted orbit with a small moon travelling round it, like the ring in the Stellarite mark. */
@Composable
fun OrbitRing(modifier: Modifier = Modifier, speed: Float = 20f, animate: Boolean = LocalAnimations.current) {
    val angle = turningAngle(speed, false, animate)
    Canvas(modifier) {
        val c = center
        val rx = size.minDimension / 2f
        val ry = rx * 0.38f
        rotate(-28f, c) {
            drawOval(Holo.Line, Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(1.5.dp.toPx()))
            val a = Math.toRadians(angle.floatValue.toDouble())
            val m = Offset(c.x + rx * cos(a).toFloat(), c.y + ry * sin(a).toFloat())
            drawCircle(Holo.Ok.copy(alpha = 0.35f), 7.dp.toPx(), m)
            drawCircle(Holo.Ok, 3.5.dp.toPx(), m)
        }
    }
}

/**
 * The Stellarite star: four long points, each split into a light and a dark facet, with a bright diamond core.
 * The upper and left points take the palette's light, the lower and right points its secondary colour.
 */
internal fun DrawScope.stellariteStar(c: Offset, r: Float) {
    val a = Holo.Line
    val aDark = lerp(Holo.Line, Color.Black, 0.35f)
    val b = Holo.Ok
    val bDark = lerp(Holo.Ok, Color.Black, 0.35f)
    val w = r * 0.16f
    fun facet(tip: Offset, side: Offset, color: Color) {
        drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(side.x, side.y); lineTo(c.x, c.y); close() }, color)
    }
    val top = Offset(c.x, c.y - r)
    val bottom = Offset(c.x, c.y + r)
    val left = Offset(c.x - r, c.y)
    val right = Offset(c.x + r, c.y)
    val tl = Offset(c.x - w, c.y - w)
    val tr = Offset(c.x + w, c.y - w)
    val bl = Offset(c.x - w, c.y + w)
    val br = Offset(c.x + w, c.y + w)
    facet(top, tl, a); facet(top, tr, aDark)
    facet(left, tl, a); facet(left, bl, aDark)
    facet(right, tr, b); facet(right, br, bDark)
    facet(bottom, bl, b); facet(bottom, br, bDark)
    val d = r * 0.1f
    drawPath(
        Path().apply { moveTo(c.x, c.y - d); lineTo(c.x + d, c.y); lineTo(c.x, c.y + d); lineTo(c.x - d, c.y); close() },
        Color(0xFFEFFFF8),
    )
}

/** Faint horizontal scan lines, as on a projected image. */
private fun DrawScope.scanLines(color: Color) {
    val step = 3.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/** Bright L-shaped brackets in two corners and a short accent bar, the frame of a hologram panel. */
private fun DrawScope.holoTrim(lit: Color, glow: Color, shimmer: Float) {
    val w = size.width
    val h = size.height
    val arm = minOf(18.dp.toPx(), w * 0.2f, h * 0.2f)
    val i = 4.dp.toPx()
    val stroke = 1.6.dp.toPx()
    val tr = Path().apply { moveTo(w - i - arm, i); lineTo(w - i, i); lineTo(w - i, i + arm) }
    val bl = Path().apply { moveTo(i, h - i - arm); lineTo(i, h - i); lineTo(i + arm, h - i) }
    for (p in listOf(tr, bl)) {
        drawPath(p, glow.copy(alpha = 0.3f * shimmer), style = Stroke(stroke * 3f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
        drawPath(p, lit.copy(alpha = 0.95f * shimmer), style = Stroke(stroke, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
    // the accent bar along the top edge, just after the cut corner
    val start = 18.dp.toPx()
    drawLine(glow.copy(alpha = 0.9f * shimmer), Offset(start, 1.dp.toPx()), Offset(minOf(start + 42.dp.toPx(), w * 0.5f), 1.dp.toPx()), strokeWidth = 2.dp.toPx())
    // a light sheen across the glass
    drawRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), Offset.Zero, Offset(w * 0.5f, h)),
        size = Size(w, h),
    )
}

/**
 * A panel of projected glass: translucent, with cut corners, a thin glowing frame, corner brackets and scan lines.
 * Every widget, menu and dialog sits on one.
 */
@Composable
fun HoloPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    fillAlpha: Float = 1f, // 1 = normal glass; lower lets more of the wallpaper show through
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
    val seed = remember { Random.nextInt() }
    val lit = Holo.signal.lit
    val glow = Holo.signal.glow
    val glass = 0.86f * fillAlpha
    Box(
        modifier
            .then(if (fillAlpha >= 0.99f) Modifier.shadow(4.dp, shape, ambientColor = glow, spotColor = glow) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Holo.Deck.copy(alpha = glass), Holo.Hull.copy(alpha = glass))))
            .border(1.dp, Brush.verticalGradient(listOf(glow.copy(alpha = 0.9f), Holo.Dim.copy(alpha = 0.7f))), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                scanLines(Holo.Line.copy(alpha = 0.035f))
                // about one panel in four shimmers fully, the rest only a little, so they feel like separate projections
                val strength = if (seed % 4 == 0) 1f else 0.3f
                holoTrim(lit, glow, 1f - strength * (1f - StyleMotion.flicker))
            }
            .padding(padding),
        content = content,
    )
}

/** A round holo button with one word inside. The outer ring of light turns a notch when pressed. */
@Composable
fun HoloButton(
    text: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 92.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val turn by animateFloatAsState(
        if (pressed) 30f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ringTurn",
    )
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "buttonScale")
    val lit = Holo.signal.lit
    val glow = Holo.signal.glow
    Box(
        modifier
            .size(diameter)
            .alpha(if (enabled) 1f else 0.4f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            rotate(turn, c) {
                for (i in 0 until 6) {
                    drawArc(
                        glow.copy(alpha = 0.85f), startAngle = i * 60f + 8f, sweepAngle = 44f, useCenter = false,
                        topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f), size = Size(r * 1.8f, r * 1.8f),
                        style = Stroke(r * 0.07f),
                    )
                }
            }
            drawCircle(
                Brush.radialGradient(
                    if (pressed) listOf(glow.copy(alpha = 0.55f), Holo.Deck) else listOf(Holo.Deck, Holo.Hull),
                    center = c, radius = r * 0.74f,
                ),
                r * 0.74f, c,
            )
            drawCircle(glow.copy(alpha = 0.3f), r * 0.74f, c, style = Stroke(r * 0.07f))
            drawCircle(lit, r * 0.74f, c, style = Stroke(r * 0.022f))
        }
        val fs = (diameter.value * 0.56f / (text.length * 0.62f)).coerceIn(9f, 15f)
        Text(
            text, fontSize = fs.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
            color = if (pressed) Holo.Text else lit, maxLines = 1, softWrap = false,
            style = TextStyle(shadow = Shadow(glow, Offset.Zero, 10f)),
        )
    }
}

/** A centred, wrapping row of buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** Every app icon sits in a ring of light, so the whole device gets the same look. */
@Composable
fun IconPort(icon: ImageBitmap, modifier: Modifier = Modifier, size: Dp = 62.dp) {
    val glow = Holo.signal.glow
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Holo.Deck, Holo.Hull), center = c, radius = r * 0.86f), r * 0.86f, c)
            drawCircle(glow.copy(alpha = 0.25f), r * 0.86f, c, style = Stroke(r * 0.08f))
            drawCircle(Holo.Line, r * 0.86f, c, style = Stroke(r * 0.03f))
            for (i in 0 until 4) {
                val deg = i * 90.0 - 45.0
                drawLine(Holo.Bright, polar(c, r * 0.9f, deg), polar(c, r * 1.0f, deg), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
            }
        }
        Image(
            icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.62f).clip(CircleShape),
            colorFilter = if (Holo.tintIcons) holoFilter() else null,
        )
    }
}

/** Holo icons: turns an icon to shades of grey, then colours the greys with the light of your palette. */
private fun holoFilter(): ColorFilter {
    val t = lerp(Holo.Bright, Color.White, 0.15f)
    val r = 0.299f
    val g = 0.587f
    val b = 0.114f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                t.red * r, t.red * g, t.red * b, 0f, 0f,
                t.green * r, t.green * g, t.green * b, 0f, 0f,
                t.blue * r, t.blue * g, t.blue * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

// ───────────────────────── Dial faces ─────────────────────────
// A dial is a ring of light around a dark glass face, with marks and needles in the signal colour.

/** Colour for scale marks and the main hand. */
private val dialMark: Color get() = Holo.signal.lit

/** Colour for the accent: the north marker, the danger zone. */
private val dialAccent: Color get() = Holo.Alert

/** The rim and face of any round dial. faceFrac is how much of the radius the face takes up. */
internal fun DrawScope.dialFace(c: Offset, r: Float, faceFrac: Float) {
    val glow = Holo.signal.glow
    drawCircle(Brush.radialGradient(listOf(Holo.Deck, Holo.Void), center = c, radius = r), r, c)
    drawCircle(Holo.Dim, r * 0.98f, c, style = Stroke(r * 0.03f))
    // segmented outer ring
    for (i in 0 until 24) {
        drawArc(
            Holo.Line.copy(alpha = 0.6f), startAngle = i * 15f + 2f, sweepAngle = 9f, useCenter = false,
            topLeft = Offset(c.x - r * 0.93f, c.y - r * 0.93f), size = Size(r * 1.86f, r * 1.86f), style = Stroke(r * 0.04f),
        )
    }
    drawCircle(Color(0xEE03050C), r * faceFrac, c)
    drawCircle(glow.copy(alpha = 0.25f), r * faceFrac, c, style = Stroke(r * 0.05f))
    drawCircle(Holo.signal.lit, r * faceFrac, c, style = Stroke(r * 0.014f))
}

/** A hand or needle with a soft glow underneath. */
internal fun DrawScope.dialHand(from: Offset, to: Offset, width: Float, color: Color) {
    drawLine(color.copy(alpha = 0.3f), from, to, strokeWidth = width * 3.2f, cap = StrokeCap.Round)
    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Round)
}

/** The centre point the hands turn on. */
internal fun DrawScope.dialHub(c: Offset, r: Float) {
    drawCircle(Holo.signal.glow, r * 0.07f, c)
    drawCircle(Holo.Text, r * 0.03f, c)
}

/** Arc gauge. value is 0..1. dangerLow puts the red zone at the low end (for a battery). */
@Composable
fun ArcGauge(value: Float, modifier: Modifier = Modifier, dangerLow: Boolean = false) {
    val v by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val glow = Holo.signal.glow
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        dialFace(c, r, 0.88f)
        val box = Offset(c.x - r * 0.72f, c.y - r * 0.72f)
        val boxSize = Size(r * 1.44f, r * 1.44f)
        // the filled part of the scale glows, the rest is a dim track
        drawArc(Holo.Dim.copy(alpha = 0.6f), 135f, 270f, false, box, boxSize, style = Stroke(r * 0.09f))
        drawArc(glow.copy(alpha = 0.3f), 135f, 270f * v, false, box, boxSize, style = Stroke(r * 0.16f))
        drawArc(glow, 135f, 270f * v, false, box, boxSize, style = Stroke(r * 0.09f))
        val zoneStart = if (dangerLow) 135f else 135f + 270f * 0.8f
        drawArc(
            dialAccent, startAngle = zoneStart, sweepAngle = 270f * 0.2f, useCenter = false,
            topLeft = Offset(c.x - r * 0.84f, c.y - r * 0.84f), size = Size(r * 1.68f, r * 1.68f), style = Stroke(r * 0.03f),
        )
        for (i in 0..10) {
            val major = i % 5 == 0
            val deg = 135.0 + 27.0 * i
            drawLine(dialMark.copy(alpha = if (major) 1f else 0.6f), polar(c, r * (if (major) 0.5f else 0.55f), deg), polar(c, r * 0.6f, deg), strokeWidth = if (major) 4f else 2f)
        }
        dialHand(c, polar(c, r * 0.5f, 135.0 + 270.0 * v), r * 0.035f, Holo.signal.lit)
        dialHub(c, r)
    }
}

/** Analogue clock face. hour is 0..12 (fractional), minute is 0..60 (fractional). */
@Composable
fun AnalogClock(hour: Float, minute: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        dialFace(c, r, 0.9f)
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val deg = i * 6.0 - 90.0
            drawLine(dialMark.copy(alpha = if (major) 1f else 0.45f), polar(c, r * (if (major) 0.72f else 0.80f), deg), polar(c, r * 0.86f, deg), strokeWidth = if (major) 4f else 2f)
        }
        dialHand(c, polar(c, r * 0.45f, hour / 12.0 * 360.0 - 90.0), r * 0.07f, Holo.Text)
        dialHand(c, polar(c, r * 0.68f, minute / 60.0 * 360.0 - 90.0), r * 0.04f, Holo.signal.glow)
        dialHub(c, r)
    }
}

/**
 * The floating Stellarite star. Tap = Databank, double tap = Console menu,
 * press and hold, then drag = move it. onMove gives the drag in dp.
 */
@Composable
fun StarFab(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onMoveEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    // A press-and-hold ends with a finger lift that would otherwise count as a tap.
    val ignoreTapUntil = remember { LongArray(1) }
    val tapNow by rememberUpdatedState(onTap)
    val doubleNow by rememberUpdatedState(onDoubleTap)
    val moveNow by rememberUpdatedState(onMove)
    val endNow by rememberUpdatedState(onMoveEnd)
    val scale by animateFloatAsState(if (moving) 1.15f else if (pressed) 0.9f else 1f, label = "fab")
    val haptic = LocalHapticFeedback.current
    val orbit = turningAngle(9f, false, LocalAnimations.current)
    val glow = Holo.signal.glow
    Box(
        modifier
            .size(72.dp)
            // gestures sit before the scaling so a drag follows the finger exactly
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onDoubleTap = { if (System.currentTimeMillis() >= ignoreTapUntil[0]) doubleNow() },
                    onTap = { if (System.currentTimeMillis() >= ignoreTapUntil[0]) tapNow() },
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        ignoreTapUntil[0] = Long.MAX_VALUE
                        moving = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        ignoreTapUntil[0] = System.currentTimeMillis() + 500L
                        moving = false
                        endNow()
                    },
                    onDragCancel = {
                        ignoreTapUntil[0] = System.currentTimeMillis() + 500L
                        moving = false
                        endNow()
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        moveNow(drag.x.toDp().value, drag.y.toDp().value)
                    },
                )
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (moving) 20.dp else 10.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Holo.Deck, Holo.Void), center = c, radius = r), r, c)
            drawCircle(glow.copy(alpha = 0.3f), r * 0.94f, c, style = Stroke(r * 0.08f))
            drawCircle(Holo.signal.lit, r * 0.94f, c, style = Stroke(r * 0.025f))
            // the tilted orbit with its moon, then the star on top
            val rx = r * 0.78f
            val ry = rx * 0.4f
            rotate(-28f, c) {
                drawOval(Holo.Line.copy(alpha = 0.7f), Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(r * 0.035f))
            }
            stellariteStar(c, r * 0.66f)
            rotate(-28f, c) {
                val a = Math.toRadians(orbit.floatValue.toDouble())
                val sinA = sin(a).toFloat()
                // only draw the moon on the near half of the orbit, so it passes behind the star
                if (sinA > -0.2f) drawCircle(Holo.Ok, r * 0.09f, Offset(c.x + rx * cos(a).toFloat(), c.y + ry * sinA))
            }
        }
    }
}

// ───────────────────────── Holo digits ─────────────────────────

/** One projected digit cell: dark glass with scan lines, a ghost 8 and the lit digit. */
@Composable
private fun HoloCell(digit: Char, w: Dp, h: Dp) {
    val glass = CutCornerShape(topStart = w * 0.18f, bottomEnd = w * 0.18f)
    val fs = with(LocalDensity.current) { (h * 0.62f).toSp() }
    val glow = Holo.signal.glow
    val lit = Holo.signal.lit
    Box(
        Modifier.size(w, h).clip(glass)
            .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.10f), Color(0xCC02040A))))
            .border(1.dp, Brush.verticalGradient(listOf(glow.copy(alpha = 0.9f), Holo.Dim.copy(alpha = 0.5f))), glass),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            scanLines(glow.copy(alpha = 0.08f))
            // the emitter strip at the base of the cell
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow.copy(alpha = 0.5f))), topLeft = Offset(0f, size.height * 0.86f), size = Size(size.width, size.height * 0.14f))
        }
        Text("8", fontSize = fs, fontFamily = FontFamily.Monospace, color = glow.copy(alpha = 0.10f))
        if (digit != ' ') {
            Text(
                digit.toString(), fontSize = fs, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                color = lit, style = TextStyle(shadow = Shadow(glow, Offset.Zero, 22f)),
            )
        }
    }
}

@Composable
private fun HoloColon(w: Dp, h: Dp) {
    val pulse = if (LocalAnimations.current) {
        val t = rememberInfiniteTransition(label = "holoColon")
        val a by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    } else 1f
    Canvas(Modifier.size(w, h)) {
        val s = size.width * 0.3f
        for (f in listOf(0.36f, 0.64f)) {
            val c = Offset(size.width / 2f, size.height * f)
            drawCircle(Holo.signal.glow.copy(alpha = 0.3f * pulse), s * 1.6f, c)
            drawRect(Holo.signal.lit.copy(alpha = pulse), Offset(c.x - s / 2f, c.y - s / 2f), Size(s, s))
        }
    }
}

/** Four projected digit cells (HHMM, a space gives an empty cell) with a pulsing colon and a date line. */
@Composable
fun HoloClock(hhmm: String, date: String?, modifier: Modifier = Modifier, badge: String? = null) =
    BoxWithConstraints(modifier) {
        val mw = maxWidth
        val mh = maxHeight
        val gap = 4.dp
        val dateH = if (date != null && mh > 80.dp) 30.dp else 0.dp
        val reserve = if (badge != null) 26.dp else 0.dp
        val th = maxOf(minOf(mh - dateH, (mw - gap * 4 - reserve) / 2.64f), 20.dp)
        val tw = th * 0.56f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                HoloCell(hhmm[0], tw, th)
                HoloCell(hhmm[1], tw, th)
                HoloColon(tw * 0.4f, th)
                HoloCell(hhmm[2], tw, th)
                HoloCell(hhmm[3], tw, th)
                if (badge != null) {
                    Column(Modifier.height(th), verticalArrangement = Arrangement.Bottom) {
                        Text(
                            badge, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                            color = Holo.signal.lit, style = TextStyle(shadow = Shadow(Holo.signal.glow, Offset.Zero, 16f)),
                        )
                    }
                }
            }
            if (dateH > 0.dp && date != null) {
                Text(
                    date.uppercase(), fontSize = 12.sp, fontFamily = Holo.font, letterSpacing = 2.sp, color = Holo.Text, maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp).wrapContentHeight(Alignment.Top, unbounded = true),
                )
            }
        }
    }

// ───────────────────────── Compass, moon and sun drawings ─────────────────────────

/** A navigation compass. The card turns so your heading (degrees, 0 = north) sits at the top. */
@Composable
fun CompassRose(heading: Float, modifier: Modifier = Modifier) = BoxWithConstraints(modifier.aspectRatio(1f)) {
    val d = minOf(maxWidth, maxHeight)
    val fs = with(LocalDensity.current) { (d * 0.11f).toSp() }
    val inset = d * 0.15f
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        dialFace(c, r, 0.88f)
    }
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = -heading }) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 0 until 36) {
                val major = i % 3 == 0
                val deg = i * 10.0 - 90.0
                drawLine(dialMark.copy(alpha = if (major) 1f else 0.5f), polar(c, r * (if (major) 0.78f else 0.81f), deg), polar(c, r * 0.85f, deg), strokeWidth = if (major) 3f else 1.5f)
            }
            val north = Path().apply {
                moveTo(c.x, c.y - r * 0.40f); lineTo(c.x - r * 0.08f, c.y); lineTo(c.x + r * 0.08f, c.y); close()
            }
            val south = Path().apply {
                moveTo(c.x, c.y + r * 0.40f); lineTo(c.x - r * 0.08f, c.y); lineTo(c.x + r * 0.08f, c.y); close()
            }
            drawPath(north, dialAccent.copy(alpha = 0.3f), style = Stroke(r * 0.06f, join = StrokeJoin.Round))
            drawPath(north, dialAccent)
            drawPath(south, dialMark.copy(alpha = 0.5f))
            dialHub(c, r)
        }
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = inset), fontSize = fs, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = dialAccent)
        Text("E", Modifier.align(Alignment.CenterEnd).padding(end = inset), fontSize = fs, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = dialMark)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = inset), fontSize = fs, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = dialMark)
        Text("W", Modifier.align(Alignment.CenterStart).padding(start = inset), fontSize = fs, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = dialMark)
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        val mark = Path().apply {
            moveTo(c.x, c.y - r * 0.80f); lineTo(c.x - r * 0.06f, c.y - r * 0.93f); lineTo(c.x + r * 0.06f, c.y - r * 0.93f); close()
        }
        drawPath(mark, Holo.signal.lit)
    }
}

/** The moon as it looks tonight, as a holographic scan. ageDays is days since the new moon (0 to 29.53). */
@Composable
fun MoonDisc(ageDays: Double, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val mr = r * 0.82f
        val f = (ageDays / 29.530588853).toFloat().coerceIn(0f, 1f)
        dialFace(c, r, 0.88f)
        drawCircle(Holo.Dim.copy(alpha = 0.35f), mr, c) // the dark side
        drawCircle(Holo.Line.copy(alpha = 0.5f), mr, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        val waxing = f <= 0.5f
        val q = if (waxing) f else 1f - f // 0 = new, 0.5 = full
        val k = cos(2.0 * Math.PI * q).toFloat() // 1 = new ... -1 = full
        val rx = maxOf(abs(k) * mr, 0.5f)
        val lit = Path().apply {
            arcTo(Rect(c.x - mr, c.y - mr, c.x + mr, c.y + mr), -90f, 180f, true)
            arcTo(Rect(c.x - rx, c.y - mr, c.x + rx, c.y + mr), 90f, if (k > 0f) -180f else 180f, false)
            close()
        }
        scale(if (waxing) 1f else -1f, 1f, c) {
            clipPath(lit) {
                drawCircle(
                    Brush.radialGradient(listOf(Holo.signal.lit, Holo.signal.glow.copy(alpha = 0.8f)), center = Offset(c.x - mr * 0.2f, c.y - mr * 0.2f), radius = mr * 1.4f),
                    mr, c,
                )
                val craters = listOf(
                    Triple(-0.30f, -0.25f, 0.16f), Triple(0.25f, -0.35f, 0.10f), Triple(0.10f, 0.20f, 0.20f),
                    Triple(-0.35f, 0.35f, 0.09f), Triple(0.45f, 0.30f, 0.07f),
                )
                for ((dx, dy, rr) in craters) drawCircle(Color(0x33000814), mr * rr, Offset(c.x + mr * dx, c.y + mr * dy))
                scanLines(Color(0x22000814))
            }
        }
    }
}

/** The sun's path across the sky. progress is 0 at sunrise to 1 at sunset, or null at night. */
@Composable
fun SunArc(progress: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * 0.82f
        val rad = minOf(w * 0.42f, baseY - 6f)
        val cx = w / 2f
        val horizon = Holo.signal.glow
        val track = Holo.signal.lit
        drawLine(horizon.copy(alpha = 0.3f), Offset(cx - rad - 10f, baseY), Offset(cx + rad + 10f, baseY), strokeWidth = 9f, cap = StrokeCap.Round)
        drawLine(horizon, Offset(cx - rad - 10f, baseY), Offset(cx + rad + 10f, baseY), strokeWidth = 3f, cap = StrokeCap.Round)
        drawArc(
            track.copy(alpha = 0.8f), 180f, 180f, false, Offset(cx - rad, baseY - rad), Size(rad * 2f, rad * 2f),
            style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
        if (progress != null) {
            val th = Math.PI * (1.0 - progress)
            val sx = cx + rad * cos(th).toFloat()
            val sy = baseY - rad * sin(th).toFloat()
            drawCircle(Holo.Ok.copy(alpha = 0.3f), 16f, Offset(sx, sy))
            drawCircle(Holo.Ok, 9f, Offset(sx, sy))
        }
    }
}
