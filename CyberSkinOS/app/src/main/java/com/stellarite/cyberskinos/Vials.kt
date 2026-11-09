package com.stellarite.cyberskinos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

/** One thing a Vitals bar can measure. */
class VialMetric(val key: String, val label: String, val blurb: String)

/** Every reading a Vitals widget can show, in the order they are offered. */
val VIAL_METRICS = listOf(
    VialMetric("battery", "Power", "Battery charge"),
    VialMetric("storage", "Storage", "Storage used"),
    VialMetric("memory", "Memory", "RAM in use"),
    VialMetric("heat", "Heat", "Battery temperature"),
    VialMetric("pressure", "Atmos", "Air pressure (needs a sensor)"),
    VialMetric("day", "Day", "How much of today has passed"),
    VialMetric("net", "Uplink", "Network speed, down and up together"),
    VialMetric("brightness", "Light", "Screen brightness"),
    VialMetric("volume", "Volume", "Media volume"),
)

/** Bar colours to choose from. The first entry means "automatic": the colour follows how worrying the level is. */
val VIAL_COLOURS: List<Pair<String, Int?>> = listOf(
    "Auto" to null,
    "Teal" to 0xFF00C2B3.toInt(),
    "Lime" to 0xFFB8F000.toInt(),
    "Cyan" to 0xFF1FC8FF.toInt(),
    "Blue" to 0xFF3D7BFF.toInt(),
    "Violet" to 0xFF8A4DFF.toInt(),
    "Magenta" to 0xFFFF2DB4.toInt(),
)

val DEFAULT_VIALS = listOf("battery", "storage", "memory")

/** The most bars one widget can hold. */
const val MAX_VIALS = 6

/** What one bar shows. [bad] says how worrying the level is, 0 (fine) to 1 (alarming), or null for a neutral reading. */
private class Reading(val key: String, val label: String, val fill: Float, val text: String, val bad: Float?, val exact: String = text)

private fun dayFraction(): Float = LocalTime.now().toSecondOfDay() / 86_400f

/** Bytes per second as a short figure. */
private fun speedText(bps: Float): String = when {
    bps >= 1_048_576f -> "%.1f MB/s".format(bps / 1_048_576f)
    bps >= 1024f -> "${(bps / 1024f).roundToInt()} KB/s"
    else -> "${bps.roundToInt()} B/s"
}

private fun hoursText(sec: Int): String = "%d:%02d".format(sec / 3600, sec / 60 % 60)

/**
 * A stack of holographic level bars that fill from left to right. Which readings it shows is chosen with the ⚙
 * on the widget in Arrange mode. They are drawn in the order picked.
 */
@Composable
fun VialsContent(keys: List<String>, fahrenheit: Boolean, colours: Map<String, Int> = emptyMap()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    var picked by remember { mutableStateOf<String?>(null) } // the bar whose exact figure is showing
    var netRate by remember { mutableFloatStateOf(0f) } // bytes per second
    var brightness by remember { mutableFloatStateOf(0f) }
    var volume by remember { mutableFloatStateOf(0f) }
    var volumeRaw by remember { mutableStateOf(0 to 0) }
    var charging by remember { mutableStateOf(false) }
    var freeGb by remember { mutableFloatStateOf(0f) }
    var memFreeMb by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(picked) {
        if (picked != null) {
            delay(3500)
            picked = null
        }
    }
    // network speed is sampled every second, and only while an Uplink bar is chosen
    val wantNet = "net" in keys
    LaunchedEffect(active, wantNet) {
        if (!active || !wantNet) return@LaunchedEffect
        var last = android.net.TrafficStats.getTotalRxBytes() + android.net.TrafficStats.getTotalTxBytes()
        var lastAt = System.nanoTime()
        while (true) {
            delay(1000)
            val now = android.net.TrafficStats.getTotalRxBytes() + android.net.TrafficStats.getTotalTxBytes()
            val at = System.nanoTime()
            val secs = (at - lastAt) / 1e9f
            netRate = if (now < 0L || last < 0L || secs <= 0f) 0f else ((now - last) / secs).coerceAtLeast(0f)
            last = now
            lastAt = at
        }
    }
    var battery by remember { mutableFloatStateOf(batteryLevel(ctx)) }
    var storage by remember { mutableFloatStateOf(storageUse().first) }
    var mem by remember { mutableFloatStateOf(0f) }
    var heat by remember { mutableFloatStateOf(-1f) } // degrees C, -1 = unknown
    var day by remember { mutableFloatStateOf(dayFraction()) }
    var hpa by remember { mutableFloatStateOf(0f) }
    var hasBaro by remember { mutableStateOf(true) }
    val wantBaro = "pressure" in keys

    // refresh every few seconds while the home screen is showing
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            battery = batteryLevel(ctx)
            storageUse().let { storage = it.first; freeGb = it.second }
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mem = if (mi.totalMem > 0L) 1f - mi.availMem.toFloat() / mi.totalMem.toFloat() else 0f
            memFreeMb = mi.availMem / 1_048_576f
            val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            brightness = try {
                android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            } catch (e: Exception) {
                0.5f
            }
            try {
                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                volumeRaw = cur to max
                volume = cur / max.toFloat()
            } catch (e: Exception) {
                volume = 0f
            }
            val t = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            heat = if (t == null || t == Int.MIN_VALUE) -1f else t / 10f
            day = dayFraction()
            delay(5000)
        }
    }

    // the pressure sensor is only switched on while an Atmos bar is chosen
    DisposableEffect(active, wantBaro) {
        if (!wantBaro) {
            onDispose { }
        } else {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
            if (sensor == null) {
                hasBaro = false
                onDispose { }
            } else {
                hasBaro = true
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) { hpa = e.values[0] }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                if (active) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                onDispose { sm.unregisterListener(listener) }
            }
        }
    }

    val readings = keys.mapNotNull { k ->
        when (k) {
            "battery" -> Reading(k, "Power", battery, "${(battery * 100).roundToInt()}%", 1f - battery,
                "Power: ${(battery * 100).roundToInt()}% charge" + if (charging) ", charging" else "")
            "storage" -> Reading(k, "Storage", storage, "${(storage * 100).roundToInt()}% full", storage,
                "Storage: ${(storage * 100).roundToInt()}% used, " + "%.1f".format(freeGb) + " GB free")
            "memory" -> Reading(k, "Memory", mem, "${(mem * 100).roundToInt()}% used", mem,
                "Memory: ${(mem * 100).roundToInt()}% used, ${memFreeMb.roundToInt()} MB free")
            "heat" -> if (heat < 0f) {
                Reading(k, "Heat", 0f, "n/a", null, "Heat: the phone does not report it")
            } else {
                val f = ((heat - 20f) / 40f).coerceIn(0f, 1f)
                val t = if (fahrenheit) "%.1f°F".format(heat * 9f / 5f + 32f) else "%.1f°C".format(heat)
                Reading(k, "Heat", f, if (fahrenheit) "${(heat * 9f / 5f + 32f).roundToInt()}°F" else "${heat.roundToInt()}°C", f, "Heat: $t")
            }
            "pressure" -> when {
                !hasBaro -> Reading(k, "Atmos", 0f, "no sensor", null)
                hpa <= 0f -> Reading(k, "Atmos", 0f, "reading...", null)
                else -> Reading(k, "Atmos", ((hpa - 950f) / 100f).coerceIn(0f, 1f), "${hpa.roundToInt()} hPa", null,
                    "Atmos: %.1f hPa".format(hpa))
            }
            "day" -> {
                val sec = (day * 86_400f).roundToInt()
                Reading(k, "Day", day, "${(day * 100).roundToInt()}% gone", null,
                    "Day: ${hoursText(sec)} gone, ${hoursText(86_400 - sec)} left")
            }
            "net" -> {
                val f = (kotlin.math.ln(1f + netRate / 1024f) / kotlin.math.ln(1f + 20f * 1024f)).coerceIn(0f, 1f)
                Reading(k, "Uplink", f, speedText(netRate), null, "Uplink: ${speedText(netRate)}, down and up together")
            }
            "brightness" -> Reading(k, "Light", brightness, "${(brightness * 100).roundToInt()}%", null,
                "Screen brightness: ${(brightness * 100).roundToInt()}%")
            "volume" -> Reading(k, "Volume", volume, "${(volume * 100).roundToInt()}%", null,
                "Media volume: ${volumeRaw.first} of ${volumeRaw.second} steps")
            else -> null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val n = readings.size.coerceAtLeast(1)
        val gap = 6.dp
        val rowH = maxOf((maxHeight - gap * (n - 1)) / n, 10.dp)
        val fs = minOf(rowH.value * 0.5f, maxWidth.value * 0.05f).coerceIn(8f, 15f)
        val barH = minOf(rowH * 0.7f, 22.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            readings.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().height(rowH).clickable { picked = if (picked == r.key) null else r.key },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (picked == r.key) {
                        // tapped: the exact figure replaces the bar for a few seconds
                        Text(
                            r.exact, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge, fontSize = fs.sp, color = Holo.signal.lit,
                        )
                    } else {
                        Text(
                            r.label.uppercase(), Modifier.weight(0.27f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelLarge, fontSize = fs.sp, color = Holo.Text,
                        )
                        HoloBar(r.fill, r.bad, Modifier.weight(0.45f).padding(horizontal = 6.dp).height(barH), colours[r.key]?.let { Color(it) })
                        Text(
                            r.text, Modifier.weight(0.28f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelLarge, fontSize = fs.sp, color = Holo.Muted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One holographic level bar: a slanted frame of light holding small segments that light up from the left.
 * In Auto colour it glows in the signal colour, then amber, then red as things get worrying.
 */
@Composable
private fun HoloBar(fill: Float, bad: Float?, modifier: Modifier, tint: Color? = null) {
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), tween(700), label = "bar")
    val light = when {
        tint != null -> tint
        bad == null -> Holo.signal.glow
        bad < 0.6f -> Holo.signal.glow
        bad < 0.85f -> Color(0xFFFFB13D)
        else -> Holo.Alert
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val slant = h * 0.35f
        // the frame: a parallelogram, like a readout on a ship's console
        val frame = Path().apply {
            moveTo(slant, 0f); lineTo(w, 0f); lineTo(w - slant, h); lineTo(0f, h); close()
        }
        drawPath(frame, Color(0xAA02040A))
        drawPath(frame, light.copy(alpha = 0.25f), style = Stroke(3.dp.toPx()))
        drawPath(frame, light.copy(alpha = 0.8f), style = Stroke(1.dp.toPx()))

        // the segments
        val pad = 3.dp.toPx()
        val count = 16
        val inner = w - slant - pad * 2f
        val segW = inner / count
        val lit = (level * count).roundToInt()
        for (i in 0 until count) {
            val x = pad + slant * 0.5f + i * segW
            val on = i < lit
            drawRect(
                if (on) Brush.verticalGradient(listOf(lerp(light, Color.White, 0.35f), light)) else Brush.verticalGradient(listOf(light.copy(alpha = 0.10f), light.copy(alpha = 0.10f))),
                topLeft = Offset(x + segW * 0.15f, pad + 1f), size = Size(segW * 0.7f, h - pad * 2f - 2f),
            )
        }
        // a soft glow behind the lit part
        if (lit > 0) {
            drawRect(
                Brush.horizontalGradient(listOf(light.copy(alpha = 0.0f), light.copy(alpha = 0.18f))),
                topLeft = Offset(pad, 0f), size = Size(lit * segW + slant * 0.5f, h),
            )
        }
    }
}
