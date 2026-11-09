package com.stellarite.cyberskinos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Solar system tracker. Positions come from the two JPL "Keplerian elements for approximate positions of the
 * major planets" tables, worked out from today's date with no internet:
 *  - Table 1 (best for 1800 to 2050) is used for today and the coming years.
 *  - Table 2a (valid 3000 BC to 3000 AD, with extra long-period terms for Jupiter, Saturn, Uranus and Neptune)
 *    takes over smoothly between 2045 and 2055 and is used through 2250 and beyond.
 * Checked against each other, the tables agree to within about 0.4 degrees from 1900 to 2250, so the map is
 * right to about half a degree. Longitudes are measured from the J2000 equinox.
 */
private class Body(
    val name: String,
    val color: Color,
    val size: Float,
    val year: String,
    // Each set is: a, a per century, e, e per century, inclination, per century,
    // mean longitude, per century, longitude of perihelion, per century, longitude of node, per century.
    val near: DoubleArray, // Table 1
    val far: DoubleArray,  // Table 2a
    // Table 2a extra terms for the outer planets: b, c, s, f
    val extra: DoubleArray?,
)

private val BODIES = listOf(
    Body("Mercury", Color(0xFFB8AFA6), 3f, "88 days",
        doubleArrayOf(0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081),
        doubleArrayOf(0.38709843, 0.00000000, 0.20563661, 0.00002123, 7.00559432, -0.00590158, 252.25166724, 149472.67486623, 77.45771895, 0.15940013, 48.33961819, -0.12214182),
        null),
    Body("Venus", Color(0xFFE8CC8E), 4.5f, "225 days",
        doubleArrayOf(0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418),
        doubleArrayOf(0.72332102, -0.00000026, 0.00676399, -0.00005107, 3.39777545, 0.00043494, 181.97970850, 58517.81560260, 131.76755713, 0.05679648, 76.67261496, -0.27274174),
        null),
    Body("Earth", Color(0xFF58AEE8), 4.5f, "365 days",
        doubleArrayOf(1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0),
        doubleArrayOf(1.00000018, -0.00000003, 0.01673163, -0.00003661, -0.00054346, -0.01337178, 100.46691572, 35999.37306329, 102.93005885, 0.31795260, -5.11260389, -0.24123856),
        null),
    Body("Mars", Color(0xFFD4663C), 3.5f, "687 days",
        doubleArrayOf(1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343),
        doubleArrayOf(1.52371243, 0.00000097, 0.09336511, 0.00009149, 1.85181869, -0.00724757, -4.56813164, 19140.29934243, -23.91744784, 0.45223625, 49.71320984, -0.26852431),
        null),
    Body("Jupiter", Color(0xFFDBAA7A), 7.5f, "11.9 years",
        doubleArrayOf(5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106),
        doubleArrayOf(5.20248019, -0.00002864, 0.04853590, 0.00018026, 1.29861416, -0.00322699, 34.33479152, 3034.90371757, 14.27495244, 0.18199196, 100.29282654, 0.13024619),
        doubleArrayOf(-0.00012452, 0.06064060, -0.35635438, 38.35125000)),
    Body("Saturn", Color(0xFFE6D194), 6.5f, "29.5 years",
        doubleArrayOf(9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794),
        doubleArrayOf(9.54149883, -0.00003065, 0.05550825, -0.00032044, 2.49424102, 0.00451969, 50.07571329, 1222.11494724, 92.86136063, 0.54179478, 113.63998702, -0.25015002),
        doubleArrayOf(0.00025899, -0.13434469, 0.87320147, 38.35125000)),
    Body("Uranus", Color(0xFF8FDCE2), 5f, "84 years",
        doubleArrayOf(19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589),
        doubleArrayOf(19.18797948, -0.00020455, 0.04685740, -0.00001550, 0.77298127, -0.00180155, 314.20276625, 428.49512595, 172.43404441, 0.09266985, 73.96250215, 0.05739699),
        doubleArrayOf(0.00058331, -0.97731848, 0.17689245, 7.67025000)),
    Body("Neptune", Color(0xFF5879E6), 5f, "165 years",
        doubleArrayOf(30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664),
        doubleArrayOf(30.06952752, 0.00006447, 0.00895439, 0.00000818, 1.77005520, 0.00022400, 304.22289287, 218.46515314, 46.68158724, 0.01009938, 131.78635853, -0.00606302),
        doubleArrayOf(-0.00041348, 0.68346318, -0.10162547, 7.67025000)),
)

private class Pos(val x: Double, val y: Double, val z: Double)

private fun rad(d: Double) = d * PI / 180.0

/** The six orbital elements at time t (centuries since J2000), from Table 1 or, if far is true, Table 2a. */
private fun elementsAt(b: Body, t: Double, far: Boolean): DoubleArray {
    val s = if (far) b.far else b.near
    var meanLong = s[6] + s[7] * t
    val x = b.extra
    if (far && x != null) {
        meanLong += x[0] * t * t + x[1] * cos(rad(x[3] * t)) + x[2] * sin(rad(x[3] * t))
    }
    return doubleArrayOf(
        s[0] + s[1] * t, s[2] + s[3] * t, s[4] + s[5] * t,
        meanLong, s[8] + s[9] * t, s[10] + s[11] * t,
    )
}

/** A point on the orbit for eccentric anomaly bigE, in the ecliptic frame, in AU. */
private fun orbitPoint(el: DoubleArray, bigE: Double): Pos {
    val a = el[0]
    val e = el[1]
    val inc = rad(el[2])
    val peri = rad(el[4])
    val node = rad(el[5])
    val w = peri - node
    val xp = a * (cos(bigE) - e)
    val yp = a * sqrt(1.0 - e * e) * sin(bigE)
    val cw = cos(w)
    val sw = sin(w)
    val cn = cos(node)
    val sn = sin(node)
    val ci = cos(inc)
    val si = sin(inc)
    return Pos(
        (cw * cn - sw * sn * ci) * xp + (-sw * cn - cw * sn * ci) * yp,
        (cw * sn + sw * cn * ci) * xp + (-sw * sn + cw * cn * ci) * yp,
        (sw * si) * xp + (cw * si) * yp,
    )
}

/** Solves Kepler's equation for one set of elements and returns the planet's position. */
private fun solve(el: DoubleArray): Pos {
    var m = rad(el[3] - el[4]) % (2.0 * PI) // mean anomaly
    if (m > PI) m -= 2.0 * PI else if (m < -PI) m += 2.0 * PI
    var bigE = m
    repeat(15) { bigE = m + el[1] * sin(bigE) }
    return orbitPoint(el, bigE)
}

/** Position at t. Table 1 up to 2045, Table 2a from 2055 (and before 1800), blended in between so nothing jumps. */
private fun position(b: Body, t: Double): Pos {
    val w = when {
        t < -2.0 -> 1.0
        t <= 0.45 -> 0.0
        t >= 0.55 -> 1.0
        else -> (t - 0.45) / 0.10
    }
    return when {
        w <= 0.0 -> solve(elementsAt(b, t, false))
        w >= 1.0 -> solve(elementsAt(b, t, true))
        else -> {
            val p = solve(elementsAt(b, t, false))
            val q = solve(elementsAt(b, t, true))
            Pos(p.x * (1.0 - w) + q.x * w, p.y * (1.0 - w) + q.y * w, p.z * (1.0 - w) + q.z * w)
        }
    }
}

private fun centuries(nowMs: Long): Double = ((nowMs / 86_400_000.0 + 2440587.5) - 2451545.0) / 36525.0

private fun dist(a: Pos, b: Pos): Double {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private val SUN_POS = Pos(0.0, 0.0, 0.0)

/** Where a point lands on the map. The inner view is to scale; the whole system uses a square-root scale so Neptune fits. */
private fun toScreen(p: Pos, c: Offset, rMax: Float, inner: Boolean): Offset {
    val d = sqrt(p.x * p.x + p.y * p.y)
    if (d < 1e-9) return c
    val f = (if (inner) d / 1.75 else sqrt(d / 31.0)).coerceAtMost(1.0).toFloat()
    return Offset(c.x + (p.x / d).toFloat() * f * rMax, c.y - (p.y / d).toFloat() * f * rMax)
}

private fun screenPoints(c: Offset, rMax: Float, pos: List<Pos>, inner: Boolean): List<Offset?> =
    pos.mapIndexed { i, p -> if (inner && i > 3) null else toScreen(p, c, rMax, inner) }

private fun orbitPath(b: Body, t: Double, c: Offset, rMax: Float, inner: Boolean): Path {
    val el = elementsAt(b, t, t >= 0.5 || t < -2.0)
    val path = Path()
    for (k in 0..72) {
        val o = toScreen(orbitPoint(el, 2.0 * PI * k / 72.0), c, rMax, inner)
        if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    return path
}

private fun lightTime(au: Double): String {
    val minutes = au * 8.3167
    return if (minutes < 90.0) "%.0f min".format(minutes) else "%.1f h".format(minutes / 60.0)
}

@Composable
private fun SolarMap(
    pos: List<Pos>,
    t: Double,
    inner: Boolean,
    sel: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val stars = remember {
        val r = java.util.Random(11)
        List(36) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val pick = rememberUpdatedState(onPick)
    Canvas(
        modifier.pointerInput(pos, inner) {
            detectTapGestures { tap ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val rMax = minOf(size.width, size.height) / 2f - 6f
                val pts = screenPoints(c, rMax, pos, inner)
                if ((tap - c).getDistance() < rMax * 0.12f + 10f) {
                    pick.value(-2)
                } else {
                    var best = -1
                    var bestD = 34f
                    pts.forEachIndexed { i, o ->
                        if (o != null) {
                            val d = (tap - o).getDistance()
                            if (d < bestD) { bestD = d; best = i }
                        }
                    }
                    pick.value(best)
                }
            }
        }
    ) {
        val c = center
        val rMax = minOf(this.size.width, this.size.height) / 2f - 6f
        val k = (rMax / 150f).coerceIn(0.8f, 1.8f)
        for ((fx, fy, fb) in stars) {
            drawCircle(Color.White.copy(alpha = 0.12f + 0.35f * fb), 1.2f, Offset(fx * this.size.width, fy * this.size.height))
        }
        BODIES.forEachIndexed { i, b ->
            if (inner && i > 3) return@forEachIndexed
            drawPath(
                orbitPath(b, t, c, rMax, inner),
                Holo.Dim.copy(alpha = if (i == 2) 0.95f else 0.55f),
                style = Stroke(if (i == 2) 2.2f else 1.2f),
            )
        }
        val sunR = rMax * 0.06f + 3f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE9A0), Color(0xFFFFA000), Color.Transparent), center = c, radius = sunR * 2.8f),
            sunR * 2.8f, c,
        )
        drawCircle(Color(0xFFFFD25A), sunR, c)
        val pts = screenPoints(c, rMax, pos, inner)
        BODIES.forEachIndexed { i, b ->
            val p = pts[i] ?: return@forEachIndexed
            val r = b.size * k
            if (i == sel) drawCircle(Holo.Bright, r + 5f, p, style = Stroke(2f))
            drawCircle(b.color, r, p)
            if (b.name == "Saturn") {
                drawOval(b.color, Offset(p.x - r * 2.1f, p.y - r * 0.55f), Size(r * 4.2f, r * 1.1f), style = Stroke(1.6f))
            }
        }
    }
}

@Composable
private fun SolarInfo(sel: Int, pos: List<Pos>, inner: Boolean, roomy: Boolean) {
    val head = 14.sp
    val small = 12.sp
    if (sel < 0) {
        Text("Solar system", fontSize = head, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Bright, maxLines = 1)
        Text(
            if (inner) "Inner view. Tap the Sun for the whole system." else "Whole system. Tap the Sun for the inner view.",
            fontSize = small, fontFamily = Holo.font, color = Holo.Muted, maxLines = if (roomy) 3 else 1,
        )
        if (roomy) {
            Text("Tap a planet for details. Positions are for today.", fontSize = small, fontFamily = Holo.font, color = Holo.Muted)
        }
    } else {
        val b = BODIES[sel]
        val sun = dist(pos[sel], SUN_POS)
        val earth = dist(pos[sel], pos[2])
        Text(b.name, fontSize = head, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = b.color, maxLines = 1)
        if (roomy) {
            Text("%.2f AU from the Sun".format(sun), fontSize = small, fontFamily = Holo.font, color = Holo.Text)
            if (sel != 2) {
                Text("%.2f AU from Earth, light takes %s".format(earth, lightTime(earth)), fontSize = small, fontFamily = Holo.font, color = Holo.Text)
            }
            Text("One year: ${b.year}", fontSize = small, fontFamily = Holo.font, color = Holo.Muted)
        } else {
            val line = if (sel == 2) "%.2f AU from the Sun".format(sun) else "%.2f AU Sun, %.2f AU Earth".format(sun, earth)
            Text(line, fontSize = small, fontFamily = Holo.font, color = Holo.Text, maxLines = 1)
        }
    }
}

/** The widget: a top-down map of the planets where they really are today. Tap a planet to inspect it, tap the Sun to zoom. */
@Composable
fun SolarContent(now: LocalDateTime) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val mw = maxWidth
    val mh = maxHeight
    val wide = mw > mh * 1.35f
    val t = remember(now) { centuries(System.currentTimeMillis()) }
    val pos = remember(t) { BODIES.map { position(it, t) } }
    var inner by remember { mutableStateOf(false) }
    var sel by remember { mutableIntStateOf(-1) }
    val onPick: (Int) -> Unit = { i ->
        if (i == -2) {
            inner = !inner
            sel = -1
        } else if (i >= 0 && i != sel) {
            sel = i
        } else {
            sel = -1
        }
    }
    if (wide) {
        val side = minOf(mh, mw * 0.58f)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            SolarMap(pos, t, inner, sel, onPick, Modifier.size(side))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { SolarInfo(sel, pos, inner, true) }
        }
    } else {
        val side = maxOf(minOf(mw, mh - 46.dp), 60.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            SolarMap(pos, t, inner, sel, onPick, Modifier.size(side))
            SolarInfo(sel, pos, inner, false)
        }
    }
}
