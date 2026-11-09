package com.stellarite.cyberskinos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Weather widget. It asks open-meteo.com, a free weather service that needs no account or key, for the weather at
 * your rough location. Only the rounded latitude and longitude are sent (to about 1 km), nothing else. The last answer
 * is kept, together with an hour-by-hour forecast, so the widget still shows the right hour when you have no signal.
 * Weather data by Open-Meteo.com (CC BY 4.0).
 */
private class DayForecast(
    val date: LocalDate, val code: Int, val high: Float, val low: Float,
    val rain: Int, // chance of rain or snow in %, or -1 when not known
    val sunrise: LocalDateTime?, val sunset: LocalDateTime?,
)

private class HourForecast(val time: LocalDateTime, val temp: Float, val feels: Float, val code: Int, val day: Boolean, val wind: Float)

private class WeatherNow(
    val at: LocalDateTime?, // local time of the "current" reading
    val temp: Float, val feels: Float, val code: Int, val day: Boolean, val wind: Float, val gusts: Float,
    val offset: Int, // the place's offset from UTC, in seconds
    val days: List<DayForecast>, val hours: List<HourForecast>,
)

/** What the widget should show right now, worked out from the saved answer. */
private class Shown(
    val temp: Float, val feels: Float, val code: Int, val day: Boolean, val wind: Float, val gusts: Float,
    val today: DayForecast?, val next: List<DayForecast>, val old: Boolean,
)

/** Words for the WMO weather codes the service uses. */
private fun weatherName(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Part cloud"
    3 -> "Overcast"
    45 -> "Fog"
    48 -> "Freezing fog"
    51 -> "Light drizzle"
    53 -> "Drizzle"
    55 -> "Heavy drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80 -> "Light showers"
    81 -> "Showers"
    82 -> "Heavy showers"
    85 -> "Snow showers"
    86 -> "Heavy snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Storm with hail"
    else -> "Unsettled"
}

private fun JSONArray.floatAt(i: Int, or: Float): Float = if (!isNull(i)) optDouble(i, or.toDouble()).toFloat() else or
private fun JSONArray.intAt(i: Int, or: Int): Int = if (!isNull(i)) optInt(i, or) else or
private fun JSONArray.timeAt(i: Int): LocalDateTime? =
    if (!isNull(i)) try { LocalDateTime.parse(getString(i)) } catch (e: Exception) { null } else null

/** Reads the service's answer. Older saved answers without the hourly part still work. */
private fun parseWeather(json: String): WeatherNow? = try {
    val root = JSONObject(json)
    val cur = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val dates = daily.getJSONArray("time")
    val codes = daily.getJSONArray("weather_code")
    val highs = daily.getJSONArray("temperature_2m_max")
    val lows = daily.getJSONArray("temperature_2m_min")
    val rains = daily.optJSONArray("precipitation_probability_max") ?: JSONArray()
    val rises = daily.optJSONArray("sunrise") ?: JSONArray()
    val sets = daily.optJSONArray("sunset") ?: JSONArray()
    val days = (0 until dates.length()).map { i ->
        DayForecast(
            LocalDate.parse(dates.getString(i)), codes.intAt(i, 3), highs.floatAt(i, 0f), lows.floatAt(i, 0f),
            rains.intAt(i, -1), rises.timeAt(i), sets.timeAt(i),
        )
    }
    val hourly = root.optJSONObject("hourly")
    val hours = if (hourly == null) emptyList() else {
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val feels = hourly.optJSONArray("apparent_temperature") ?: temps
        val hcodes = hourly.getJSONArray("weather_code")
        val isDay = hourly.optJSONArray("is_day") ?: JSONArray()
        val winds = hourly.optJSONArray("wind_speed_10m") ?: JSONArray()
        (0 until times.length()).mapNotNull { i ->
            val t = times.timeAt(i)
            if (t == null || temps.isNull(i)) null
            else HourForecast(t, temps.floatAt(i, 0f), feels.floatAt(i, temps.floatAt(i, 0f)), hcodes.intAt(i, 3), isDay.intAt(i, 1) == 1, winds.floatAt(i, 0f))
        }
    }
    val wind = cur.getDouble("wind_speed_10m").toFloat()
    WeatherNow(
        try { LocalDateTime.parse(cur.optString("time")) } catch (e: Exception) { null },
        cur.getDouble("temperature_2m").toFloat(), cur.getDouble("apparent_temperature").toFloat(), cur.getInt("weather_code"),
        cur.getInt("is_day") == 1, wind, cur.optDouble("wind_gusts_10m", wind.toDouble()).toFloat(),
        root.optInt("utc_offset_seconds", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000),
        days, hours,
    )
} catch (e: Exception) {
    null
}

/**
 * Picks what to show at this moment. A recent reading is used as it is. An older one is replaced by the forecast for
 * the current hour, so a phone that has been offline for a while does not show this morning's weather at night.
 * Day and night come from that day's sunrise and sunset, and "today" and the coming days follow the place's own date.
 */
private fun shown(w: WeatherNow, nowMs: Long): Shown {
    val local = Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.ofTotalSeconds(w.offset)).toLocalDateTime()
    val date = local.toLocalDate()
    val today = w.days.firstOrNull { it.date == date }
    val next = w.days.filter { it.date.isAfter(date) }.take(3)
    val age = w.at?.let { Duration.between(it, local).toMinutes() }
    val fresh = age != null && age in -90L..45L
    val hour = if (fresh) null else w.hours
        .minByOrNull { abs(Duration.between(it.time, local).toMinutes()) }
        ?.takeIf { abs(Duration.between(it.time, local).toMinutes()) <= 45L }
    val rise = today?.sunrise
    val set = today?.sunset
    fun dayAt(fallback: Boolean) = if (rise != null && set != null) !local.isBefore(rise) && local.isBefore(set) else fallback
    return if (hour != null) {
        Shown(hour.temp, hour.feels, hour.code, dayAt(hour.day), hour.wind, hour.wind, today, next, false)
    } else {
        Shown(w.temp, w.feels, w.code, dayAt(w.day), w.wind, w.gusts, today, next, !fresh)
    }
}

/** Asks the service. Run this off the main thread. Returns the raw answer, or null if it could not be had. */
private fun fetchWeather(lat: Double, lon: Double): String? = try {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=" + "%.2f".format(Locale.US, lat) + "&longitude=" + "%.2f".format(Locale.US, lon) +
        "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,wind_gusts_10m" +
        "&hourly=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
        "&forecast_days=5&timezone=auto"
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    c.requestMethod = "GET"
    try {
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        c.disconnect()
    }
} catch (e: Exception) {
    null
}

/**
 * Asks the phone for a fresh rough position (Android 11 and later), instead of trusting a last-known one that may be
 * days old. Uses the network provider only, so it is quick and light on the battery. Null if none came in time.
 */
@SuppressLint("MissingPermission")
private suspend fun freshFix(ctx: Context): Pair<Double, Double>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val on = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
    if (!on) return null
    return withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, ctx.mainExecutor) { l ->
                    if (cont.isActive) cont.resume(l?.let { it.latitude to it.longitude })
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

/** True when [here] is more than about 3 km from the place the saved forecast is for. */
private fun farApart(here: Pair<Double, Double>, spot: String?): Boolean {
    val parts = spot?.split(",") ?: return true
    if (parts.size != 2) return true
    val lat = parts[0].toDoubleOrNull() ?: return true
    val lon = parts[1].toDoubleOrNull() ?: return true
    val dy = (here.first - lat) * 111.0
    val dx = (here.second - lon) * 111.0 * cos(Math.toRadians(lat))
    return dx * dx + dy * dy > 9.0
}

private fun degrees(c: Float, fahrenheit: Boolean): String =
    (if (fahrenheit) (c * 9f / 5f + 32f).roundToInt() else c.roundToInt()).toString() + "°"

private fun speed(kmh: Float, imperial: Boolean): String =
    if (imperial) "${(kmh * 0.621f).roundToInt()} mph" else "${kmh.roundToInt()} km/h"

private enum class Sky { CLEAR, FEW, PART, OVERCAST, FOG, DRIZZLE, RAIN, HEAVY_RAIN, FREEZING, SNOW, HEAVY_SNOW, SHOWERS, SNOW_SHOWERS, STORM, HAIL }

private fun skyFor(code: Int): Sky = when (code) {
    0 -> Sky.CLEAR
    1 -> Sky.FEW
    2 -> Sky.PART
    3 -> Sky.OVERCAST
    45, 48 -> Sky.FOG
    51, 53, 55 -> Sky.DRIZZLE
    56, 57, 66, 67 -> Sky.FREEZING
    61, 63 -> Sky.RAIN
    65 -> Sky.HEAVY_RAIN
    71, 73, 77 -> Sky.SNOW
    75 -> Sky.HEAVY_SNOW
    80, 81, 82 -> Sky.SHOWERS
    85, 86 -> Sky.SNOW_SHOWERS
    95 -> Sky.STORM
    96, 99 -> Sky.HAIL
    else -> Sky.OVERCAST
}

/**
 * A small weather picture drawn as a hologram, in the colours of the palette. The sun is a star with a ring of light,
 * the night has a proper crescent moon, and rain, snow and storms show how heavy they are.
 */
private fun DrawScope.glyph(code: Int, day: Boolean) {
    val u = minOf(size.width, size.height)
    val ox = (size.width - u) / 2f
    val oy = (size.height - u) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    val sun = Holo.signal.lit
    val sunGlow = Holo.signal.glow
    val cloud = lerp(Holo.Bright, Holo.Deck, 0.15f)
    val cloudEdge = Holo.Line
    val dark = lerp(cloud, Holo.Void, 0.4f)
    val rain = Holo.Ok
    val snow = Color.White

    fun sunAt(x: Float, y: Float, r0: Float) {
        val c = p(x, y)
        val r = r0 * u
        if (!day) {
            val disc = Path().apply { addOval(Rect(c, r)) }
            val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) }
            drawCircle(sunGlow.copy(alpha = 0.18f), r * 1.35f, c)
            drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, sun)
            return
        }
        drawCircle(sunGlow.copy(alpha = 0.3f), r * 1.6f, c)
        for (i in 0 until 12) { // short rays of light
            val a = Math.toRadians(i * 30.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val long = i % 3 == 0
            drawLine(sun, Offset(c.x + r * 1.15f * dx, c.y + r * 1.15f * dy), Offset(c.x + r * (if (long) 1.6f else 1.4f) * dx, c.y + r * (if (long) 1.6f else 1.4f) * dy), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
        }
        drawCircle(sun, r, c)
        drawCircle(sunGlow, r * 0.72f, c, style = Stroke(r * 0.1f))
    }

    fun star(x: Float, y: Float, r0: Float) {
        val c = p(x, y)
        val r = r0 * u
        drawLine(sun, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
        drawLine(sun, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
    }

    fun cloudAt(x: Float, y: Float, s0: Float, fill: Color = cloud) {
        val cx = ox + x * u
        val cy = oy + y * u
        val s = s0 * u
        val shape = Path().apply {
            moveTo(cx - s, cy + s * 0.5f)
            lineTo(cx + s, cy + s * 0.5f)
            cubicTo(cx + s * 1.5f, cy + s * 0.5f, cx + s * 1.5f, cy - s * 0.2f, cx + s * 0.8f, cy - s * 0.2f)
            cubicTo(cx + s * 0.8f, cy - s * 0.9f, cx - s * 0.1f, cy - s * 1.0f, cx - s * 0.2f, cy - s * 0.3f)
            cubicTo(cx - s * 0.7f, cy - s * 0.5f, cx - s * 1.3f, cy - s * 0.1f, cx - s * 1.0f, cy + s * 0.2f)
            cubicTo(cx - s * 1.5f, cy + s * 0.3f, cx - s * 1.3f, cy + s * 0.5f, cx - s, cy + s * 0.5f)
            close()
        }
        drawPath(shape, fill)
        drawPath(shape, cloudEdge, style = Stroke(s * 0.08f))
    }

    fun drop(x: Float, y: Float, d0: Float = 0.065f) {
        val cx = ox + x * u
        val cy = oy + y * u
        val d = d0 * u
        val shape = Path().apply {
            moveTo(cx, cy - d)
            cubicTo(cx + d * 0.15f, cy - d * 0.55f, cx + d * 0.7f, cy - d * 0.05f, cx + d * 0.7f, cy + d * 0.35f)
            cubicTo(cx + d * 0.7f, cy + d * 0.75f, cx + d * 0.35f, cy + d, cx, cy + d)
            cubicTo(cx - d * 0.35f, cy + d, cx - d * 0.7f, cy + d * 0.75f, cx - d * 0.7f, cy + d * 0.35f)
            cubicTo(cx - d * 0.7f, cy - d * 0.05f, cx - d * 0.15f, cy - d * 0.55f, cx, cy - d)
            close()
        }
        drawPath(shape, rain)
    }

    fun dash(x: Float, y: Float) =
        drawLine(rain, p(x, y), p(x - 0.02f, y + 0.055f), strokeWidth = u * 0.035f, cap = StrokeCap.Round)

    fun flake(x: Float, y: Float, r0: Float = 0.055f) {
        val c = p(x, y)
        val r = r0 * u
        for (i in 0 until 3) {
            val a = Math.toRadians(90.0 + i * 60.0)
            val dx = (r * cos(a)).toFloat()
            val dy = (r * sin(a)).toFloat()
            drawLine(snow, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), strokeWidth = r * 0.3f, cap = StrokeCap.Round)
        }
    }

    fun pellet(x: Float, y: Float) {
        drawCircle(snow, u * 0.04f, p(x, y))
        drawCircle(cloudEdge, u * 0.04f, p(x, y), style = Stroke(u * 0.012f))
    }

    fun bolt() {
        val a = p(0.56f, 0.56f); val b = p(0.42f, 0.76f); val c = p(0.52f, 0.76f)
        val d = p(0.45f, 0.96f); val e = p(0.64f, 0.70f); val f = p(0.54f, 0.70f)
        val shape = Path().apply {
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); lineTo(e.x, e.y); lineTo(f.x, f.y); close()
        }
        drawPath(shape, sun)
        drawPath(shape, cloudEdge, style = Stroke(u * 0.015f))
    }

    when (skyFor(code)) {
        Sky.CLEAR -> {
            sunAt(0.5f, 0.5f, 0.22f)
            if (!day) { star(0.2f, 0.24f, 0.045f); star(0.82f, 0.74f, 0.035f) }
        }
        Sky.FEW -> { sunAt(0.44f, 0.44f, 0.21f); cloudAt(0.66f, 0.72f, 0.15f) }
        Sky.PART -> { sunAt(0.36f, 0.36f, 0.17f); cloudAt(0.55f, 0.6f, 0.26f) }
        Sky.OVERCAST -> { cloudAt(0.62f, 0.42f, 0.2f, dark); cloudAt(0.45f, 0.6f, 0.27f) }
        Sky.FOG -> {
            cloudAt(0.5f, 0.36f, 0.24f)
            for (i in 0 until 3) {
                val y = 0.66f + i * 0.11f
                val x0 = if (i % 2 == 0) 0.18f else 0.28f
                drawLine(cloud.copy(alpha = 0.85f), p(x0, y), p(x0 + 0.54f, y), strokeWidth = u * 0.055f, cap = StrokeCap.Round)
            }
        }
        Sky.DRIZZLE -> {
            cloudAt(0.5f, 0.38f, 0.26f)
            dash(0.32f, 0.66f); dash(0.5f, 0.7f); dash(0.68f, 0.66f); dash(0.41f, 0.83f); dash(0.59f, 0.83f)
        }
        Sky.RAIN -> { cloudAt(0.5f, 0.38f, 0.26f); drop(0.34f, 0.76f); drop(0.5f, 0.84f); drop(0.66f, 0.76f) }
        Sky.HEAVY_RAIN -> {
            cloudAt(0.5f, 0.38f, 0.26f, dark)
            drop(0.27f, 0.72f, 0.06f); drop(0.43f, 0.8f, 0.06f); drop(0.59f, 0.72f, 0.06f); drop(0.75f, 0.8f, 0.06f); drop(0.51f, 0.92f, 0.06f)
        }
        Sky.FREEZING -> { cloudAt(0.5f, 0.38f, 0.26f); drop(0.33f, 0.76f); flake(0.5f, 0.84f); drop(0.67f, 0.76f) }
        Sky.SNOW -> { cloudAt(0.5f, 0.38f, 0.26f); flake(0.33f, 0.74f); flake(0.5f, 0.86f); flake(0.67f, 0.74f) }
        Sky.HEAVY_SNOW -> {
            cloudAt(0.5f, 0.38f, 0.26f, dark)
            flake(0.27f, 0.72f, 0.05f); flake(0.47f, 0.73f, 0.05f); flake(0.67f, 0.72f, 0.05f); flake(0.37f, 0.88f, 0.05f); flake(0.57f, 0.88f, 0.05f)
        }
        Sky.SHOWERS -> { sunAt(0.3f, 0.3f, 0.15f); cloudAt(0.56f, 0.48f, 0.24f); drop(0.46f, 0.8f); drop(0.64f, 0.86f) }
        Sky.SNOW_SHOWERS -> { sunAt(0.3f, 0.3f, 0.15f); cloudAt(0.56f, 0.48f, 0.24f); flake(0.46f, 0.8f); flake(0.64f, 0.86f) }
        Sky.STORM -> { cloudAt(0.5f, 0.36f, 0.26f, dark); bolt(); drop(0.28f, 0.74f); drop(0.76f, 0.74f) }
        Sky.HAIL -> {
            cloudAt(0.5f, 0.36f, 0.26f, dark); bolt()
            pellet(0.28f, 0.72f); pellet(0.76f, 0.7f); pellet(0.33f, 0.88f); pellet(0.72f, 0.88f)
        }
    }
}

@Composable
private fun Glyph(code: Int, day: Boolean, modifier: Modifier) {
    Canvas(modifier) { glyph(code, day) }
}

@Composable
fun WeatherContent(prefs: SharedPreferences, fahrenheit: Boolean, title: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val mw = maxWidth
    val mh = maxHeight
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    var retry by remember { mutableIntStateOf(0) }
    var loc by remember(granted, retry) { mutableStateOf(findPlace(ctx, prefs, granted)) }
    var weather by remember { mutableStateOf(parseWeather(prefs.getString("wx_json", null) ?: "")) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Refresh every half hour while the home screen is showing, or sooner if you have moved more than a few km
    LaunchedEffect(granted, active, retry) {
        if (!active) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            val known = findPlace(ctx, prefs, granted)
            if (known != null) loc = known
            val due = now - prefs.getLong("wx_time", 0L) > 30 * 60_000L ||
                (known != null && farApart(known, prefs.getString("wx_spot", null)))
            if (due) {
                loading = true
                var here = known
                if (granted) {
                    freshFix(ctx)?.let { f ->
                        here = f
                        loc = f
                        prefs.edit().putString("sun_lat", f.first.toString()).putString("sun_lon", f.second.toString()).apply()
                    }
                }
                val at = here
                if (at != null) {
                    val raw = withContext(Dispatchers.IO) { fetchWeather(at.first, at.second) }
                    val parsed = raw?.let { parseWeather(it) }
                    if (parsed != null) {
                        weather = parsed
                        failed = false
                        now = System.currentTimeMillis()
                        prefs.edit().putString("wx_json", raw).putLong("wx_time", now)
                            .putString("wx_spot", "%.3f,%.3f".format(Locale.US, at.first, at.second)).apply()
                    } else {
                        failed = true
                    }
                }
                loading = false
            }
            delay(if (failed) 2 * 60_000L else 5 * 60_000L)
        }
    }

    val w = weather
    if (loc == null && w == null) {
        WeatherNote(
            title,
            if (!granted) "Tap to share your rough location once. It stays on this phone, and only the rounded place is sent to open-meteo.com for the forecast."
            else "No location yet. Open a maps app once, then tap here to try again.",
            Modifier.clickable { if (!granted) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else retry++ },
        )
    } else if (w == null) {
        WeatherNote(
            title,
            if (failed) "Could not reach the weather service. Tap to try again." else "Fetching the weather...",
            Modifier.clickable { failed = false; prefs.edit().remove("wx_time").apply(); retry++ },
        )
    } else {
        val s = shown(w, now)
        val hi = Holo.signal.lit
        val tall = mh > 150.dp
        val roomy = mh > 215.dp // enough height for the wind line as well as the three days
        val showDays = mw > 250.dp && s.next.isNotEmpty()
        val today = s.today
        val showHiLo = today != null && mh > 120.dp
        val glyphSize = minOf(mh * (if (tall) 0.5f else 0.8f), mw * 0.3f)
        Column(Modifier.fillMaxSize().clickable { failed = false; prefs.edit().remove("wx_time").apply(); retry++ }) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Glyph(s.code, s.day, Modifier.size(glyphSize))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 12.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Muted, maxLines = 1)
                    Text(
                        degrees(s.temp, fahrenheit) + (if (fahrenheit) "F" else "C"),
                        fontSize = minOf(mh.value * 0.24f, 34f).coerceAtLeast(18f).sp,
                        fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = hi, maxLines = 1,
                    )
                    Text(
                        weatherName(s.code) + ", feels " + degrees(s.feels, fahrenheit),
                        fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Text, maxLines = 1,
                    )
                    if (showHiLo && today != null) {
                        Text(
                            "H " + degrees(today.high, fahrenheit) + "  L " + degrees(today.low, fahrenheit) +
                                (if (today.rain >= 10) "  ·  ${today.rain}% rain" else ""),
                            fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Text, maxLines = 1,
                        )
                    }
                    if (roomy || !showDays) {
                        val gust = if (s.gusts >= s.wind + 15f) ", gusts " + speed(s.gusts, fahrenheit).substringBefore(' ') else ""
                        Text(
                            "Wind " + speed(s.wind, fahrenheit) + gust +
                                (if (loading) "  (updating)" else if (failed) "  (offline)" else if (s.old) "  (old)" else ""),
                            fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1,
                        )
                    }
                }
            }
            if (showDays && tall) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    s.next.forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1,
                            )
                            Glyph(d.code, true, Modifier.size(30.dp))
                            Text(
                                degrees(d.high, fahrenheit) + " " + degrees(d.low, fahrenheit),
                                fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Text, maxLines = 1,
                            )
                            if (roomy && d.rain >= 20) {
                                Text("${d.rain}%", fontSize = 10.sp, fontFamily = Holo.font, color = Holo.Ok, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherNote(title: String, text: String, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Bright, maxLines = 1)
        Text(text, fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted)
    }
}

/** The same remembered place the Solar phase widget uses, so you only share your location once. */
private fun findPlace(ctx: Context, prefs: SharedPreferences, granted: Boolean): Pair<Double, Double>? = findLocation(ctx, prefs, granted)
