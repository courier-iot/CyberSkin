package com.stellarite.cyberskinos

import android.content.ClipData
import android.content.ClipboardManager
import android.Manifest
import android.app.ActivityManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.util.Base64
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import android.net.TrafficStats
import android.os.SystemClock
import kotlin.math.ln
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.roundToInt

// ───────────────────────── Apps ─────────────────────────

/**
 * Same as the Material Text, except it is never cut off at the bottom. Text inside a widget gets
 * measured with whatever height is left over, and fonts with taller line spacing used to be clipped.
 */
@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier.wrapContentHeight(Alignment.Top, unbounded = true),
        color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, style = style,
    )
}

/** Takes the cursor out of any text box and puts the keyboard away. */
internal fun dropFocus(ctx: Context, view: View, focus: FocusManager) {
    focus.clearFocus()
    try {
        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
    } catch (e: Exception) {
    }
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** First run: put the phone's default dialer, messages, browser and camera in Quick access. */
private fun defaultShelf(ctx: Context): List<String> {
    val pm = ctx.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    )
    return intents
        .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .filter { it != "android" }
        .distinct()
}

private fun loadPinned(ctx: Context, prefs: SharedPreferences): List<String> {
    val saved = prefs.getString("pinned", null)
    if (saved != null) return saved.split(",").filter { it.isNotBlank() }
    val d = defaultShelf(ctx)
    prefs.edit().putString("pinned", d.joinToString(",")).apply()
    return d
}

private fun loadCounts(prefs: SharedPreferences): Map<String, Int> =
    (prefs.getString("counts", "") ?: "").split(",").mapNotNull {
        val p = it.split(":")
        if (p.size == 2) p[1].toIntOrNull()?.let { n -> p[0] to n } else null
    }.toMap()

/** The most apps shown in the Databank's Recent row. */
private const val MAX_RECENT = 8

/** Package names of the apps you opened lately, newest first. */
private fun loadRecent(prefs: SharedPreferences): List<String> =
    (prefs.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }

private fun launchApp(ctx: Context, app: AppInfo) {
    Sfx.play(Sfx.Kind.TAP)
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try { ctx.startActivity(i) } catch (e: Exception) {
        Toast.makeText(ctx, "Could not open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

private fun open(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
        Toast.makeText(ctx, "That is not available on this device", Toast.LENGTH_SHORT).show()
    }
}

internal fun batteryLevel(ctx: Context): Float {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level < 0 || scale <= 0) 0f else level / scale.toFloat()
}

/** (fraction used, GB free) */
internal fun storageUse(): Pair<Float, Float> = try {
    val s = StatFs(Environment.getDataDirectory().path)
    val total = s.totalBytes.toFloat()
    val free = s.availableBytes.toFloat()
    (1f - free / total) to (free / 1e9f)
} catch (e: Exception) { 0f to 0f }

// ───────────────────────── Power-user helpers ─────────────────────────

/** Fuzzy match score (higher is better, null = no match). Handles prefixes, word starts, initials and typos in order. */
private fun fuzzyScore(label: String, query: String): Int? {
    val l = label.lowercase()
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 0
    if (l == q) return 1000
    if (l.startsWith(q)) return 900 - l.length
    val words = l.split(" ", "-", "_", ".").filter { it.isNotEmpty() }
    if (words.any { it.startsWith(q) }) return 800 - l.length
    val at = l.indexOf(q)
    if (at >= 0) return 700 - at
    if (words.map { it[0] }.joinToString("").startsWith(q)) return 600 // "gm" finds Google Maps
    if (q.length >= 3) {
        var qi = 0
        var gaps = 0
        var last = -1
        for ((i, ch) in l.withIndex()) {
            if (qi < q.length && ch == q[qi]) {
                if (last >= 0 && i - last > 1) gaps++
                last = i
                qi++
            }
        }
        if (qi == q.length) return 400 - gaps * 10 - l.length // "clndr" finds Calendar
    }
    return null
}

/** A tiny, safe arithmetic parser: + - * / ^ % and brackets. */
private class CalcParser(private val s: String) {
    private var i = 0

    fun parse(): Double? = try {
        val v = expr()
        skip()
        if (i == s.length && v.isFinite()) v else null
    } catch (e: Exception) { null }

    private fun skip() { while (i < s.length && s[i] == ' ') i++ }
    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

    private fun expr(): Double {
        var v = term()
        var more = true
        while (more) {
            skip()
            when (peek()) {
                '+' -> { i++; v += term() }
                '-' -> { i++; v -= term() }
                else -> more = false
            }
        }
        return v
    }

    private fun term(): Double {
        var v = unary()
        var more = true
        while (more) {
            skip()
            when (peek()) {
                '*' -> { i++; v *= unary() }
                '/' -> { i++; v /= unary() }
                else -> more = false
            }
        }
        return v
    }

    private fun unary(): Double {
        skip()
        return when (peek()) {
            '-' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> power()
        }
    }

    private fun power(): Double {
        val b = postfix()
        skip()
        if (peek() == '^') { i++; return Math.pow(b, unary()) }
        return b
    }

    private fun postfix(): Double {
        var v = atom()
        skip()
        while (peek() == '%') { i++; v /= 100.0; skip() }
        return v
    }

    private fun atom(): Double {
        skip()
        if (peek() == '(') {
            i++
            val v = expr()
            skip()
            if (peek() != ')') throw IllegalArgumentException("missing bracket")
            i++
            return v
        }
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (st == i) throw IllegalArgumentException("number expected")
        return s.substring(st, i).toDouble()
    }
}

private fun formatNumber(v: Double): String {
    if (v == Math.rint(v) && Math.abs(v) < 1e15) return v.toLong().toString()
    return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
}

/** The answer if the search text is a sum such as 18*4+6, otherwise null. */
private fun calcResult(query: String): String? {
    val q = query.trim()
        .replace('×', '*').replace('÷', '/').replace(',', '.')
        .replace(Regex("(?<=[0-9)])\\s*[xX]\\s*(?=[0-9(])"), "*")
    if (q.length < 3 || q.any { it !in "0123456789+-*/^%(). " }) return null
    val hasOp = q.indices.any { it > 0 && q[it] in "+-*/^%" } || q.contains('(')
    if (!hasOp || q.none { it.isDigit() }) return null
    val v = CalcParser(q).parse() ?: return null
    return formatNumber(v)
}

private fun loadHidden(prefs: SharedPreferences): Set<String> =
    (prefs.getString("hidden", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

private fun canUseShortcuts(ctx: Context): Boolean = try {
    (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).hasShortcutHostPermission()
} catch (e: Exception) { false }

/** App shortcuts (e.g. "New message"). Android only gives these to the default home app. */
private fun loadShortcuts(ctx: Context, pkg: String): List<ShortcutInfo> = try {
    val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    if (!la.hasShortcutHostPermission()) {
        emptyList()
    } else {
        val q = LauncherApps.ShortcutQuery()
            .setPackage(pkg)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
            .filter { it.isEnabled && it.shortLabel != null }
            .take(4)
    }
} catch (e: Exception) { emptyList() }

private fun startShortcut(ctx: Context, sc: ShortcutInfo) {
    try {
        (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(sc, null, null)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Could not open that shortcut", Toast.LENGTH_SHORT).show()
    }
}

// ───────────────────────── Widgets: model ─────────────────────────

enum class WType(
    val title: String, val blurb: String,
    val defW: Float, val defH: Float, val minW: Float, val minH: Float,
    val tip: String? = null,
) {
    CLOCK("Holo clock", "Projected digits with the date underneath", 340f, 140f, 150f, 100f, "Recommended: the centrepiece"),
    NIXIE("Time readout", "Just the projected digits, no date", 340f, 66f, 150f, 60f, "Recommended: a clean, compact clock"),
    ANALOG("Chrono dial", "A dial of light with the time", 340f, 140f, 150f, 120f),
    DIGITAL("Time display", "Large digital time and date", 340f, 100f, 150f, 80f),
    BOILER("Power cell", "Battery level on an arc gauge", 164f, 140f, 110f, 110f, "Recommended: your charge at a glance"),
    CARGO("Data storage", "Storage used and free", 164f, 140f, 110f, 110f, "Recommended: spot a full phone early"),
    VIALS("Vitals", "Level bars that fill left to right. You choose the readings", 340f, 120f, 150f, 70f, "Tap the ⚙ in Arrange mode to choose the readings"),
    MUSIC("Audio deck", "A holographic disc with working controls. Resize it to change its shape", 340f, 120f, 150f, 120f, "Track names and progress need Notification access, granted once"),
    ENGINE("System load", "RAM, battery heat and network flow on three gauges", 340f, 140f, 200f, 110f, "Recommended: see what is loading the phone"),
    COMPASS("Nav bearing", "A navigation compass that turns as you do", 164f, 164f, 110f, 110f, "Recommended: a working compass, hold the phone flat"),
    BAROMETER("Atmos pressure", "Air pressure with a weather word", 164f, 140f, 110f, 110f, "Needs a pressure sensor, most phones have one"),
    MOON("Lunar phase", "Tonight's moon and the next full moon", 164f, 164f, 110f, 110f, "Recommended: no permissions needed"),
    SUN("Daylight cycle", "Sunrise, sunset and the sun's path", 340f, 140f, 200f, 100f, "Asks for your rough location once"),
    WEATHER("Atmos", "Weather now and the next three days, from the free open-meteo.com service", 340f, 190f, 200f, 100f, "Uses the internet. Sends only your rounded location"),
    SOLAR("Orbital map", "The planets where they really are today. Tap one to inspect it", 340f, 290f, 200f, 200f, "Recommended: no internet needed"),
    CHRONO("Stopwatch", "Stopwatch with a lap. Keeps counting while you are away", 340f, 140f, 300f, 100f, "Recommended: a precise stopwatch"),
    TIMER("Timer", "A countdown and an alarm clock. They ring even when the launcher is closed", 340f, 150f, 300f, 130f, "Asks to send notifications once, so it can ring"),
    ALMANAC("Calendar grid", "Month calendar with today marked", 340f, 280f, 200f, 200f, "Recommended: the month at a glance"),
    AGENDA("Schedule feed", "What is coming up in your calendar, day by day", 340f, 200f, 200f, 110f, "Asks to read your calendar once"),
    LANTERN("Floodlight", "A torch you switch on and off with a tap", 164f, 120f, 110f, 90f, "Recommended: no permission needed"),
    WORLD("World time", "The time here and in up to six cities you choose", 164f, 190f, 150f, 100f, "Recommended: no permission needed"),
    CONTACTS("Comms", "A few people as round tiles. Tap to call, hold to text", 340f, 120f, 110f, 100f, "No permission needed. You pick each contact yourself"),
    LOGBOOK("Usage log", "Which apps you used most today, as bars", 340f, 220f, 200f, 110f, "Asks you to switch on Usage access once"),
    MEMO("Log", "Notes and checklists you can type in", 164f, 140f, 150f, 100f, "Recommended: jot things down fast"),
    SHELF("Quick access", "Apps you choose, one tap away", 340f, 130f, 110f, 100f, "Recommended: your most-used apps"),
    FOLDER("Data folder", "A named bundle of apps. Tap to open it", 164f, 150f, 110f, 110f, "Recommended: keeps related apps tidy"),
    LINKS("Link cache", "A named bundle of web links. Tap to open it", 164f, 150f, 110f, 110f, "Recommended: bookmarks on your home screen"),
    PICKS("Priority apps", "Apps you open most, learned on this phone", 340f, 210f, 110f, 100f, "Recommended: suggestions from your habits"),
    APPWIDGET("External widget", "Shows a widget that another app provides, drawn as that app designed it", 300f, 160f, 80f, 60f, "Android may ask permission. Looks as the other app made it"),
}

data class WidgetItem(val id: Long, val type: WType, val x: Float, val y: Float, val w: Float, val h: Float)

private fun encode(list: List<WidgetItem>): String = JSONArray().also { arr ->
    list.forEach {
        arr.put(
            JSONObject().put("id", it.id).put("t", it.type.name)
                .put("x", it.x.toDouble()).put("y", it.y.toDouble())
                .put("w", it.w.toDouble()).put("h", it.h.toDouble())
        )
    }
}.toString()

private fun decode(s: String?): List<WidgetItem>? {
    if (s == null) return null
    return try {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val t = WType.entries.firstOrNull { e -> e.name == o.getString("t") } ?: return@mapNotNull null
            WidgetItem(o.getLong("id"), t, o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("w").toFloat(), o.getDouble("h").toFloat())
        }
    } catch (e: Exception) { null }
}

// ───────────────────────── Presets ─────────────────────────

/** One widget slot in a preset, in grid cells (so it fits any screen width). */
data class PresetItem(val type: WType, val col: Int, val row: Int, val cols: Int, val rows: Int, val id: Long = 0L)
data class Preset(val name: String, val items: List<PresetItem>, val builtin: Boolean = false, val look: Look? = null)

private const val AUTO_PRESET = "Previous layout"

/** The most home pages you can have. */
private const val MAX_PAGES = 8

private val BUILTIN_PRESETS = listOf(
    Preset("Bridge", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.BOILER, 0, 2, 2, 2),
        PresetItem(WType.CARGO, 2, 2, 2, 2), PresetItem(WType.SHELF, 0, 4, 4, 2),
    ), true),
    Preset("Minimal", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.PICKS, 0, 2, 4, 3),
    ), true),
    Preset("Systems", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.BOILER, 0, 2, 2, 2), PresetItem(WType.CARGO, 2, 2, 2, 2),
        PresetItem(WType.MEMO, 0, 4, 2, 2), PresetItem(WType.FOLDER, 2, 4, 2, 2),
    ), true),
    Preset("Planner", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.ALMANAC, 0, 2, 4, 4), PresetItem(WType.MEMO, 0, 6, 4, 2),
    ), true),
    Preset("Diagnostics", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.ENGINE, 0, 2, 4, 2),
        PresetItem(WType.BOILER, 0, 4, 2, 2), PresetItem(WType.CARGO, 2, 4, 2, 2),
    ), true),
    Preset("Navigator", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.COMPASS, 0, 2, 2, 2),
        PresetItem(WType.MOON, 2, 2, 2, 2), PresetItem(WType.SUN, 0, 4, 4, 2),
    ), true),
    // Looks: these also set the colours (never the wallpaper), and "Previous layout" keeps what you had
    Preset("Deep Space", listOf(
        PresetItem(WType.DIGITAL, 0, 0, 4, 2), PresetItem(WType.SOLAR, 0, 2, 4, 4),
        PresetItem(WType.MUSIC, 0, 6, 4, 2),
    ), true, builtinLook("Cobalt", "Cyan")),
    Preset("Mission Control", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.WEATHER, 0, 2, 4, 3),
        PresetItem(WType.AGENDA, 0, 5, 2, 2), PresetItem(WType.MEMO, 2, 5, 2, 2),
    ), true, builtinLook("Stellarite", "Lime")),
    Preset("Nebula Drift", listOf(
        PresetItem(WType.ANALOG, 0, 0, 4, 2), PresetItem(WType.MOON, 0, 2, 2, 2), PresetItem(WType.COMPASS, 2, 2, 2, 2),
        PresetItem(WType.SUN, 0, 4, 4, 2),
    ), true, builtinLook("Nebula", "Violet")),
    Preset("Night Watch", listOf(
        PresetItem(WType.NIXIE, 0, 0, 4, 2), PresetItem(WType.TIMER, 0, 2, 4, 2),
        PresetItem(WType.LANTERN, 0, 4, 2, 2), PresetItem(WType.VIALS, 2, 4, 2, 2),
    ), true, builtinLook("Solar", "Amber")),
    Preset("Stellarite Prime", listOf(
        PresetItem(WType.DIGITAL, 0, 0, 4, 2), PresetItem(WType.WEATHER, 0, 2, 2, 3), PresetItem(WType.VIALS, 2, 2, 2, 3),
        PresetItem(WType.MEMO, 0, 5, 4, 2),
    ), true, builtinLook("Stellarite", "Teal")),
)

/** The colours part of a ready-made preset, using the standard custom colours. */
private fun builtinLook(palette: String, signal: String) = Look(
    LookColors(Holo.STYLE, palette, signal, 0xFF00B3A6.toInt(), 0xFF111C36.toInt(), 0xFF00C2B3.toInt()), null, 0.45f,
)

private fun encodePresets(list: List<Preset>): String = JSONArray().also { arr ->
    list.forEach { p ->
        val its = JSONArray()
        p.items.forEach { i ->
            its.put(
                JSONObject().put("t", i.type.name).put("c", i.col).put("r", i.row)
                    .put("cs", i.cols).put("rs", i.rows).put("id", i.id)
            )
        }
        val o = JSONObject().put("name", p.name).put("items", its)
        encodeLook(p.look)?.let { o.put("look", it) }
        arr.put(o)
    }
}.toString()

private fun decodePresets(s: String?): List<Preset> {
    if (s == null) return emptyList()
    return try {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val its = o.getJSONArray("items")
            Preset(
                o.getString("name"),
                (0 until its.length()).mapNotNull { j ->
                    val x = its.getJSONObject(j)
                    val t = WType.entries.firstOrNull { e -> e.name == x.getString("t") } ?: return@mapNotNull null
                    PresetItem(t, x.getInt("c"), x.getInt("r"), x.getInt("cs"), x.getInt("rs"), x.optLong("id", 0L))
                },
                look = decodeLook(o.optJSONObject("look")),
            )
        }
    } catch (e: Exception) { emptyList() }
}

/** Holds every widget's position and size (in dp), the grid rules, folders and presets. */
class WidgetBoard(private val prefs: SharedPreferences, land: Boolean = false, private val appCtx: Context) {
    /** Portrait and landscape each keep their own layout, so rotating never piles the widgets up. */
    private var landscape by mutableStateOf(land)
    private var reflow = false // this orientation has no layout of its own yet

    /** Which home page is showing (0 is the first) and how many pages there are. Page 0 keeps the original save keys. */
    var page by mutableIntStateOf(0)
        private set
    var pageCount by mutableIntStateOf(maxOf(1, prefs.getInt("pages", 1)))
        private set

    private fun layoutKey(l: Boolean, p: Int = page) =
        (if (l) "widgets_land" else "widgets_v1") + (if (p == 0) "" else "_p$p")
    private fun loadFor(l: Boolean): List<WidgetItem>? {
        val own = decode(prefs.getString(layoutKey(l), null))
        if (own != null) {
            if (stale(l, page)) reflow = true // saved at another grid size: re-snap once the screen size is known
            return own
        }
        val other = decode(prefs.getString(layoutKey(!l), null))
        if (other != null) reflow = true
        return other
    }

    /**
     * The grid a layout was saved at is remembered, so pages and orientations that were not on screen
     * when the grid size changed get re-snapped when you next visit them. These read prefs directly
     * because loadFor runs before the other properties are set up.
     */
    private fun currentSig(): String =
        if (prefs.getBoolean("grid", true)) (prefs.getString("grid_kind", "auto") ?: "auto") else "free"

    private fun sigKey(l: Boolean, p: Int) = "gsig_" + layoutKey(l, p)

    private fun stale(l: Boolean, p: Int): Boolean {
        val now = currentSig()
        if (now == "free") return false
        val saved = prefs.getString(sigKey(l, p), null) ?: return false // saved before this feature: leave it alone
        return saved != now
    }

    var items by mutableStateOf(loadFor(land))
    var areaW by mutableFloatStateOf(0f)
    var areaH by mutableFloatStateOf(0f)

    /** true = fixed grid (snap, no overlap); false = floating (place anywhere). */
    var gridMode by mutableStateOf(prefs.getBoolean("grid", true))
        private set

    var userPresets by mutableStateOf(decodePresets(prefs.getString("presets_v1", null)))
        private set

    /** Each Data folder widget has its own name and its own list of apps. */
    val folderNames = mutableStateMapOf<Long, String>()
    val folderApps = mutableStateMapOf<Long, List<String>>()

    /** Each Link cache widget has its own name and its own list of web links. */
    val linkNames = mutableStateMapOf<Long, String>()
    val linkItems = mutableStateMapOf<Long, List<WebLink>>()

    init {
        loadExtras(items.orEmpty())
    }

    /** Reads in the folder and link contents for any of these widgets that are not loaded yet. */
    private fun loadExtras(list: List<WidgetItem>) {
        list.forEach {
            if (it.type == WType.FOLDER && it.id !in folderNames) loadFolder(it.id)
            if (it.type == WType.LINKS && it.id !in linkNames) loadLinks(it.id)
        }
    }

    private fun loadLinks(id: Long) {
        linkNames[id] = prefs.getString("lname_$id", "Links") ?: "Links"
        linkItems[id] = decodeLinks(prefs.getString("llinks_$id", null))
    }

    private fun saveLinks(id: Long) {
        prefs.edit()
            .putString("lname_$id", linkNames[id] ?: "Links")
            .putString("llinks_$id", encodeLinks(linkItems[id].orEmpty()))
            .apply()
    }

    fun addLink(id: Long, link: WebLink) {
        linkItems[id] = linkItems[id].orEmpty() + link
        saveLinks(id)
    }

    fun updateLink(id: Long, index: Int, link: WebLink) {
        val cur = linkItems[id].orEmpty()
        if (index in cur.indices) {
            linkItems[id] = cur.mapIndexed { i, old -> if (i == index) link else old }
            saveLinks(id)
        }
    }

    fun removeLink(id: Long, index: Int) {
        val cur = linkItems[id].orEmpty()
        if (index in cur.indices) {
            linkItems[id] = cur.filterIndexed { i, _ -> i != index }
            saveLinks(id)
        }
    }

    fun renameLinks(id: Long, name: String) {
        linkNames[id] = name
        saveLinks(id)
    }

    private fun loadFolder(id: Long) {
        folderNames[id] = prefs.getString("fname_$id", "Folder") ?: "Folder"
        folderApps[id] = (prefs.getString("fapps_$id", "") ?: "").split(",").filter { it.isNotBlank() }
    }

    private fun saveFolder(id: Long) {
        prefs.edit()
            .putString("fname_$id", folderNames[id] ?: "Folder")
            .putString("fapps_$id", folderApps[id].orEmpty().joinToString(","))
            .apply()
    }

    private fun hasData(id: Long) = prefs.contains("fname_$id") || prefs.contains("note_$id") || prefs.contains("notes_$id")

    fun addToFolder(id: Long, pkg: String) {
        val cur = folderApps[id].orEmpty()
        if (pkg !in cur) { folderApps[id] = cur + pkg; saveFolder(id) }
    }

    fun removeFromFolder(id: Long, pkg: String) {
        folderApps[id] = folderApps[id].orEmpty() - pkg
        saveFolder(id)
    }

    fun renameFolder(id: Long, name: String) {
        folderNames[id] = name
        saveFolder(id)
    }

    // ----- grid maths -----

    /** "auto" fits as many cells as the screen allows. "5x10" and "4x9" are fixed (columns x rows in portrait). */
    var gridKind by mutableStateOf(prefs.getString("grid_kind", "auto") ?: "auto")
        private set

    /** Fixed grids turn with the phone: 5 x 10 becomes 10 x 5 in landscape, so the cells stay roughly square. */
    private val fixedCols: Int?
        get() = when (gridKind) {
            "5x10" -> if (landscape) 10 else 5
            "4x9" -> if (landscape) 9 else 4
            else -> null
        }
    private val fixedRows: Int?
        get() = when (gridKind) {
            "5x10" -> if (landscape) 5 else 10
            "4x9" -> if (landscape) 4 else 9
            else -> null
        }

    private val gridCols: Int
        get() = fixedCols ?: if (areaW > 0f) maxOf(4, ((areaW + gap) / 94f).toInt()) else 4
    private val gap = 8f
    val columns: Int get() = gridCols
    val cellW: Float get() = if (areaW > 0f) (areaW - gap * (gridCols - 1)) / gridCols else 80f
    val cellH: Float
        get() {
            val rows = fixedRows
            return if (rows != null && areaH > 0f) (areaH - gap * (rows - 1)) / rows else cellW * 0.85f
        }
    val stepX: Float get() = cellW + gap
    val stepY: Float get() = cellH + gap
    val maxRows: Int
        get() = fixedRows ?: if (areaH > 0f) maxOf(1, ((areaH + gap) / stepY).toInt()) else 8

    private fun spanW(n: Int) = n * cellW + (n - 1) * gap
    private fun spanH(n: Int) = n * cellH + (n - 1) * gap
    private fun colsOf(w: Float) = ((w + gap) / stepX).roundToInt().coerceIn(1, gridCols)
    private fun rowsOf(h: Float) = ((h + gap) / stepY).roundToInt().coerceAtLeast(1)
    private fun minColsOf(t: WType) = ceil((t.minW + gap) / stepX).toInt().coerceIn(1, gridCols)
    private fun minRowsOf(t: WType) = ceil((t.minH + gap) / stepY).toInt().coerceAtLeast(1)

    private fun place(w: WidgetItem, col: Int, row: Int, c: Int, r: Int) =
        w.copy(x = col * stepX, y = row * stepY, w = spanW(c), h = spanH(r))

    private fun snapped(w: WidgetItem): WidgetItem {
        val c = maxOf(colsOf(w.w), minColsOf(w.type))
        val r = maxOf(rowsOf(w.h), minRowsOf(w.type))
        val col = (w.x / stepX).roundToInt().coerceIn(0, gridCols - c)
        val row = (w.y / stepY).roundToInt().coerceIn(0, maxOf(0, maxRows - r))
        return place(w, col, row, c, r)
    }

    private fun overlaps(a: WidgetItem, b: WidgetItem) =
        a.x < b.x + b.w - 1f && b.x < a.x + a.w - 1f && a.y < b.y + b.h - 1f && b.y < a.y + a.h - 1f

    private fun newOcc() = Array(maxRows) { BooleanArray(gridCols) }

    private fun Array<BooleanArray>.fits(col: Int, row: Int, c: Int, r: Int): Boolean {
        if (col < 0 || row < 0 || col + c > gridCols || row + r > size) return false
        for (rr in row until row + r) for (cc in col until col + c) if (this[rr][cc]) return false
        return true
    }

    private fun Array<BooleanArray>.mark(col: Int, row: Int, c: Int, r: Int) {
        for (rr in row until minOf(row + r, size)) for (cc in col until minOf(col + c, gridCols)) this[rr][cc] = true
    }

    /** The free spot closest to (nearCol, nearRow) that fits c x r cells. */
    private fun Array<BooleanArray>.findSpot(c: Int, r: Int, nearCol: Int, nearRow: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestD = Int.MAX_VALUE
        for (row in 0..(size - r)) for (col in 0..(gridCols - c)) {
            if (fits(col, row, c, r)) {
                val d = (col - nearCol) * (col - nearCol) + (row - nearRow) * (row - nearRow)
                if (d < bestD) { bestD = d; best = col to row }
            }
        }
        return best
    }

    private fun markAll(occ: Array<BooleanArray>, list: List<WidgetItem>) {
        list.forEach { o ->
            occ.mark((o.x / stepX).roundToInt().coerceAtLeast(0), (o.y / stepY).roundToInt().coerceAtLeast(0), colsOf(o.w), rowsOf(o.h))
        }
    }

    /** Snap everything to the grid and remove overlaps. If dropIfNoRoom, widgets with no space are left out. */
    private fun tidyList(list: List<WidgetItem>, dropIfNoRoom: Boolean): List<WidgetItem> {
        val occ = newOcc()
        val out = mutableListOf<WidgetItem>()
        for (w in list.sortedWith(compareBy({ it.y }, { it.x }))) {
            var c = maxOf(colsOf(w.w), minColsOf(w.type))
            var r = maxOf(rowsOf(w.h), minRowsOf(w.type))
            val nc = (w.x / stepX).roundToInt().coerceIn(0, gridCols - c)
            val nr = (w.y / stepY).roundToInt().coerceAtLeast(0)
            var spot = occ.findSpot(c, r, nc, nr)
            if (spot == null) {
                c = minColsOf(w.type); r = minRowsOf(w.type)
                spot = occ.findSpot(c, r, nc, nr)
            }
            if (spot != null) {
                occ.mark(spot.first, spot.second, c, r)
                out += place(w, spot.first, spot.second, c, r)
            } else if (!dropIfNoRoom) {
                out += place(w, nc.coerceIn(0, gridCols - c), nr, c, r)
            }
        }
        return out
    }

    fun useGrid(on: Boolean) {
        gridMode = on
        prefs.edit().putBoolean("grid", on).apply()
        if (on) {
            items = items?.let { tidyList(it, false) }
        }
        persist() // also records which grid this layout now fits
    }

    /** Switch between Auto, 5 x 10 and 4 x 9. This page is re-snapped now; the others when you next visit them. */
    fun chooseGrid(kind: String) {
        if (kind == gridKind) return
        gridKind = kind
        prefs.edit().putString("grid_kind", kind).apply()
        if (gridMode) items = items?.let { tidyList(it, false) }
        persist()
    }

    /**
     * The other orientation keeps its own arrangement, but gets the same widgets: anything you added
     * is placed in a free spot, and anything you removed is taken out.
     */
    private fun syncWith(own: List<WidgetItem>, source: List<WidgetItem>): List<WidgetItem> {
        val ids = source.map { it.id }.toSet()
        val keep = own.filter { it.id in ids }
        val missing = source.filter { m -> own.none { it.id == m.id } }
        if (missing.isEmpty() && keep.size == own.size) return own
        val occ = newOcc()
        markAll(occ, keep)
        val added = mutableListOf<WidgetItem>()
        for (m in missing) {
            var c = maxOf(colsOf(m.w), minColsOf(m.type))
            var r = maxOf(rowsOf(m.h), minRowsOf(m.type))
            var spot = occ.findSpot(c, r, 0, 0)
            if (spot == null) {
                c = minColsOf(m.type)
                r = minRowsOf(m.type)
                spot = occ.findSpot(c, r, 0, 0)
            }
            if (spot != null) {
                occ.mark(spot.first, spot.second, c, r)
                added += place(m, spot.first, spot.second, c, r)
            }
        }
        return keep + added
    }

    // ----- per-widget settings -----

    val widgetSettings = mutableStateMapOf<Long, WidgetSettings>()

    fun settingsOf(id: Long): WidgetSettings =
        widgetSettings[id] ?: decodeSettings(prefs.getString("wset_$id", null))

    fun setSettings(id: Long, s: WidgetSettings) {
        widgetSettings[id] = s
        prefs.edit().putString("wset_$id", encodeSettings(s)).apply()
    }

    // ----- pages -----

    /** The orientation a page was last saved in. Its widget list is the freshest one. */
    private fun lastOrientation(p: Int) = prefs.getBoolean("lo_$p", false)

    private fun savedPage(p: Int): List<WidgetItem> =
        (decode(prefs.getString(layoutKey(landscape, p), null)) ?: decode(prefs.getString(layoutKey(!landscape, p), null))).orEmpty()

    /** Reads the current page for the current orientation, re-fitting it if it was last used the other way round. */
    private fun loadPage(): List<WidgetItem> {
        reflow = false
        val own = decode(prefs.getString(layoutKey(landscape), null))?.let { fresh(it, landscape, page) }
        val other = decode(prefs.getString(layoutKey(!landscape), null))
        if (own == null) return if (other == null) emptyList() else tidyList(other, false)
        if (other != null && lastOrientation(page) != landscape) return syncWith(own, other)
        return own
    }

    /** Re-snaps a saved layout if it was saved at a different grid size. Only for the orientation on screen. */
    private fun fresh(list: List<WidgetItem>, l: Boolean, p: Int): List<WidgetItem> =
        if (stale(l, p)) tidyList(list, false) else list

    fun goToPage(p: Int) {
        if (p == page || p !in 0 until pageCount || areaW <= 0f) return
        persist()
        Sfx.play(Sfx.Kind.SWIPE)
        page = p
        val list = loadPage()
        items = list
        loadExtras(list)
        persist()
    }

    /** Adds an empty page at the end and goes to it. Returns its number, or -1 if the limit is reached. */
    fun addPage(): Int {
        if (pageCount >= MAX_PAGES) return -1
        pageCount += 1
        prefs.edit().putInt("pages", pageCount).apply()
        goToPage(pageCount - 1)
        return pageCount - 1
    }

    fun widgetsOnPage(p: Int): Int = if (p == page) items?.size ?: 0 else savedPage(p).size

    /** Deletes the page you are on, with its widgets, and slides the later pages down. */
    fun removeCurrentPage() {
        if (pageCount <= 1) return
        val gone = page
        val ids = (items.orEmpty() + decode(prefs.getString(layoutKey(false), null)).orEmpty() +
            decode(prefs.getString(layoutKey(true), null)).orEmpty()).map { it.id }.toSet()
        ids.forEach { forget(it) }
        val ed = prefs.edit()
        for (q in gone until pageCount - 1) {
            for (l in listOf(false, true)) {
                val v = prefs.getString(layoutKey(l, q + 1), null)
                if (v == null) ed.remove(layoutKey(l, q)) else ed.putString(layoutKey(l, q), v)
                val sv = prefs.getString(sigKey(l, q + 1), null)
                if (sv == null) ed.remove(sigKey(l, q)) else ed.putString(sigKey(l, q), sv)
            }
            ed.putBoolean("lo_$q", prefs.getBoolean("lo_${q + 1}", false))
        }
        val last = pageCount - 1
        ed.remove(layoutKey(false, last)).remove(layoutKey(true, last)).remove("lo_$last")
            .remove(sigKey(false, last)).remove(sigKey(true, last))
        Holo.closeGapAfterPage(appCtx, gone, pageCount)
        pageCount -= 1
        ed.putInt("pages", pageCount).apply()
        page = minOf(gone, pageCount - 1)
        val list = loadPage()
        items = list
        loadExtras(list)
        persist()
    }

    /** Swaps the page you are on with its neighbour (-1 = earlier, +1 = later) and follows it. Widgets and pictures go along. */
    fun movePageBy(dir: Int): Boolean {
        val from = page
        val to = from + dir
        if (to !in 0 until pageCount || areaW <= 0f) return false
        persist()
        val ed = prefs.edit()
        for (l in listOf(false, true)) {
            val a = prefs.getString(layoutKey(l, from), null)
            val b = prefs.getString(layoutKey(l, to), null)
            if (b == null) ed.remove(layoutKey(l, from)) else ed.putString(layoutKey(l, from), b)
            if (a == null) ed.remove(layoutKey(l, to)) else ed.putString(layoutKey(l, to), a)
            val sa = prefs.getString(sigKey(l, from), null)
            val sb = prefs.getString(sigKey(l, to), null)
            if (sb == null) ed.remove(sigKey(l, from)) else ed.putString(sigKey(l, from), sb)
            if (sa == null) ed.remove(sigKey(l, to)) else ed.putString(sigKey(l, to), sa)
        }
        val la = prefs.getBoolean("lo_$from", false)
        val lb = prefs.getBoolean("lo_$to", false)
        ed.putBoolean("lo_$from", lb).putBoolean("lo_$to", la)
        ed.apply()
        Holo.swapPageWallpapers(appCtx, from, to)
        page = to
        val list = loadPage()
        items = list
        loadExtras(list)
        persist()
        return true
    }

    /** Moves a widget (keeping its notes and settings) to a free spot on another page. Returns false if that page is full. */
    fun moveToPage(id: Long, target: Int): Boolean {
        if (target == page || target !in 0 until pageCount) return false
        val w = items?.firstOrNull { it.id == id } ?: return false
        val targetList = (decode(prefs.getString(layoutKey(landscape, target), null))?.let { fresh(it, landscape, target) }
            ?: decode(prefs.getString(layoutKey(!landscape, target), null))?.let { tidyList(it, false) }).orEmpty()
        val occ = newOcc()
        markAll(occ, targetList)
        var c = maxOf(colsOf(w.w), minColsOf(w.type))
        var r = maxOf(rowsOf(w.h), minRowsOf(w.type))
        var spot = occ.findSpot(c, r, 0, 0)
        if (spot == null) {
            c = minColsOf(w.type); r = minRowsOf(w.type)
            spot = occ.findSpot(c, r, 0, 0)
        }
        if (spot == null) return false
        val moved = targetList + place(w, spot.first, spot.second, c, r)
        prefs.edit()
            .putString(layoutKey(landscape, target), encode(moved))
            .putString(sigKey(landscape, target), currentSig())
            .putBoolean("lo_$target", landscape)
            .apply()
        items = items?.filter { it.id != id }
        persist()
        return true
    }

    private fun everyWidget(): List<WidgetItem> =
        (0 until pageCount).flatMap { p -> if (p == page) items.orEmpty() else savedPage(p) }

    /** Every Data folder widget on every page, for the Add to folder chooser. */
    fun allFolders(): List<WidgetItem> = everyWidget().filter { it.type == WType.FOLDER }

    /** Loads folder and link contents for every page, so a folder on another page can be edited safely. */
    fun loadAllExtras() = loadExtras(everyWidget())

    // ----- first run / defaults -----

    fun ensureDefaults(land: Boolean) {
        if (areaW <= 0f || areaH <= 0f) return
        val cur = items
        if (cur != null && land != landscape) {
            // the phone was turned: keep this layout for its own orientation and switch to the other one
            persist()
            landscape = land
            reflow = false
            val own = decode(prefs.getString(layoutKey(land), null))?.let { fresh(it, land, page) }
            items = if (own == null) tidyList(cur, false) else syncWith(own, cur)
            persist()
            return
        }
        if (cur != null && reflow) {
            reflow = false
            items = tidyList(cur, false)
            persist()
            return
        }
        if (cur == null) {
            items = tidyList(instantiate(BUILTIN_PRESETS[0], emptyList()), true)
            prefs.edit().putBoolean("grid_done", true).apply()
            persist()
        } else if (gridMode && !prefs.getBoolean("grid_done", false)) {
            // layouts made before the grid existed get tidied once
            items = tidyList(cur, false)
            prefs.edit().putBoolean("grid_done", true).apply()
            persist()
        }
    }

    // ----- drag / resize -----

    private var start: WidgetItem? = null

    /** How far (in dp) a drag has pushed past the left (negative) or right (positive) edge. */
    private var edgePush = 0f

    fun begin(id: Long) {
        start = items?.firstOrNull { it.id == id }
        edgePush = 0f
    }

    private fun update(id: Long, f: (WidgetItem) -> WidgetItem) {
        items = items?.map { if (it.id == id) f(it) else it }
    }

    fun move(id: Long, dx: Float, dy: Float) {
        val cur = items?.firstOrNull { it.id == id } ?: return
        val maxX = maxOf(0f, areaW - cur.w)
        val rawX = cur.x + dx
        edgePush = when {
            rawX < 0f -> (if (edgePush < 0f) edgePush else 0f) + rawX
            rawX > maxX -> (if (edgePush > 0f) edgePush else 0f) + (rawX - maxX)
            else -> 0f
        }
        update(id) {
            it.copy(
                x = rawX.coerceIn(0f, maxX),
                y = (it.y + dy).coerceIn(0f, maxOf(0f, areaH - it.h)),
            )
        }
    }

    /**
     * When a drag ends after pushing the widget well past a side edge, sends it to the next page on that side
     * (a new page if you push past the last one). Returns 0 = nothing happened, 1 = moved, 2 = no room there.
     */
    fun sendToEdge(id: Long): Int {
        val push = edgePush
        edgePush = 0f
        if (kotlin.math.abs(push) < 48f) return 0
        val target = page + if (push < 0f) -1 else 1
        if (target < 0) return 0
        var created = false
        if (target >= pageCount) {
            if (pageCount >= MAX_PAGES) return 2
            pageCount += 1
            prefs.edit().putInt("pages", pageCount).apply()
            created = true
        }
        if (!moveToPage(id, target)) {
            if (created) {
                pageCount -= 1
                prefs.edit().putInt("pages", pageCount).apply()
            }
            return 2
        }
        goToPage(target)
        return 1
    }

    fun resize(id: Long, dw: Float, dh: Float) = update(id) {
        val maxW = maxOf(it.type.minW, areaW - it.x)
        val maxH = maxOf(it.type.minH, areaH - it.y)
        it.copy(
            w = (it.w + dw).coerceIn(it.type.minW, maxW),
            h = (it.h + dh).coerceIn(it.type.minH, maxH),
        )
    }

    /** Called when a drag or resize ends. In grid mode the widget snaps into a free spot. */
    fun settle(id: Long, resizing: Boolean) {
        val list = items ?: return
        if (gridMode) {
            val cur = list.firstOrNull { it.id == id } ?: return
            val others = list.filter { it.id != id }
            val s = snapped(cur)
            val back = start?.takeIf { it.id == id }?.let { snapped(it) }
            val result = when {
                others.none { overlaps(s, it) } -> s
                resizing -> back ?: s
                else -> nearestFree(cur, others) ?: back ?: s
            }
            items = list.map { if (it.id == id) result else it }
        }
        if (!resizing) Sfx.play(Sfx.Kind.DROP)
        persist()
    }

    private fun nearestFree(cur: WidgetItem, others: List<WidgetItem>): WidgetItem? {
        val occ = newOcc()
        markAll(occ, others)
        val c = maxOf(colsOf(cur.w), minColsOf(cur.type))
        val r = maxOf(rowsOf(cur.h), minRowsOf(cur.type))
        val spot = occ.findSpot(c, r, (cur.x / stepX).roundToInt(), (cur.y / stepY).roundToInt()) ?: return null
        return place(cur, spot.first, spot.second, c, r)
    }

    // ----- add / remove -----

    /** Returns the new widget's id, or -1 if there is no room left on the grid. */
    fun add(type: WType): Long {
        val id = System.currentTimeMillis()
        if (gridMode) {
            val occ = newOcc()
            markAll(occ, items.orEmpty())
            var c = maxOf(colsOf(type.defW), minColsOf(type))
            var r = maxOf(rowsOf(type.defH), minRowsOf(type))
            var spot = occ.findSpot(c, r, 0, 0)
            if (spot == null) {
                c = minColsOf(type); r = minRowsOf(type)
                spot = occ.findSpot(c, r, 0, 0)
            }
            if (spot == null) return -1L
            items = items.orEmpty() + place(WidgetItem(id, type, 0f, 0f, 0f, 0f), spot.first, spot.second, c, r)
        } else {
            val n = items?.size ?: 0
            val w = minOf(type.defW, areaW)
            val h = type.defH
            val x = ((areaW - w) / 2f).coerceAtLeast(0f)
            val y = (24f + (n % 6) * 28f).coerceAtMost(maxOf(0f, areaH - h))
            items = items.orEmpty() + WidgetItem(id, type, x, y, w, h)
        }
        if (type == WType.FOLDER) {
            folderNames[id] = "Folder"
            folderApps[id] = emptyList()
            saveFolder(id)
        }
        if (type == WType.LINKS) {
            linkNames[id] = "Links"
            linkItems[id] = emptyList()
            saveLinks(id)
        }
        persist()
        return id
    }

    fun remove(id: Long) {
        items = items?.filter { it.id != id }
        forget(id)
        persist()
    }

    /** Throws away everything saved for one widget. */
    private fun forget(id: Long) {
        folderNames.remove(id)
        folderApps.remove(id)
        linkNames.remove(id)
        linkItems.remove(id)
        widgetSettings.remove(id)
        prefs.edit().remove("wset_$id").remove("lname_$id").remove("llinks_$id").remove("note_$id").remove("notes_$id").remove("fname_$id").remove("fapps_$id").remove("chr_acc_$id").remove("chr_start_$id").remove("chr_lap_$id")
            .remove("ch_run_$id").remove("ch_base_$id").remove("ch_acc_$id").remove("ch_lap_$id")
            .remove("tm_mode_$id").remove("tm_set_$id").remove("tm_end_$id").remove("tm_left_$id").remove("tm_slot_$id").remove("tm_set1_$id").remove("tm_end1_$id").remove("tm_left1_$id").remove("tm_set2_$id").remove("tm_end2_$id").remove("tm_left2_$id").remove("al_min_$id").remove("al_at_$id").apply()
        TimerEngine.forget(appCtx, id)
        AppWidgets.release(appCtx, prefs.getInt("aw_$id", -1))
        prefs.edit().remove("aw_$id").apply()
    }

    fun persist() {
        items?.let {
            prefs.edit().putString(layoutKey(landscape), encode(it)).putString(sigKey(landscape, page), currentSig())
                .putBoolean("lo_$page", landscape).apply()
        }
    }

    // ----- presets -----

    private fun snapshot(name: String): Preset? {
        // another app's widget belongs to this phone only, so presets leave it out
        val list = items.orEmpty().filter { it.type != WType.APPWIDGET }
        if (list.isEmpty()) return null
        val tidy = tidyList(list, false)
        return Preset(
            name,
            tidy.map { PresetItem(it.type, (it.x / stepX).roundToInt(), (it.y / stepY).roundToInt(), colsOf(it.w), rowsOf(it.h), it.id) },
        )
    }

    /**
     * The look as it is now. [colours] includes the palette, signal colour and your own colours.
     * [wall] includes the wallpaper (a private copy is kept) or, if there is none, a note to clear it.
     */
    private fun captureLook(colours: Boolean, wall: Boolean): Look? {
        if (!colours && !wall) return null
        val c = if (colours) Holo.currentColors() else null
        val w: String? = if (wall) {
            if (Holo.hasWallpaper) (Holo.copyWallpaperTo(appCtx, "pw_${System.currentTimeMillis()}.jpg") ?: "") else ""
        } else {
            null
        }
        return Look(c, w, Holo.wallDim)
    }

    private fun applyLook(l: Look) {
        l.colors?.let { Holo.applyColors(appCtx, it) }
        l.wall?.let { w ->
            if (w.isEmpty()) Holo.removeWallpaper(appCtx) else Holo.useWallpaperFile(appCtx, w)
            Holo.setDim(appCtx, l.dim)
        }
    }

    /** Deletes saved preset wallpapers that no preset refers to any more. */
    private fun sweepWallpapers() {
        Holo.sweepPresetWallpapers(appCtx, userPresets.mapNotNull { it.look?.wall }.filter { it.isNotEmpty() }.toSet())
    }

    private fun persistPresets() {
        prefs.edit().putString("presets_v1", encodePresets(userPresets)).apply()
    }

    /** Save the current layout under a name. Using an existing name updates that preset. */
    fun savePreset(name: String, colours: Boolean = true, wall: Boolean = true, sweep: Boolean = true) {
        val base = snapshot(name) ?: return
        val p = base.copy(look = captureLook(colours, wall))
        userPresets = userPresets.filter { it.name != name } + p
        persistPresets()
        if (sweep) sweepWallpapers()
    }

    /** A text code for the current layout. Send it to anyone and they can import it. Ids stay private to this phone. */
    fun exportCode(): String? {
        val p = snapshot("Shared layout") ?: return null
        // colours travel with the code; a wallpaper picture never does
        val clean = p.copy(items = p.items.map { it.copy(id = 0L) }, look = captureLook(true, false))
        val json = encodePresets(listOf(clean))
        return "CYBERSKIN1:" + Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /** Adds a shared layout to your presets (it is not applied). Returns null if the code is not valid. */
    fun importCode(code: String): Preset? {
        val raw = code.trim().removePrefix("CYBERSKIN1:").filter { !it.isWhitespace() }
        val json = try {
            String(Base64.decode(raw, Base64.URL_SAFE), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        val p = decodePresets(json).firstOrNull() ?: return null
        val items = p.items.take(20).map {
            it.copy(col = it.col.coerceIn(0, 11), row = it.row.coerceIn(0, 30), cols = it.cols.coerceIn(1, 12), rows = it.rows.coerceIn(1, 12), id = 0L)
        }
        if (items.isEmpty()) return null
        var name = "Imported layout"
        var n = 2
        while (userPresets.any { it.name == name }) { name = "Imported layout $n"; n++ }
        val imported = Preset(name, items, false, sanitizeLook(p.look))
        userPresets = userPresets + imported
        persistPresets()
        return imported
    }

    /** Renames one of your presets. Returns null on success, or a message saying why it was refused. */
    fun renamePreset(old: String, new: String): String? {
        val n = new.trim()
        if (n.isEmpty()) return "Give the preset a name."
        if (n == old) return null
        if (BUILTIN_PRESETS.any { it.name.equals(n, ignoreCase = true) }) return "That name belongs to a built-in preset."
        if (userPresets.any { it.name != old && it.name.equals(n, ignoreCase = true) }) return "You already have a preset with that name."
        userPresets = userPresets.map { if (it.name == old) it.copy(name = n) else it }
        persistPresets()
        return null
    }

    fun deletePreset(name: String) {
        userPresets = userPresets.filter { it.name != name }
        persistPresets()
        sweepWallpapers()
    }

    /**
     * Turn a preset into widgets. Widgets you already have (same id, else same type) are reused,
     * so notes and folder contents survive.
     */
    private fun instantiate(p: Preset, existing: List<WidgetItem>): List<WidgetItem> {
        val used = mutableSetOf<Long>()
        var stamp = System.currentTimeMillis()
        return p.items.map { pi ->
            val reuse = existing.firstOrNull { it.id == pi.id && pi.id != 0L && it.id !in used && it.type == pi.type }
                ?: existing.firstOrNull { it.type == pi.type && it.id !in used }
            val id = when {
                reuse != null -> reuse.id
                pi.id != 0L && pi.id !in used && existing.none { it.id == pi.id } && hasData(pi.id) -> pi.id
                else -> ++stamp
            }
            used += id
            WidgetItem(id, pi.type, pi.col * stepX, pi.row * stepY, spanW(pi.cols), spanH(pi.rows))
        }
    }

    /** The look (colours) of a built-in or saved preset, or null if it has none. Used by Scheduled looks. */
    fun lookOf(name: String): Look? = (BUILTIN_PRESETS + userPresets).firstOrNull { it.name == name }?.look

    /** Names of every preset that carries colours, built-ins first. */
    fun lookNames(): List<String> = (BUILTIN_PRESETS + userPresets).filter { it.look?.colors != null && it.name != AUTO_PRESET }.map { it.name }.distinct()

    fun applyPreset(p: Preset): Int {
        savePreset(AUTO_PRESET, sweep = false) // undo point, with today's colours and wallpaper too
        val raw = instantiate(p, items.orEmpty())
        val placed = tidyList(raw, true)
        items = placed
        placed.filter { it.type == WType.FOLDER }.forEach { if (it.id !in folderNames) loadFolder(it.id) }
        placed.filter { it.type == WType.LINKS }.forEach { if (it.id !in linkNames) loadLinks(it.id) }
        persist()
        p.look?.let { applyLook(it) }
        sweepWallpapers()
        return raw.size - placed.size
    }
}

// ───────────────────────── Root ─────────────────────────

@Composable
fun LauncherRoot(tick: Int, homeSignal: Int, active: Boolean = true) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("cyberskinos", Context.MODE_PRIVATE) }
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val board = remember { WidgetBoard(prefs, land, ctx.applicationContext) }
    val focusManager = LocalFocusManager.current
    val rootView = LocalView.current
    LaunchedEffect(homeSignal) { dropFocus(ctx, rootView, focusManager) }
    LaunchedEffect(active) { if (!active) dropFocus(ctx, rootView, focusManager) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }

    var pinned by remember { mutableStateOf(loadPinned(ctx, prefs)) }
    var counts by remember { mutableStateOf(loadCounts(prefs)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("anim", true)) }
    var recent by remember { mutableStateOf(loadRecent(prefs)) }
    var recentOn by remember { mutableStateOf(prefs.getBoolean("recent_on", true)) }
    var drawer by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var settings by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var puff by remember { mutableIntStateOf(0) } // counts the times pulse rings should spread from the star
    var appWidgetChooser by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<Long?>(null) }
    var openLinks by remember { mutableStateOf<Long?>(null) }
    var linkEdit by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var menuFolder by remember { mutableStateOf<Long?>(null) }
    var folderPick by remember { mutableStateOf<AppInfo?>(null) }
    var pagesDialog by remember { mutableStateOf(false) }
    var confirmRemovePage by remember { mutableStateOf(false) }
    val pickPageWall = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = Holo.installPageWallpaper(ctx, board.page, uri)
            Toast.makeText(ctx, if (ok) "Page wallpaper set" else "That picture could not be used.", Toast.LENGTH_LONG).show()
        }
    }
    var widgetDialog by remember { mutableStateOf<Long?>(null) }
    var creditsDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var presetDialog by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var hiddenDialog by remember { mutableStateOf(false) }
    var scheduleDialog by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    var renameFrom by remember { mutableStateOf<String?>(null) }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = Holo.installCustomFont(ctx, uri)
            Toast.makeText(ctx, if (ok) "Font installed" else "That file is not a usable .ttf or .otf font.", Toast.LENGTH_LONG).show()
        }
    }
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = Holo.installWallpaper(ctx, uri)
            Toast.makeText(ctx, if (ok) "Wallpaper set" else "That picture could not be used.", Toast.LENGTH_LONG).show()
        }
    }
    var hidden by remember { mutableStateOf(loadHidden(prefs)) }

    // Backup, restore and reset (Settings > Backup and reset)
    val ioScope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) } // "layout", "defaults" or "restore"
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var lastBackup by remember { mutableStateOf(Backup.lastBackup(ctx)) }
    var remindOn by remember { mutableStateOf(Backup.remindOn(ctx)) }
    var remindDialog by remember { mutableStateOf(false) }
    var hasRescue by remember { mutableStateOf(Backup.hasRescue(ctx)) }
    var safeNotice by remember { mutableStateOf(SafeMode.notice(ctx)) }
    val makeBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) ioScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { Backup.export(ctx, it); true } ?: false
                } catch (e: Exception) { false }
            }
            if (ok) { Backup.markBackedUp(ctx); lastBackup = Backup.lastBackup(ctx) }
            Toast.makeText(ctx, if (ok) "Backup saved" else "The backup could not be saved.", Toast.LENGTH_LONG).show()
        }
    }
    val backupName = { "cyberskinos-backup-" + java.time.LocalDate.now() + ".json" }

    // Coming back to the home screen counts as noticing a ringing timer or alarm, so it goes quiet.
    LaunchedEffect(active) { if (active) TimerEngine.silence(ctx) }

    // The occasional shimmer of the hologram panels
    HoloShimmer(active, animations)

    // Scheduled looks: while the home screen is showing, check every half minute whether a rule fits.
    LaunchedEffect(active) {
        while (active) {
            try {
                LookSchedule.check(ctx, prefs, board)
            } catch (e: Exception) {
                // a bad rule must never stop the launcher
            }
            delay(30_000)
        }
    }

    // The program has now been running long enough to count as a good start (see SafeMode).
    LaunchedEffect(Unit) {
        delay(5000)
        SafeMode.healthy(ctx)
    }
    // Once a month, suggest a backup. Only when the home screen is in front, and never on the very first run.
    LaunchedEffect(active) {
        if (active && safeNotice == 0) {
            delay(2500)
            if (Backup.reminderDue(ctx)) remindDialog = true
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingRestore = uri; confirm = "restore" }
    }

    // Pressing the Home button closes everything and returns to the main screen.
    LaunchedEffect(homeSignal) {
        drawer = false; menuApp = null; settings = false; fabMenu = false; picker = false; editing = false
        openFolder = null; openLinks = null; linkEdit = null; menuFolder = null; folderPick = null; presetDialog = false; saveDialog = false; hiddenDialog = false; scheduleDialog = false; importDialog = false; renameFrom = null
        pagesDialog = false; confirmRemovePage = false; widgetDialog = null; creditsDialog = false; helpDialog = false
        confirm = null; pendingRestore = null
        board.goToPage(0)
    }
    BackHandler(enabled = drawer || menuApp != null || settings || fabMenu || picker || editing || folderPick != null || openFolder != null) {
        when {
            menuApp != null -> menuApp = null
            folderPick != null -> folderPick = null
            openFolder != null -> openFolder = null
            picker -> picker = false
            fabMenu -> fabMenu = false
            settings -> settings = false
            drawer -> drawer = false
            else -> editing = false
        }
    }

    fun setPinned(list: List<String>) {
        pinned = list
        prefs.edit().putString("pinned", list.joinToString(",")).apply()
    }

    fun setHiddenSet(set: Set<String>) {
        hidden = set
        prefs.edit().putString("hidden", set.joinToString(",")).apply()
    }

    val visibleApps = remember(apps, hidden) { apps.filter { it.packageName !in hidden } }

    fun applyPreset(p: Preset) {
        Sfx.play(Sfx.Kind.CHIME)
        val dropped = board.applyPreset(p)
        presetDialog = false
        settings = false
        Toast.makeText(
            ctx,
            "Applied ${p.name}" + (if (p.look != null) " with its colours" else "") + (if (dropped > 0) ". $dropped widget(s) did not fit." else ""),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun shareLayout() {
        val code = board.exportCode()
        if (code == null) {
            Toast.makeText(ctx, "Nothing to share yet. Add a widget first.", Toast.LENGTH_SHORT).show()
        } else {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "CyberSkinOS layout")
                .putExtra(Intent.EXTRA_TEXT, code)
            open(ctx, Intent.createChooser(send, "Share layout"))
        }
    }

    val launch: (AppInfo) -> Unit = { app ->
        counts = counts + (app.packageName to ((counts[app.packageName] ?: 0) + 1))
        recent = (listOf(app.packageName) + recent.filter { it != app.packageName }).take(MAX_RECENT)
        prefs.edit()
            .putString("counts", counts.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString("recent", recent.joinToString(","))
            .apply()
        launchApp(ctx, app)
    }

    CompositionLocalProvider(LocalAnimations provides animations, LocalActive provides active) {
        Surface(Modifier.fillMaxSize(), color = Holo.Void, contentColor = Holo.Text) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    board = board, apps = apps, pinned = pinned, counts = counts, hidden = hidden, tick = tick,
                    prefs = prefs, editing = editing,
                    onLaunch = launch,
                    onLongPress = { menuApp = it; menuFolder = null },
                    onOpenFolder = { openFolder = it },
                    onOpenLinks = { openLinks = it },
                    onFabTap = { Sfx.play(Sfx.Kind.OPEN); drawer = true },
                    onFabDoubleTap = { Sfx.play(Sfx.Kind.MENU); puff++; fabMenu = true },
                    onWidgetSettings = { widgetDialog = it },
                    overlayOpen = drawer || settings,
                    onEnterEdit = { Sfx.play(Sfx.Kind.ARRANGE); puff++; editing = true },
                    puff = puff,
                    onAdd = { picker = true },
                    onDone = { editing = false },
                )
                ScanLine(board.page)
                AnimatedVisibility(
                    visible = drawer,
                    enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                    exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
                ) {
                    DrawerScreen(
                        apps = visibleApps,
                        recent = if (recentOn) recent.mapNotNull { p -> visibleApps.firstOrNull { it.packageName == p } } else emptyList(),
                        onLaunch = launch,
                        onLongPress = { menuApp = it; menuFolder = null },
                        onClose = { drawer = false },
                    )
                }
                AnimatedVisibility(
                    visible = settings,
                    enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                    exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
                ) {
                    SettingsScreen(
                        board = board,
                        animations = animations,
                        onAnimations = { animations = it; prefs.edit().putBoolean("anim", it).apply() },
                        onPickFont = { pickFont.launch(arrayOf("*/*")) },
                        onPickWall = { pickWall.launch(arrayOf("image/*")) },
                        onPresets = { presetDialog = true },
                        onShare = { shareLayout() },
                        onImport = { importDialog = true },
                        onHidden = { hiddenDialog = true },
                        onSchedule = { scheduleDialog = true },
                        recentOn = recentOn,
                        onRecentOn = { recentOn = it; prefs.edit().putBoolean("recent_on", it).apply() },
                        onClearRecent = { recent = emptyList(); prefs.edit().remove("recent").apply() },
                        lastBackup = lastBackup,
                        remindOn = remindOn,
                        onRemind = { remindOn = it; Backup.setRemind(ctx, it) },
                        hasRescue = hasRescue,
                        onRescued = { confirm = "rescued" },
                        onBackup = { makeBackup.launch(backupName()) },
                        onRestore = { pickBackup.launch(arrayOf("*/*")) },
                        onResetLayout = { confirm = "layout" },
                        onRestoreDefaults = { confirm = "defaults" },
                        onCredits = { creditsDialog = true },
                        onHelp = { helpDialog = true },
                        onClose = { settings = false },
                    )
                }
            }
        }

        // Long-press menu for an app
        menuApp?.let { app ->
            val onShelf = app.packageName in pinned
            val isHidden = app.packageName in hidden
            val shortcuts = remember(app.packageName) { loadShortcuts(ctx, app.packageName) }
            val canShortcuts = remember { canUseShortcuts(ctx) }
            Dialog(onDismissRequest = { menuApp = null }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconPort(app.icon, size = 64.dp)
                        Text(app.label, style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                        Text(
                            (if (onShelf) "Unpin takes it out of Quick access." else "Pin puts it in Quick access.") +
                                (if (menuFolder != null) " Eject takes it out of this folder." else "") +
                                " Hide keeps it out of the Databank.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted, textAlign = TextAlign.Center,
                        )
                        if (shortcuts.isNotEmpty()) {
                            Text("Shortcuts", style = MaterialTheme.typography.labelLarge, color = Holo.Bright)
                            shortcuts.forEach { sc ->
                                ChoiceRow(sc.shortLabel.toString(), "") { startShortcut(ctx, sc); menuApp = null }
                            }
                        } else if (!canShortcuts) {
                            Text(
                                "Make CyberSkinOS your home app to see this app's shortcuts here.",
                                style = MaterialTheme.typography.labelLarge, color = Holo.Muted.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                            )
                        }
                        ButtonRow {
                            HoloButton(if (onShelf) "Unpin" else "Pin") {
                                setPinned(if (onShelf) pinned - app.packageName else pinned + app.packageName)
                                menuApp = null
                            }
                            HoloButton("Folder") { board.loadAllExtras(); folderPick = app; menuApp = null }
                            HoloButton(if (isHidden) "Show" else "Hide") {
                                setHiddenSet(if (isHidden) hidden - app.packageName else hidden + app.packageName)
                                menuApp = null
                            }
                            menuFolder?.let { fid ->
                                HoloButton("Eject") {
                                    board.removeFromFolder(fid, app.packageName)
                                    menuApp = null
                                }
                            }
                            HoloButton("Info") {
                                open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                                menuApp = null
                            }
                            HoloButton("Uninstall") {
                                open(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                                menuApp = null
                            }
                        }
                    }
                }
            }
        }

        // Double tap on the star: the Console
        if (fabMenu) {
            Dialog(onDismissRequest = { fabMenu = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StellariteMark(Modifier.size(44.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("CONSOLE", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        }
                        Text("Tap the star to open the Databank, double-tap it for this Console, and press and hold it, then drag, to move it. Arrange moves and resizes widgets and is where you add new ones, Presets saves and applies layouts, Pages adds and removes home pages, and Settings has everything else, including the colours.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        ButtonRow {
                            HoloButton("Arrange") { fabMenu = false; Sfx.play(Sfx.Kind.ARRANGE); puff++; editing = true }
                            HoloButton("Presets") { fabMenu = false; presetDialog = true }
                            HoloButton("Pages") { fabMenu = false; pagesDialog = true }
                            HoloButton("Settings") { fabMenu = false; settings = true }
                            HoloButton("Close") { fabMenu = false }
                        }
                    }
                }
            }
        }

        // Folder: choose which folder an app goes into
        folderPick?.let { app ->
            val folders = board.allFolders()
            Dialog(onDismissRequest = { folderPick = null }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ADD TO FOLDER", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text(app.label, style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        Column(
                            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            folders.forEach { f ->
                                val name = board.folderNames[f.id] ?: "Folder"
                                val n = board.folderApps[f.id].orEmpty().size
                                val has = app.packageName in board.folderApps[f.id].orEmpty()
                                ChoiceRow(name, if (has) "Already inside" else "$n ${if (n == 1) "app" else "apps"}") {
                                    board.addToFolder(f.id, app.packageName)
                                    Toast.makeText(ctx, "Added to $name", Toast.LENGTH_SHORT).show()
                                    folderPick = null
                                }
                            }
                        }
                        Text("New starts a fresh Data folder on your home screen.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        ButtonRow {
                            HoloButton("New") {
                                val id = board.add(WType.FOLDER)
                                if (id < 0L) {
                                    Toast.makeText(ctx, "No room left on the grid for a new folder.", Toast.LENGTH_LONG).show()
                                } else {
                                    board.addToFolder(id, app.packageName)
                                    Toast.makeText(ctx, "Created a new folder on your home screen", Toast.LENGTH_SHORT).show()
                                }
                                folderPick = null
                            }
                            HoloButton("Cancel") { folderPick = null }
                        }
                    }
                }
            }
        }

        // Folder: the opened pop-up
        openFolder?.let { fid ->
            FolderDialog(
                id = fid, board = board, apps = apps,
                onLaunch = { launch(it); openFolder = null },
                onLongPress = { menuApp = it; menuFolder = fid },
                onClose = { openFolder = null },
            )
        }

        // Link cache: the opened pop-up, and the add or edit form on top of it
        openLinks?.let { lid ->
            LinksDialog(id = lid, board = board, onEdit = { index -> linkEdit = lid to index }, onClose = { openLinks = null })
        }
        linkEdit?.let { (lid, index) ->
            LinkEditDialog(board = board, folder = lid, index = index, onDone = { linkEdit = null })
        }

        // Scheduled looks
        if (scheduleDialog) {
            ScheduleDialog(board = board, prefs = prefs, onClose = { scheduleDialog = false })
        }

        // Hidden apps
        if (hiddenDialog) {
            val hiddenApps = apps.filter { it.packageName in hidden }
            Dialog(onDismissRequest = { hiddenDialog = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HIDDEN APPS", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text(
                            "Hidden apps stay out of the Databank, search and Priority apps. Tap one to open it. This only tidies the view and does not lock anything.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                        if (hiddenApps.isEmpty()) {
                            Text(
                                "Nothing is hidden. Long-press an app in the Databank and choose Hide.",
                                style = MaterialTheme.typography.bodyMedium, color = Holo.Text,
                            )
                        } else {
                            Column(
                                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                hiddenApps.forEach { a ->
                                    HiddenRow(a, onOpen = { launch(a); hiddenDialog = false }, onShow = { setHiddenSet(hidden - a.packageName) })
                                }
                            }
                        }
                        ButtonRow { HoloButton("Close") { hiddenDialog = false } }
                    }
                }
            }
        }

        // Presets list
        if (presetDialog) {
            Dialog(onDismissRequest = { presetDialog = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PRESETS", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text(
                            "Tap a preset to apply it to this page. Your current layout, colours and wallpaper are kept as Previous layout, so you can undo. A preset of yours can also carry your colours, and a wallpaper, which apply to the whole launcher. Tap the pencil to rename one. Share sends the layout and colours as a text code, never the wallpaper.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                        Column(
                            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BUILTIN_PRESETS.forEach { p -> PresetRow(p, null, null) { applyPreset(p) } }
                            board.userPresets.forEach { p -> PresetRow(p, { board.deletePreset(p.name) }, { presetDialog = false; renameFrom = p.name }) { applyPreset(p) } }
                        }
                        ButtonRow {
                            HoloButton("Save") { presetDialog = false; saveDialog = true }
                            HoloButton("Share") { shareLayout() }
                            HoloButton("Import") { presetDialog = false; importDialog = true }
                            HoloButton("Close") { presetDialog = false }
                        }
                    }
                }
            }
        }

        // Import a shared layout code
        if (importDialog) {
            var code by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { importDialog = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("IMPORT LAYOUT", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text(
                            "Paste a layout code someone shared with you. It is added to your presets and nothing changes until you apply it.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                        OutlinedTextField(
                            value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth(),
                            maxLines = 4, placeholder = { Text("CYBERSKIN1:...") }, colors = holoFieldColors(),
                        )
                        ButtonRow {
                            HoloButton("Paste") {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    code = clip.getItemAt(0).text?.toString() ?: code
                                }
                            }
                            HoloButton("Import") {
                                val imported = board.importCode(code)
                                if (imported == null) {
                                    Toast.makeText(ctx, "That does not look like a CyberSkinOS layout code.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(ctx, "Imported ${imported.name}. Find it in Presets.", Toast.LENGTH_LONG).show()
                                    importDialog = false
                                    presetDialog = true
                                }
                            }
                            HoloButton("Cancel") { importDialog = false }
                        }
                    }
                }
            }
        }

        // Rename one of your presets
        renameFrom?.let { old ->
            var text by remember(old) { mutableStateOf(old) }
            Dialog(onDismissRequest = { renameFrom = null; presetDialog = true }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("RENAME PRESET", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text("Currently called $old.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        OutlinedTextField(
                            value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, placeholder = { Text("New name") }, colors = holoFieldColors(),
                        )
                        ButtonRow {
                            HoloButton("Rename") {
                                val problem = board.renamePreset(old, text)
                                if (problem == null) {
                                    Toast.makeText(ctx, "Renamed to ${text.trim()}", Toast.LENGTH_SHORT).show()
                                    renameFrom = null
                                    presetDialog = true
                                } else {
                                    Toast.makeText(ctx, problem, Toast.LENGTH_LONG).show()
                                }
                            }
                            HoloButton("Cancel") { renameFrom = null; presetDialog = true }
                        }
                    }
                }
            }
        }

        // Save the current layout as a preset
        if (saveDialog) {
            var name by remember { mutableStateOf("") }
            var withColours by remember { mutableStateOf(true) }
            var withWall by remember { mutableStateOf(true) }
            Dialog(onDismissRequest = { saveDialog = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SAVE PRESET", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text("Name your layout. Using an existing name updates that preset.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, placeholder = { Text("Preset name") }, colors = holoFieldColors(),
                        )
                        SettingSwitch(
                            "Save colours",
                            "The palette and signal colour, including colours you mixed yourself.",
                            withColours,
                        ) { withColours = it }
                        SettingSwitch(
                            "Save wallpaper",
                            if (Holo.hasWallpaper) "The picture behind your widgets, and how dark it is." else "No wallpaper is set, so applying this preset will clear any wallpaper.",
                            withWall,
                        ) { withWall = it }
                        ButtonRow {
                            HoloButton("Save") {
                                val n = name.trim()
                                if (n.isNotEmpty()) {
                                    board.savePreset(n, withColours, withWall)
                                    Toast.makeText(ctx, "Saved preset $n", Toast.LENGTH_SHORT).show()
                                    saveDialog = false
                                }
                            }
                            HoloButton("Cancel") { saveDialog = false }
                        }
                    }
                }
            }
        }

        // Pages
        if (pagesDialog) {
            Dialog(onDismissRequest = { pagesDialog = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PAGES", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text(
                            "Swipe left or right on the home screen to change page, or tap the dots at the bottom. Each page has its own widgets. " +
                                "Pressing Home brings you back to page 1. Add, Arrange and Presets work on the page you are on.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                        Column(
                            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (i in 0 until board.pageCount) {
                                val n = board.widgetsOnPage(i)
                                ChoiceRow(
                                    "Page ${i + 1}" + (if (i == board.page) "  (you are here)" else ""),
                                    "$n ${if (n == 1) "widget" else "widgets"}",
                                ) { board.goToPage(i); pagesDialog = false }
                            }
                        }
                        Text(
                            "Page ${board.page + 1}: move it, or give it a wallpaper of its own. Pages without one show your main wallpaper.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                        ButtonRow {
                            if (board.page > 0) HoloButton("Earlier") { board.movePageBy(-1) }
                            if (board.page < board.pageCount - 1) HoloButton("Later") { board.movePageBy(1) }
                            HoloButton("Picture") { pickPageWall.launch(arrayOf("image/*")) }
                            if (Holo.hasPageWallpaper(board.page)) HoloButton("No picture") { Holo.removePageWallpaper(ctx, board.page) }
                        }
                        ButtonRow {
                            HoloButton("Add") {
                                if (board.addPage() < 0) {
                                    Toast.makeText(ctx, "That is the most pages you can have.", Toast.LENGTH_LONG).show()
                                } else {
                                    pagesDialog = false
                                }
                            }
                            if (board.pageCount > 1) HoloButton("Remove") { pagesDialog = false; confirmRemovePage = true }
                            HoloButton("Close") { pagesDialog = false }
                        }
                    }
                }
            }
        }

        // Pages: are you sure?
        if (confirmRemovePage) {
            val n = board.widgetsOnPage(board.page)
            Dialog(onDismissRequest = { confirmRemovePage = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("REMOVE PAGE", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Text(
                            "Remove page ${board.page + 1}" + (if (n > 0) " and its $n ${if (n == 1) "widget" else "widgets"}" else "") +
                                "? Notes, folders and links on it go too. This cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium, color = Holo.Text,
                        )
                        ButtonRow {
                            HoloButton("Remove") {
                                board.removeCurrentPage()
                                confirmRemovePage = false
                                Toast.makeText(ctx, "Page removed", Toast.LENGTH_SHORT).show()
                            }
                            HoloButton("Cancel") { confirmRemovePage = false }
                        }
                    }
                }
            }
        }

        // Confirmations for reset and restore
        when (confirm) {
            "layout" -> ConfirmDialog(
                "RESET LAYOUT",
                "Starts the widgets again from the Bridge layout on a single page. Notes, folders, links and widget settings go with the old widgets, and the home star and grid size return to their defaults. Your colours, presets and hidden apps are kept. This cannot be undone, so make a backup first if you want one.",
                "Reset",
                onConfirm = { confirm = null; Backup.resetLayout(ctx); Backup.refresh(ctx) },
                onCancel = { confirm = null },
            )
            "defaults" -> ConfirmDialog(
                "RESTORE DEFAULTS",
                "Goes back to the Stellarite palette, Teal signal, HUD font, normal motion speed, motion on and no wallpaper. Your own colours and uploaded font are cleared. Your layout, presets and hidden apps are kept.",
                "Restore",
                onConfirm = { confirm = null; Backup.restoreDefaults(ctx); Backup.refresh(ctx) },
                onCancel = { confirm = null },
            )
            "rescued" -> ConfirmDialog(
                "BRING BACK RESCUED COPY",
                "Replaces everything with the copy CyberSkinOS kept when it last had to start again from scratch. If something in that copy is what stopped CyberSkinOS opening, safe mode will step in again.",
                "Restore",
                onConfirm = {
                    confirm = null
                    ioScope.launch {
                        val ok = withContext(Dispatchers.IO) { Backup.restoreRescue(ctx) }
                        if (ok) {
                            Toast.makeText(ctx, "Rescued copy restored", Toast.LENGTH_SHORT).show()
                            Backup.refresh(ctx)
                        } else {
                            Toast.makeText(ctx, "The rescued copy could not be read. Nothing was changed.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onCancel = { confirm = null },
            )
            "restore" -> ConfirmDialog(
                "RESTORE BACKUP",
                "Replaces everything in CyberSkinOS with what is in the file: layout, pages, notes, folders, links, colours, presets, wallpaper, hidden apps and settings. This cannot be undone.",
                "Restore",
                onConfirm = {
                    val uri = pendingRestore
                    confirm = null
                    pendingRestore = null
                    if (uri != null) ioScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                ctx.contentResolver.openInputStream(uri)?.use { Backup.restore(ctx, it) } ?: false
                            } catch (e: Exception) { false }
                        }
                        if (ok) {
                            Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show()
                            Backup.refresh(ctx)
                        } else {
                            Toast.makeText(ctx, "That is not a usable CyberSkinOS backup. Nothing was changed.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onCancel = { confirm = null; pendingRestore = null },
            )
        }

        // Monthly backup reminder
        if (remindDialog) {
            BackupReminderDialog(
                onBackup = { remindDialog = false; makeBackup.launch(backupName()) },
                onLater = { remindDialog = false; Backup.snooze(ctx) },
                onStop = { remindDialog = false; remindOn = false; Backup.setRemind(ctx, false) },
            )
        }

        // Shown once after safe mode stepped in
        if (safeNotice != 0) {
            NoticeDialog(
                "SAFE MODE",
                if (safeNotice >= 2)
                    "CyberSkinOS failed to start again, so it went back to the default look as well as the Bridge layout. A copy of how things were is kept: Settings > Backup and reset > Rescued brings it back."
                else
                    "CyberSkinOS failed to start several times, so it began again from the Bridge layout. A copy of how things were is kept: Settings > Backup and reset > Rescued brings it back, or use Restore with one of your own backups.",
            ) {
                SafeMode.clearNotice(ctx)
                safeNotice = 0
                hasRescue = Backup.hasRescue(ctx)
            }
        }

        // Settings for one widget (the ⚙ in Arrange mode)
        widgetDialog?.let { wid ->
            val w = board.items.orEmpty().firstOrNull { it.id == wid }
            if (w != null) WidgetSettingsDialog(w, board) { widgetDialog = null }
        }

        if (creditsDialog) CreditsDialog { creditsDialog = false }
        if (helpDialog) HelpDialog { helpDialog = false }

        // Widgets that other apps provide
        if (appWidgetChooser) {
            AppWidgetChooser(
                onPick = { info ->
                    appWidgetChooser = false
                    val activity = AppWidgets.activityOf(ctx)
                    if (activity == null) {
                        Toast.makeText(ctx, "Could not start. Try again.", Toast.LENGTH_LONG).show()
                    } else {
                        AppWidgets.begin(activity, info) { awId ->
                            if (awId < 0) {
                                Toast.makeText(ctx, "That widget was not added.", Toast.LENGTH_LONG).show()
                            } else {
                                val id = board.add(WType.APPWIDGET)
                                if (id < 0L) {
                                    AppWidgets.release(ctx, awId)
                                    Toast.makeText(ctx, "No room left on the grid. Remove or shrink a widget first.", Toast.LENGTH_LONG).show()
                                } else {
                                    prefs.edit().putInt("aw_$id", awId).apply()
                                    editing = true
                                }
                            }
                        }
                    }
                },
                onClose = { appWidgetChooser = false },
            )
        }

        // Widget picker
        if (picker) {
            Dialog(onDismissRequest = { picker = false }) {
                HoloPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ADD A WIDGET", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                        Column(
                            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WType.entries.forEach { t ->
                                PickerRow(t) {
                                    if (t == WType.APPWIDGET) {
                                        picker = false
                                        appWidgetChooser = true
                                        return@PickerRow
                                    }
                                    val id = board.add(t)
                                    picker = false
                                    if (id < 0L) {
                                        Toast.makeText(ctx, "No room left on the grid. Remove or shrink a widget first.", Toast.LENGTH_LONG).show()
                                    } else {
                                        editing = true
                                    }
                                }
                            }
                        }
                        ButtonRow { HoloButton("Cancel") { picker = false } }
                    }
                }
            }
        }

    }
}

/** One titled panel on the Settings screen. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    HoloPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, note: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Holo.Text)
            Text(note, style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Holo.signal.lit, checkedTrackColor = Holo.Dim,
                uncheckedThumbColor = Holo.Muted, uncheckedTrackColor = Holo.Void,
            ),
        )
    }
}

/** Everything that is not on the Console, in five sections: Look, Layout, Apps, Backup and reset, and System. */
@Composable
private fun SettingsScreen(
    board: WidgetBoard,
    animations: Boolean,
    onAnimations: (Boolean) -> Unit,
    onPickFont: () -> Unit,
    onPickWall: () -> Unit,
    onPresets: () -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit,
    onHidden: () -> Unit,
    onSchedule: () -> Unit,
    recentOn: Boolean,
    onRecentOn: (Boolean) -> Unit,
    onClearRecent: () -> Unit,
    lastBackup: Long,
    remindOn: Boolean,
    onRemind: (Boolean) -> Unit,
    hasRescue: Boolean,
    onRescued: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onResetLayout: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onCredits: () -> Unit,
    onHelp: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        SpaceBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SETTINGS", style = MaterialTheme.typography.headlineMedium, color = Holo.Bright, modifier = Modifier.weight(1f))
                HoloButton("Close", diameter = 68.dp, onClick = onClose)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Holo.signal.glow, Color.Transparent)))
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---------------- Look ----------------
                SettingsSection("Look") {
                    Text("Palette", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "The light that draws every frame, button and dial, and the glass of the panels. Stellarite is the company colours.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    Palettes.chunked(4).forEach { rowOfFour ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowOfFour.forEach { p -> ThemeSwatch(p, p === Holo.palette) { Holo.choose(ctx, p) } }
                        }
                    }
                    Text("Signal colour", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Colours the clock digits, needles, panel frames and the Audio deck.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        Signals.forEach { c -> SignalSwatch(c, Holo.signal === c) { Holo.chooseSignal(ctx, c) } }
                    }
                    Text("Your own colours", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Slide to mix your own. Frame light and panel glass make a Custom palette, and signal makes a Custom signal colour. Presets can save them.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    var lineC by remember(Holo.palette.name) { mutableStateOf(Holo.palette.line) }
                    var deckC by remember(Holo.palette.name) { mutableStateOf(Holo.palette.deck) }
                    var signalC by remember(Holo.signal.name) { mutableStateOf(Holo.signal.glow) }
                    ColourEditor(
                        "Frame light", Holo.palette.name, lineC, 0.2f, 1f, 0.45f, 1f,
                        onChange = { lineC = it; Holo.applyCustomPalette(it, deckC) }, onDone = { Holo.saveCustom(ctx) },
                    )
                    ColourEditor(
                        "Panel glass", Holo.palette.name, deckC, 0f, 0.8f, 0.05f, 0.4f,
                        onChange = { deckC = it; Holo.applyCustomPalette(lineC, it) }, onDone = { Holo.saveCustom(ctx) },
                    )
                    ColourEditor(
                        "Signal", Holo.signal.name, signalC, 0.3f, 1f, 0.6f, 1f,
                        onChange = { signalC = it; Holo.applyCustomSignal(it) }, onDone = { Holo.saveCustom(ctx) },
                    )
                    Text("Motion speed", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedChip("Slow", Holo.spin == 1.8f, Modifier.weight(1f)) { Holo.setSpin(ctx, 1.8f) }
                        SpeedChip("Normal", Holo.spin == 1f, Modifier.weight(1f)) { Holo.setSpin(ctx, 1f) }
                        SpeedChip("Fast", Holo.spin == 0.5f, Modifier.weight(1f)) { Holo.setSpin(ctx, 0.5f) }
                    }
                    SettingSwitch("Motion", "Turning rings, the panel shimmer and the scan line. Turn off to save battery", animations, onAnimations)
                    SettingSwitch(
                        "Sound effects", "Soft glassy pings and sweeps as you use the launcher. They follow your phone's volume, so silent mode mutes them.",
                        Sfx.enabled,
                    ) { Sfx.setEnabled(ctx, it) }
                    if (Sfx.enabled) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeedChip("Quiet", Sfx.volumePct == 30, Modifier.weight(1f)) { Sfx.setVolume(ctx, 30) }
                            SpeedChip("Normal", Sfx.volumePct == 65, Modifier.weight(1f)) { Sfx.setVolume(ctx, 65) }
                            SpeedChip("Loud", Sfx.volumePct == 100, Modifier.weight(1f)) { Sfx.setVolume(ctx, 100) }
                        }
                    }
                    SettingSwitch(
                        "Pulse rings", "Rings of light spread out from the star when you open the Console or start arranging. Needs Motion on.",
                        Sfx.pulses,
                    ) { Sfx.setPulses(ctx, it) }
                    SettingSwitch(
                        "Holo icons", "Draws app icons in the light of your palette, so they match.",
                        Holo.tintIcons,
                    ) { Holo.setTintIcons(ctx, it) }
                    Text("Scheduled looks", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Switch your colours by the clock or while charging, for example Night Watch every evening. Your own look comes back afterwards. Layouts never change.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow { HoloButton("Schedule") { onSchedule() } }
                    Text("Font", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text("STELLARITE STATION · 0123456789", fontSize = 16.sp, fontFamily = Holo.font, letterSpacing = 1.sp, color = Holo.signal.lit)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedChip("HUD", Holo.fontKey == "hud", Modifier.weight(1f)) { Holo.chooseFont(ctx, "hud") }
                        SpeedChip("Mono", Holo.fontKey == "mono", Modifier.weight(1f)) { Holo.chooseFont(ctx, "mono") }
                        SpeedChip("Serif", Holo.fontKey == "serif", Modifier.weight(1f)) { Holo.chooseFont(ctx, "serif") }
                        SpeedChip(if (Holo.hasCustomFont) "Yours" else "Custom", Holo.fontKey == "custom", Modifier.weight(1f)) {
                            if (Holo.hasCustomFont) Holo.chooseFont(ctx, "custom") else onPickFont()
                        }
                    }
                    Text(
                        "Upload adds a .ttf or .otf of your own, such as Orbitron, Exo 2 or Rajdhani for a ship's-console feel. Find opens Google Fonts.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Upload") { onPickFont() }
                        HoloButton("Find") { open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://fonts.google.com/?query=orbitron"))) }
                        if (Holo.hasCustomFont) HoloButton("Remove") { Holo.removeCustomFont(ctx) }
                    }
                    Text("Wallpaper", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        if (Holo.hasWallpaper) "Darken the picture so your widgets stay easy to read."
                        else "Pick a picture to show instead of the star field, behind your widgets and the Databank.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    if (Holo.hasWallpaper) {
                        Slider(
                            value = Holo.wallDim,
                            onValueChange = { Holo.setDim(ctx, it) },
                            valueRange = 0f..0.85f,
                            colors = SliderDefaults.colors(
                                thumbColor = Holo.signal.lit, activeTrackColor = Holo.Line, inactiveTrackColor = Holo.Void,
                            ),
                        )
                    }
                    ButtonRow {
                        HoloButton("Picture") { onPickWall() }
                        if (Holo.hasWallpaper) HoloButton("Clear") { Holo.removeWallpaper(ctx) }
                    }
                }

                // ---------------- Layout ----------------
                SettingsSection("Layout") {
                    SettingSwitch(
                        "Fixed grid", "Widgets snap into place and cannot overlap. Off lets them float anywhere.",
                        board.gridMode,
                    ) { board.useGrid(it) }
                    if (board.gridMode) {
                        Text("Grid size", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeedChip("Auto", board.gridKind == "auto", Modifier.weight(1f)) { board.chooseGrid("auto") }
                            SpeedChip("5 × 10", board.gridKind == "5x10", Modifier.weight(1f)) { board.chooseGrid("5x10") }
                            SpeedChip("4 × 9", board.gridKind == "4x9", Modifier.weight(1f)) { board.chooseGrid("4x9") }
                        }
                        Text(
                            "Auto fits as many cells as the screen allows. 5 × 10 and 4 × 9 are columns by rows with the phone upright, and turn into 10 × 5 and 9 × 4 when you turn it on its side. Widgets are re-snapped when you change.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                    }
                    Text(
                        "Presets saves and applies layouts, and lets you rename or delete your own. Share sends your current layout as a text code. Import adds a code you received.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Presets") { onPresets() }
                        HoloButton("Share") { onShare() }
                        HoloButton("Import") { onImport() }
                    }
                }

                // ---------------- Apps ----------------
                SettingsSection("Apps") {
                    Text(
                        "Hidden lists the apps you hid from the Databank. Open one from there, or long-press it and choose Show.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow { HoloButton("Hidden") { onHidden() } }
                    SettingSwitch(
                        "Recent apps row", "Shows the apps you opened lately at the top of the Databank.",
                        recentOn, onRecentOn,
                    )
                    if (recentOn) {
                        ButtonRow { HoloButton("Clear") { onClearRecent() } }
                    }
                }

                // ---------------- Backup and reset ----------------
                SettingsSection("Backup and reset") {
                    Text(
                        "Backup saves everything to one file that you choose where to keep: layout, pages, notes, folders, links, colours, font, wallpaper, presets, hidden apps and widget settings. Restore reads one back in and replaces what is here.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    Text(
                        if (lastBackup == 0L) "You have not made a backup yet."
                        else "Last backup: " + java.text.DateFormat.getDateInstance().format(java.util.Date(lastBackup)),
                        style = MaterialTheme.typography.labelLarge, color = Holo.Text,
                    )
                    ButtonRow {
                        HoloButton("Backup") { onBackup() }
                        HoloButton("Restore") { onRestore() }
                    }
                    SettingSwitch(
                        "Backup reminder", "Suggests a backup once a month, when it has been that long since your last one.",
                        remindOn, onRemind,
                    )
                    if (hasRescue) {
                        Text(
                            "CyberSkinOS once failed to start and began again from the Bridge layout. It kept a copy of how things were. Rescued brings that copy back.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                        ButtonRow { HoloButton("Rescued") { onRescued() } }
                    }
                    Text(
                        "Layout starts the widgets again from the Bridge layout on one page, and puts the home star and grid size back. Defaults returns the colours, font, wallpaper and motion to how CyberSkinOS came. Both ask first.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Layout") { onResetLayout() }
                        HoloButton("Defaults") { onRestoreDefaults() }
                    }
                }

                // ---------------- System ----------------
                SettingsSection("System") {
                    Text(
                        "Home sets your default home app. Device opens Android settings. Access opens CyberSkinOS's permissions.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Home") { open(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
                        HoloButton("Device") { open(ctx, Intent(Settings.ACTION_SETTINGS)) }
                        HoloButton("Access") {
                            open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                        }
                    }
                    Text(
                        "Help is a short guide to every gesture. Credits lists the licences and sources behind CyberSkinOS.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Help") { onHelp() }
                        HoloButton("Credits") { onCredits() }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StellariteMark(Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "CyberSkinOS 1.0 · Stellarite Corporation",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HiddenRow(app: AppInfo, onOpen: () -> Unit, onShow: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Holo.Void.copy(alpha = 0.6f))
            .border(1.dp, Holo.Dim, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPort(app.icon, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = Holo.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Show", Modifier.clickable(onClick = onShow).padding(14.dp),
            color = Holo.Ok, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ThemeSwatch(p: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(p.bright, p.line, p.deck)))
                .border(if (selected) 3.dp else 1.dp, if (selected) Holo.Text else p.dim, CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            p.name, fontSize = 12.sp, fontFamily = Holo.font,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Holo.Bright else Holo.Muted,
        )
    }
}

@Composable
internal fun SpeedChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) Holo.signal.glow.copy(alpha = 0.85f) else Holo.Void.copy(alpha = 0.6f))
            .border(1.dp, if (selected) Holo.signal.lit else Holo.Dim, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) Holo.OnAccent else Holo.Text, maxLines = 1)
    }
}

@Composable
private fun PresetRow(p: Preset, onDelete: (() -> Unit)?, onRename: (() -> Unit)?, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Holo.Void.copy(alpha = 0.6f))
            .border(1.dp, Holo.Dim, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onClick).padding(12.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Holo.Bright)
            Text(
                "${p.items.size} widgets · " + (if (p.builtin) "Built in" else "Yours") + lookLabel(p.look).let { if (it.isEmpty()) "" else " · $it" },
                style = MaterialTheme.typography.bodyMedium, color = Holo.Text,
            )
        }
        if (onRename != null) {
            Text("✎", Modifier.clickable(onClick = onRename).padding(horizontal = 10.dp, vertical = 14.dp), color = Holo.Bright, fontSize = 20.sp)
        }
        if (onDelete != null) {
            Text("✕", Modifier.clickable(onClick = onDelete).padding(horizontal = 12.dp, vertical = 14.dp), color = Holo.Alert, fontSize = 20.sp)
        }
    }
}

@Composable
private fun PickerRow(t: WType, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Holo.Void.copy(alpha = 0.6f))
            .border(1.dp, Holo.Dim, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(t.label(), style = MaterialTheme.typography.titleMedium, color = Holo.Bright)
        Text(t.blurb, style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
        if (t.tip != null) Text("◆ " + t.tip.removePrefix("Recommended: "), style = MaterialTheme.typography.labelLarge, color = Holo.Ok, fontSize = 12.sp)
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
private fun HomeScreen(
    board: WidgetBoard,
    apps: List<AppInfo>,
    pinned: List<String>,
    counts: Map<String, Int>,
    hidden: Set<String>,
    tick: Int,
    prefs: SharedPreferences,
    editing: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenLinks: (Long) -> Unit,
    onFabTap: () -> Unit,
    onFabDoubleTap: () -> Unit,
    onWidgetSettings: (Long) -> Unit,
    overlayOpen: Boolean,
    onEnterEdit: () -> Unit,
    puff: Int,
    onAdd: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    // Where the home star has been dragged to (dp from its default bottom-right spot), per orientation
    val fabKey = if (land) "_land" else ""
    var fabDx by remember(land) { mutableFloatStateOf(prefs.getFloat("fab_dx$fabKey", 0f)) }
    var fabDy by remember(land) { mutableFloatStateOf(prefs.getFloat("fab_dy$fabKey", 0f)) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableFloatStateOf(batteryLevel(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            battery = batteryLevel(ctx)
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L) // wake on the next minute
        }
    }
    val storage = remember(tick) { storageUse() }
    // A new Animatable per page, so the new page starts see-through in the very frame it appears and then fades in
    val fade = remember(board.page) { Animatable(0f) }
    LaunchedEffect(board.page) { fade.animateTo(1f, tween(200)) }

    Box(
        Modifier.fillMaxSize().pointerInput(editing, overlayOpen, board.pageCount) {
            // swipe left for the next page, right for the previous one
            if (editing || overlayOpen || board.pageCount < 2) return@pointerInput
            val threshold = 56.dp.toPx()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total < -threshold) board.goToPage(board.page + 1)
                    else if (total > threshold) board.goToPage(board.page - 1)
                },
                onDragCancel = { total = 0f },
                onHorizontalDrag = { _, d -> total += d },
            )
        },
    ) {
        SpaceBackdrop(wall = Holo.wallpaperFor(board.page))
        // Press and hold on empty space to rearrange widgets
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { dropFocus(ctx, view, focus) }, onLongPress = { onEnterEdit() }) })

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            LaunchedEffect(maxWidth, maxHeight, land) {
                board.areaW = maxWidth.value
                board.areaH = maxHeight.value
                board.ensureDefaults(land)
            }
            if (editing && board.gridMode) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = board.stepX.dp.toPx()
                    val sy = board.stepY.dp.toPx()
                    val cw = board.cellW.dp.toPx()
                    val ch = board.cellH.dp.toPx()
                    for (r in 0 until board.maxRows) for (c in 0 until board.columns) {
                        drawRoundRect(
                            Holo.Bright.copy(alpha = 0.18f),
                            topLeft = Offset(c * sx, r * sy), size = Size(cw, ch),
                            cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
                board.items.orEmpty().forEach { w ->
                    key(w.id) {
                        WidgetHost(w, board, editing, { onWidgetSettings(w.id) }) {
                            WidgetBody(w, board, now, is24, battery, storage, apps, pinned, counts, hidden, prefs, editing, onLaunch, onLongPress, onOpenFolder, onOpenLinks)
                        }
                    }
                }
            }
        }

        // Page dots
        if (board.pageCount > 1 && !editing) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until board.pageCount) {
                    val here = i == board.page
                    Box(Modifier.clickable { board.goToPage(i) }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Box(
                            Modifier.size(if (here) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (here) Holo.signal.lit else Holo.Line.copy(alpha = 0.45f)),
                        )
                    }
                }
            }
        }

        if (editing) {
            HoloPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Drag a widget to move it. Pull its corner to resize.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted, modifier = Modifier.weight(1f),
                    )
                    HoloButton("Add", onClick = onAdd)
                    HoloButton("Done", onClick = onDone)
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                val maxDx = maxOf(0f, (maxWidth - 72.dp).value)
                val maxDy = maxOf(0f, (maxHeight - 72.dp).value)
                PulseRings(
                    puff,
                    Modifier.align(Alignment.BottomEnd).offset(fabDx.coerceIn(-maxDx, 0f).dp, fabDy.coerceIn(-maxDy, 0f).dp),
                )
                StarFab(
                    onTap = onFabTap,
                    onDoubleTap = onFabDoubleTap,
                    onMove = { dx, dy ->
                        fabDx = (fabDx.coerceIn(-maxDx, 0f) + dx).coerceIn(-maxDx, 0f)
                        fabDy = (fabDy.coerceIn(-maxDy, 0f) + dy).coerceIn(-maxDy, 0f)
                    },
                    onMoveEnd = {
                        prefs.edit().putFloat("fab_dx$fabKey", fabDx).putFloat("fab_dy$fabKey", fabDy).apply()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .offset(fabDx.coerceIn(-maxDx, 0f).dp, fabDy.coerceIn(-maxDy, 0f).dp),
                )
            }
        }
    }
}

/** Positions one widget and, in edit mode, adds the move / resize / remove controls. */
@Composable
private fun WidgetHost(w: WidgetItem, board: WidgetBoard, editing: Boolean, onSettings: () -> Unit, content: @Composable () -> Unit) {
    val hostCtx = LocalContext.current
    val wd = if (board.areaW > 0f) w.w.coerceAtMost(board.areaW) else w.w
    val ht = if (board.areaH > 0f) w.h.coerceAtMost(board.areaH) else w.h
    val x = w.x.coerceIn(0f, maxOf(0f, board.areaW - wd))
    val y = w.y.coerceIn(0f, maxOf(0f, board.areaH - ht))

    Box(
        Modifier
            .offset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
            .size(wd.dp, ht.dp)
    ) {
        content()
        if (editing) {
            val shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
            Box(
                Modifier.fillMaxSize().clip(shape)
                    .background(Color(0x40000000))
                    .border(2.dp, Holo.signal.lit, shape)
                    .pointerInput(w.id) {
                        detectDragGestures(
                            onDragStart = { board.begin(w.id) },
                            onDragEnd = {
                                when (board.sendToEdge(w.id)) {
                                    1 -> Toast.makeText(hostCtx, "Sent to page ${board.page + 1}", Toast.LENGTH_SHORT).show()
                                    2 -> {
                                        Toast.makeText(hostCtx, "No room on that page.", Toast.LENGTH_SHORT).show()
                                        board.settle(w.id, false)
                                    }
                                    else -> board.settle(w.id, false)
                                }
                            },
                            onDragCancel = { board.settle(w.id, false) },
                        ) { change, drag ->
                            change.consume()
                            board.move(w.id, drag.x.toDp().value, drag.y.toDp().value)
                        }
                    }
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                    .background(Holo.Alert).clickable { board.remove(w.id) },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color.White, fontSize = 14.sp) }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 42.dp).size(30.dp).clip(CircleShape)
                    .background(Holo.signal.glow).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Text("⚙", color = Holo.OnAccent, fontSize = 15.sp) }
            Box(
                Modifier.align(Alignment.BottomEnd).size(44.dp)
                    .pointerInput(w.id) {
                        detectDragGestures(
                            onDragStart = { board.begin(w.id) },
                            onDragEnd = { board.settle(w.id, true) },
                            onDragCancel = { board.settle(w.id, true) },
                        ) { change, drag ->
                            change.consume()
                            board.resize(w.id, drag.x.toDp().value, drag.y.toDp().value)
                        }
                    },
                contentAlignment = Alignment.BottomEnd,
            ) { Text("◢", color = Holo.signal.lit, fontSize = 26.sp, modifier = Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun WidgetBody(
    w: WidgetItem,
    board: WidgetBoard,
    now: LocalDateTime,
    is24: Boolean,
    battery: Float,
    storage: Pair<Float, Float>,
    apps: List<AppInfo>,
    pinned: List<String>,
    counts: Map<String, Int>,
    hidden: Set<String>,
    prefs: SharedPreferences,
    editing: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenLinks: (Long) -> Unit,
) {
    val ws = board.settingsOf(w.id)
    val is24w = when (ws.clock) { 12 -> false; 24 -> true; else -> is24 }
    HoloPanel(
        Modifier.fillMaxSize(),
        padding = when (w.type) { WType.NIXIE -> 8.dp; WType.VIALS, WType.MUSIC -> 14.dp; else -> 16.dp },
        fillAlpha = ws.opacity,
    ) {
        when (w.type) {
            WType.CLOCK -> HoloClockContent(now, is24w)
            WType.NIXIE -> ReadoutContent(now, is24w)
            WType.ANALOG -> ClockContent(now, is24w)
            WType.DIGITAL -> DigitalContent(now, is24w)
            WType.BOILER -> GaugeContent(battery, true, ws.title.ifBlank { w.type.label() }, "${(battery * 100).toInt()}% charged")
            WType.CARGO -> GaugeContent(storage.first, false, ws.title.ifBlank { w.type.label() }, "${(storage.first * 100).toInt()}% full")
            WType.VIALS -> VialsContent(ws.vials, ws.fahrenheit, ws.vialColors)
            WType.MUSIC -> MusicContent(ws.player)
            WType.ENGINE -> EngineRoomContent(ws.fahrenheit)
            WType.COMPASS -> CompassContent()
            WType.BAROMETER -> BarometerContent(ws.title.ifBlank { w.type.label() })
            WType.MOON -> MoonContent(now)
            WType.SUN -> SunContent(now, is24w, prefs)
            WType.WEATHER -> WeatherContent(prefs, ws.fahrenheit, ws.title.ifBlank { w.type.label() })
            WType.SOLAR -> SolarContent(now)
            WType.CHRONO -> ChronoContent(w.id, prefs)
            WType.TIMER -> TimerContent(w.id, prefs, is24w, ws.tone)
            WType.ALMANAC -> AlmanacContent(now)
            WType.AGENDA -> AgendaContent(is24w, ws.title.ifBlank { w.type.label() })
            WType.LANTERN -> LanternContent(ws.lanternMin, ws.lanternLevel)
            WType.WORLD -> WorldClockContent(now, is24w, ws.zones, ws.title.ifBlank { w.type.label() })
            WType.CONTACTS -> SpeedDialContent(ws.dial, ws.title.ifBlank { w.type.label() })
            WType.LOGBOOK -> LogbookContent(ws.title.ifBlank { w.type.label() })
            WType.APPWIDGET -> AppWidgetContent(w.id, prefs)
            WType.MEMO -> MemoContent(w.id, prefs, editing, ws.title.ifBlank { w.type.label() })
            WType.SHELF -> AppRowContent(
                ws.title.ifBlank { w.type.label() },
                pinned.mapNotNull { p -> apps.firstOrNull { it.packageName == p } },
                "Long-press an app in the Databank to add it here.",
                onLaunch, onLongPress,
            )
            WType.FOLDER -> FolderContent(
                board.folderNames[w.id] ?: "Folder",
                board.folderApps[w.id].orEmpty().mapNotNull { p -> apps.firstOrNull { it.packageName == p } },
            ) { onOpenFolder(w.id) }
            WType.LINKS -> LinksContent(
                board.linkNames[w.id] ?: "Links",
                board.linkItems[w.id].orEmpty(),
            ) { onOpenLinks(w.id) }
            WType.PICKS -> AppRowContent(
                ws.title.ifBlank { w.type.label() },
                apps.filter { it.packageName !in hidden && (counts[it.packageName] ?: 0) > 0 }.sortedByDescending { counts[it.packageName] ?: 0 }.take(8),
                "Open a few apps and the station will learn which you favour.",
                onLaunch, onLongPress, iconSize = 60.dp,
            )
        }
    }
}

// ───────────────────────── Widgets: content ─────────────────────────

private fun clockText(now: LocalDateTime, is24: Boolean): Pair<String, String?> =
    now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")) to
        (if (is24) null else now.format(DateTimeFormatter.ofPattern("a")))

@Composable
private fun TimeLine(time: String, ampm: String?, fontSize: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(time, fontSize = fontSize.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1)
        if (ampm != null) Text(
            ampm, style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
            modifier = Modifier.padding(start = 6.dp, bottom = (fontSize * 0.14f).dp),
        )
    }
}

@Composable
private fun ClockContent(now: LocalDateTime, is24: Boolean) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val (time, ampm) = clockText(now, is24)
    val hour = now.hour % 12 + now.minute / 60f
    val minute = now.minute + now.second / 60f
    val mw = maxWidth
    val mh = maxHeight
    if (mw > mh * 1.3f) {
        val dial = minOf(mh, mw * 0.5f)
        val textW = mw - dial - 12.dp
        val fs = minOf(textW.value * 0.27f, mh.value * 0.45f).coerceIn(18f, 48f)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AnalogClock(hour, minute, Modifier.size(dial))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TimeLine(time, ampm, fs)
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                    style = MaterialTheme.typography.titleMedium, color = Holo.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AnalogClock(hour, minute, Modifier.size(minOf(mw, mh * 0.68f)))
            Spacer(Modifier.height(4.dp))
            TimeLine(time, ampm, (mh.value * 0.14f).coerceIn(14f, 28f))
        }
    }
}

/** The digits on their own: no date line, so they fill a short widget. */
@Composable
private fun ReadoutContent(now: LocalDateTime, is24: Boolean) {
    val hour = if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val hh = if (is24) hour.toString().padStart(2, '0') else hour.toString().padStart(2, ' ')
    HoloClock(
        hh + now.format(DateTimeFormatter.ofPattern("mm")), null, Modifier.fillMaxSize(),
        badge = if (is24) null else (if (now.hour < 12) "AM" else "PM"),
    )
}

@Composable
private fun SignalSwatch(c: Signal, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(c.lit, c.glow, Holo.Void)))
                .border(if (selected) 3.dp else 1.dp, if (selected) Holo.Text else Holo.Dim, CircleShape),
        )
        Text(
            c.name, fontSize = 11.sp, fontFamily = Holo.font,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Holo.Bright else Holo.Muted, maxLines = 1,
        )
    }
}

@Composable
private fun HoloClockContent(now: LocalDateTime, is24: Boolean) {
    val hour = if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val hh = if (is24) hour.toString().padStart(2, '0') else hour.toString().padStart(2, ' ')
    val mm = now.format(DateTimeFormatter.ofPattern("mm"))
    val prefix = if (is24) "" else (if (now.hour < 12) "AM" else "PM") + "  ·  "
    HoloClock(hh + mm, prefix + now.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), Modifier.fillMaxSize())
}

@Composable
private fun DigitalContent(now: LocalDateTime, is24: Boolean) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val (time, ampm) = clockText(now, is24)
    val fs = minOf(maxWidth.value * 0.2f, maxHeight.value * 0.55f).coerceIn(20f, 72f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TimeLine(time, ampm, fs)
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
            style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp, color = Holo.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GaugeContent(value: Float, dangerLow: Boolean, title: String, caption: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val mw = maxWidth
    val mh = maxHeight
    if (mw > mh * 1.35f) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ArcGauge(value, Modifier.size(minOf(mh, mw * 0.45f)), dangerLow)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Holo.signal.lit, maxLines = 1)
                Text(caption, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ArcGauge(value, Modifier.size(minOf(mw, mh * 0.62f)), dangerLow)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1)
            Text(caption, fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
        }
    }
}

/** "1.2M", "340K" or "0": a compact bytes-per-second figure. */
private fun shortRate(bps: Long): String = when {
    bps >= 1_000_000L -> "%.1fM".format(bps / 1_000_000f)
    bps >= 1_000L -> "${bps / 1_000L}K"
    else -> "0"
}

/** Three gauges: memory in use, battery heat and network flow. Samples every two seconds while visible. */
@Composable
private fun EngineRoomContent(fahrenheit: Boolean = false) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val mw = maxWidth
    val mh = maxHeight
    var mem by remember { mutableFloatStateOf(0f) }
    var heat by remember { mutableFloatStateOf(-1f) } // degrees C, -1 = unknown
    var rx by remember { mutableLongStateOf(-1L) }    // bytes per second, -1 = unknown
    var tx by remember { mutableLongStateOf(-1L) }

    val active = LocalActive.current
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastT = SystemClock.elapsedRealtime()
        var first = true
        while (true) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mem = if (mi.totalMem > 0L) 1f - mi.availMem.toFloat() / mi.totalMem.toFloat() else 0f

            val t = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            heat = if (t == null || t == Int.MIN_VALUE) -1f else t / 10f

            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val nowT = SystemClock.elapsedRealtime()
            val dt = (nowT - lastT) / 1000f
            if (nowRx == TrafficStats.UNSUPPORTED.toLong() || nowTx == TrafficStats.UNSUPPORTED.toLong()) {
                rx = -1L; tx = -1L
            } else if (first || dt <= 0f) {
                rx = 0L; tx = 0L
            } else {
                rx = ((nowRx - lastRx) / dt).toLong().coerceAtLeast(0L)
                tx = ((nowTx - lastTx) / dt).toLong().coerceAtLeast(0L)
            }
            first = false
            lastRx = nowRx; lastTx = nowTx; lastT = nowT
            delay(2000)
        }
    }

    val dial = maxOf(minOf((mw - 16.dp) / 3, mh - 34.dp), 30.dp)
    // network flow on a log scale: 0 to 100 MB/s
    val flow = if (rx < 0L) 0f else (ln(1f + rx / 1000f) / ln(1f + 100_000f)).coerceIn(0f, 1f)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EngineDial(Modifier.weight(1f), dial, mem, "Memory", "${(mem * 100).roundToInt()}% used")
        EngineDial(
            Modifier.weight(1f), dial, ((heat - 20f) / 40f).coerceIn(0f, 1f), "Heat",
            if (heat < 0f) "n/a" else if (fahrenheit) "${(heat * 9f / 5f + 32f).roundToInt()}°F" else "${heat.roundToInt()}°C",
        )
        EngineDial(
            Modifier.weight(1f), dial, flow, "Uplink",
            if (rx < 0L) "n/a" else "↓${shortRate(rx)} ↑${shortRate(tx)}",
        )
    }
}

@Composable
private fun EngineDial(modifier: Modifier, dial: Dp, value: Float, title: String, caption: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ArcGauge(value, Modifier.size(dial))
        Text(title, fontSize = 12.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1)
        Text(caption, fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
    }
}

@Composable
private fun AlmanacContent(now: LocalDateTime) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ym = YearMonth.of(now.year, now.month)
    val offset = ym.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val days = ym.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val cellH = (maxHeight - 22.dp) / (rows + 1)
    val dot = minOf(cellH, maxWidth / 7) * 0.9f
    val fs = minOf(cellH.value * 0.6f, (maxWidth.value / 7f) * 0.55f).coerceIn(8f, 18f)
    Column(Modifier.fillMaxSize()) {
        Text(
            (now.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()) + " " + now.year).uppercase(),
            fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            color = Holo.signal.lit,
            modifier = Modifier.height(22.dp), maxLines = 1,
        )
        Row(Modifier.fillMaxWidth()) {
            "SMTWTFS".forEach { c ->
                Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.Center) {
                    Text(c.toString(), fontSize = fs.sp, fontFamily = Holo.font, color = Holo.Muted)
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val d = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.Center) {
                        if (d in 1..days) {
                            val today = d == now.dayOfMonth
                            // today gets a glowing ring of light
                            Box(
                                if (!today) Modifier
                                else Modifier.size(dot).clip(CircleShape)
                                    .background(Holo.signal.glow.copy(alpha = 0.25f))
                                    .border(2.dp, Holo.signal.lit, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$d", fontSize = fs.sp, fontFamily = Holo.font,
                                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (today) Holo.signal.lit else Holo.Text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AppRowContent(
    title: String,
    list: List<AppInfo>,
    empty: String,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    iconSize: Dp = 48.dp,
) {
    Column(Modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Holo.signal.lit, fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // icons stretch or shrink so each row fills the widget's width exactly
                val gap = 8.dp
                val pref = (maxWidth.value * 0.3f).coerceIn(38f, iconSize.value + 8f).dp
                val perRow = maxOf(1, ((maxWidth + gap) / (pref + gap)).roundToInt())
                val sz = maxOf((maxWidth - gap * (perRow - 1)) / perRow - 0.5.dp, 30.dp)
                FlowRow(
                    Modifier.fillMaxSize().clipToBounds(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    maxItemsInEachRow = perRow,
                ) {
                    list.forEach { app ->
                        IconPort(
                            app.icon, size = sz,
                            modifier = Modifier.combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── Folders ─────────────────────────

/** Folded view: a small 2x2 preview of the first apps plus the folder's name. Tap to open. */
@Composable
private fun FolderContent(name: String, list: List<AppInfo>, onOpen: () -> Unit) =
    BoxWithConstraints(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
        val side = maxOf(minOf(maxWidth, maxHeight - 22.dp), 40.dp)
        val cell = (side - 18.dp) / 2
        val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(side).clip(shape)
                    .background(Holo.Void.copy(alpha = 0.6f))
                    .border(1.dp, Holo.signal.glow, shape)
                    .padding(6.dp),
            ) {
                if (list.isEmpty()) {
                    Text("Empty", style = MaterialTheme.typography.labelLarge, color = Holo.Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.take(4).chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { IconPort(it.icon, size = cell) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                name, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
                color = Holo.signal.lit, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

@Composable
private fun FolderDialog(
    id: Long,
    board: WidgetBoard,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val name = board.folderNames[id] ?: "Folder"
    val list = board.folderApps[id].orEmpty().mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(name) }

    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (renaming) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true, colors = holoFieldColors())
                    } else {
                        Text(
                            name, style = MaterialTheme.typography.titleLarge, color = Holo.Bright,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HoloButton(if (renaming) "Save" else "Rename", diameter = 76.dp) {
                        if (renaming) {
                            board.renameFolder(id, draft.trim().ifEmpty { "Folder" })
                        } else {
                            draft = name
                        }
                        renaming = !renaming
                    }
                }
                if (list.isEmpty()) {
                    Text(
                        "This folder is empty. Long-press an app in the Databank and choose Folder.",
                        style = MaterialTheme.typography.bodyMedium, color = Holo.Muted,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 380.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.component.flattenToString() }) { app -> AppCell(app, onLaunch, onLongPress) }
                    }
                }
                ButtonRow { HoloButton("Close", onClick = onClose) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, sub: String, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Holo.Void.copy(alpha = 0.6f))
            .border(1.dp, Holo.Dim, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Holo.Bright)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
    }
}

// ───────────────────────── Extra widgets: compass, barometer, moon, sun, stopwatch ─────────────────────────

private const val SYNODIC = 29.530588853

private fun cardinal(deg: Float): String =
    listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg + 22.5f) / 45f).toInt()) % 8]

private fun weatherWord(hpa: Float): String = when {
    hpa < 985f -> "Stormy"
    hpa < 1000f -> "Rain"
    hpa < 1015f -> "Change"
    hpa < 1030f -> "Fair"
    else -> "Very dry"
}

/** Days since the last new moon (an accurate-enough average, within about half a day). */
private fun moonAge(now: LocalDateTime): Double {
    val epoch = 947182440.0 // the new moon of 6 January 2000, 18:14 UTC
    val t = now.atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()
    val days = (t - epoch) / 86400.0
    return ((days % SYNODIC) + SYNODIC) % SYNODIC
}

private fun moonPhaseName(f: Double): String = when {
    f < 0.03 || f > 0.97 -> "New Moon"
    f < 0.22 -> "Waxing Crescent"
    f < 0.28 -> "First Quarter"
    f < 0.47 -> "Waxing Gibbous"
    f < 0.53 -> "Full Moon"
    f < 0.72 -> "Waning Gibbous"
    f < 0.78 -> "Last Quarter"
    else -> "Waning Crescent"
}

/** Sunrise or sunset in UTC hours (0 to 24) for a day of the year and a place, or null if the sun does not rise or set. */
private fun sunEvent(dayOfYear: Int, lat: Double, lon: Double, rising: Boolean): Double? {
    val rad = PI / 180.0
    val lngHour = lon / 15.0
    val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
    val m = 0.9856 * t - 3.289
    var l = m + 1.916 * sin(m * rad) + 0.020 * sin(2.0 * m * rad) + 282.634
    l = ((l % 360.0) + 360.0) % 360.0
    var ra = atan(0.91764 * tan(l * rad)) / rad
    ra = ((ra % 360.0) + 360.0) % 360.0
    ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
    ra /= 15.0
    val sinDec = 0.39782 * sin(l * rad)
    val cosDec = cos(asin(sinDec))
    val cosH = (cos(90.833 * rad) - sinDec * sin(lat * rad)) / (cosDec * cos(lat * rad))
    if (cosH > 1.0 || cosH < -1.0) return null
    val h = (if (rising) 360.0 - acos(cosH) / rad else acos(cosH) / rad) / 15.0
    val tt = h + ra - 0.06571 * t - 6.622
    return (((tt - lngHour) % 24.0) + 24.0) % 24.0
}

private fun fmtHours(h: Double, is24: Boolean, suffix: Boolean = true): String {
    val total = (h * 60.0).roundToInt() % 1440
    val hh = total / 60
    val mm = total % 60
    if (is24) return "%02d:%02d".format(hh, mm)
    val h12 = if (hh % 12 == 0) 12 else hh % 12
    return "%d:%02d".format(h12, mm) + (if (suffix) (if (hh < 12) " AM" else " PM") else "")
}

private fun durationText(hours: Double): String {
    val m = (hours * 60.0).roundToInt()
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun chronoText(ms: Long): String {
    val tenths = (ms / 100L) % 10L
    val s = (ms / 1000L) % 60L
    val m = (ms / 60000L) % 60L
    val h = ms / 3600000L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d.%d".format(m, s, tenths)
}

/** Last known rough location (needs the location permission). Remembers it so the widget keeps working. */
internal fun findLocation(ctx: Context, prefs: SharedPreferences, granted: Boolean): Pair<Double, Double>? {
    if (granted) {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = (try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }) ?: continue
                if (best == null || l.time > best.time) best = l
            }
            if (best != null) {
                prefs.edit().putString("sun_lat", best.latitude.toString()).putString("sun_lon", best.longitude.toString()).apply()
                return best.latitude to best.longitude
            }
        } catch (e: Exception) {
            // fall through to the remembered place
        }
    }
    val lat = prefs.getString("sun_lat", null)?.toDoubleOrNull()
    val lon = prefs.getString("sun_lon", null)?.toDoubleOrNull()
    return if (lat != null && lon != null) lat to lon else null
}

@Composable
private fun WidgetNote(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1)
        Text(text, style = MaterialTheme.typography.labelLarge, color = Holo.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompassContent() = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val mw = maxWidth
    val mh = maxHeight
    var heading by remember { mutableFloatStateOf(0f) }
    var present by remember { mutableStateOf(true) }

    DisposableEffect(active) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            present = false
            onDispose { }
        } else {
            var sx = 0f
            var sy = 1f
            var first = true
            val rot = FloatArray(9)
            val ori = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, e.values)
                    SensorManager.getOrientation(rot, ori)
                    val nx = sin(ori[0])
                    val ny = cos(ori[0])
                    if (first) {
                        sx = nx; sy = ny; first = false
                    } else {
                        sx += 0.15f * (nx - sx)
                        sy += 0.15f * (ny - sy)
                    }
                    heading = ((atan2(sx.toDouble(), sy.toDouble()) * 180.0 / PI).toFloat() + 360f) % 360f
                }

                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            if (active) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(listener) }
        }
    }

    if (!present) {
        WidgetNote("Nav bearing", "No compass sensor on this phone.")
    } else {
        val dial = maxOf(minOf(mw, mh - 18.dp), 30.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CompassRose(heading, Modifier.size(dial))
            Text(
                "${heading.roundToInt() % 360}° ${cardinal(heading)}",
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1,
            )
        }
    }
}

@Composable
private fun BarometerContent(title: String = "Atmos pressure") {
    val ctx = LocalContext.current
    val active = LocalActive.current
    var hpa by remember { mutableFloatStateOf(0f) }
    var present by remember { mutableStateOf(true) }

    DisposableEffect(active) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sensor == null) {
            present = false
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { hpa = e.values[0] }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            if (active) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sm.unregisterListener(listener) }
        }
    }

    if (!present) {
        GaugeContent(0f, true, title, "no sensor")
    } else {
        GaugeContent(
            ((hpa - 950f) / 100f).coerceIn(0f, 1f), true, title,
            if (hpa <= 0f) "reading..." else "${hpa.roundToInt()} hPa · ${weatherWord(hpa)}",
        )
    }
}

@Composable
private fun MoonContent(now: LocalDateTime) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val mw = maxWidth
    val mh = maxHeight
    val age = moonAge(now)
    val f = age / SYNODIC
    val lit = ((1.0 - cos(2.0 * PI * f)) / 2.0 * 100.0).roundToInt()
    val name = moonPhaseName(f)
    val toNext = if (f < 0.5) SYNODIC / 2.0 - age else SYNODIC - age
    val next = (if (f < 0.5) "Full moon" else "New moon") + " in " + ceil(toNext).toInt() + " d"
    if (mw > mh * 1.4f) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            MoonDisc(age, Modifier.size(minOf(mh, mw * 0.4f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Holo.signal.lit, maxLines = 1)
                Text("$lit% lit", style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                Text(next, style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MoonDisc(age, Modifier.size(minOf(mw, mh * 0.62f)))
            Spacer(Modifier.height(2.dp))
            Text(name, fontSize = 13.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1)
            Text("$lit% lit", fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun SunContent(now: LocalDateTime, is24: Boolean, prefs: SharedPreferences) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val mh = maxHeight
    val wide = maxWidth > maxHeight * 1.3f
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    var retry by remember { mutableIntStateOf(0) }
    val loc = remember(granted, retry) { findLocation(ctx, prefs, granted) }

    if (loc == null) {
        WidgetNote(
            "Daylight cycle",
            if (!granted) "Tap to share your rough location once. It stays on this phone."
            else "No location yet. Open a maps app once, then tap here to try again.",
            Modifier.clickable {
                if (!granted) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else retry++
            },
        )
    } else {
        val offH = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
        val riseUt = sunEvent(now.dayOfYear, loc.first, loc.second, true)
        val setUt = sunEvent(now.dayOfYear, loc.first, loc.second, false)
        if (riseUt == null || setUt == null) {
            WidgetNote("Daylight cycle", "At this latitude the sun does not rise or set today.")
        } else {
            val rise = ((riseUt + offH) % 24.0 + 24.0) % 24.0
            val setH = ((setUt + offH) % 24.0 + 24.0) % 24.0
            val dayLen = ((setH - rise) + 24.0) % 24.0
            val nowH = now.hour + now.minute / 60.0
            val since = ((nowH - rise) + 24.0) % 24.0
            val up = dayLen > 0.0 && since <= dayLen
            val progress = if (up) (since / dayLen).toFloat() else null
            val status = if (up) "Sets in " + durationText(dayLen - since) else "Rises in " + durationText(((rise - nowH) + 24.0) % 24.0)
            val times = "↑ " + fmtHours(rise, is24, false) + "   ↓ " + fmtHours(setH, is24, false)
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    SunArc(progress, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Daylight cycle", style = MaterialTheme.typography.titleMedium, color = Holo.signal.lit, maxLines = 1)
                        Text(times, style = MaterialTheme.typography.bodyMedium, color = Holo.Text, maxLines = 1)
                        Text("Day " + durationText(dayLen), style = MaterialTheme.typography.bodyMedium, color = Holo.Text, maxLines = 1)
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = Holo.Muted, maxLines = 1)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    SunArc(progress, Modifier.fillMaxWidth().height(mh * 0.5f))
                    Text(times, fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Text, maxLines = 1)
                    Text(status, fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ChronoContent(id: Long, prefs: SharedPreferences) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val active = LocalActive.current
    val mw = maxWidth
    val mh = maxHeight
    var acc by remember(id) { mutableLongStateOf(prefs.getLong("chr_acc_$id", 0L)) }
    var started by remember(id) { mutableLongStateOf(prefs.getLong("chr_start_$id", 0L)) }
    var lap by remember(id) { mutableLongStateOf(prefs.getLong("chr_lap_$id", 0L)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick quickly while running and on screen. The time itself comes from the clock, so it never drifts.
    LaunchedEffect(started, active) {
        nowMs = System.currentTimeMillis()
        while (started != 0L && active) {
            delay(50)
            nowMs = System.currentTimeMillis()
        }
    }

    val elapsed = acc + (if (started != 0L) maxOf(0L, nowMs - started) else 0L)
    fun save() {
        prefs.edit().putLong("chr_acc_$id", acc).putLong("chr_start_$id", started).putLong("chr_lap_$id", lap).apply()
    }

    val dial = minOf(mh, mw * 0.3f)
    val textW = mw - dial - 12.dp
    val fs = minOf(textW.value * 0.15f, mh.value * 0.28f).coerceIn(16f, 34f)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        AnalogClock(((elapsed / 60000L) % 60L) / 5f, (elapsed % 60000L) / 1000f, Modifier.size(dial))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                chronoText(elapsed), fontSize = fs.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HoloButton(if (started != 0L) "Stop" else "Start", diameter = 50.dp) {
                    val t = System.currentTimeMillis()
                    if (started != 0L) {
                        acc += t - started
                        started = 0L
                    } else {
                        started = t
                    }
                    save()
                }
                HoloButton("Lap", diameter = 50.dp, enabled = elapsed > 0L) {
                    lap = elapsed
                    save()
                }
                HoloButton("Reset", diameter = 50.dp) {
                    acc = 0L
                    started = 0L
                    lap = 0L
                    save()
                }
            }
            if (lap > 0L) {
                Text("Lap " + chronoText(lap), fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
            }
        }
    }
}

// ───────────────────────── Databank (app drawer) ─────────────────────────

@Composable
internal fun holoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Holo.Text, unfocusedTextColor = Holo.Text,
    focusedBorderColor = Holo.signal.lit, unfocusedBorderColor = Holo.Line,
    cursorColor = Holo.signal.lit,
    focusedPlaceholderColor = Holo.Muted.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = Holo.Muted.copy(alpha = 0.6f),
    focusedContainerColor = Holo.Void, unfocusedContainerColor = Holo.Void,
)

/** The letter an app is filed under in the Databank. É goes under E, and anything that is not a letter goes under #. */
private fun sectionOf(label: String): String {
    val first = label.trim().firstOrNull() ?: return "#"
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: first
    return if (base.isLetter()) base.uppercaseChar().toString() else "#"
}

@Composable
private fun LetterHeader(letter: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(letter, fontSize = 18.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Holo.signal.lit)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(listOf(Holo.signal.glow, Color.Transparent)))
        )
    }
}

@Composable
private fun DrawerScreen(
    apps: List<AppInfo>,
    recent: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.mapNotNull { a -> fuzzyScore(a.label, query)?.let { a to it } }
            .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .map { it.first }
    }
    val calc = remember(query) { calcResult(query) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // With no search typed, apps are grouped under A, B, C ... (digits and symbols go under #)
    val sections = remember(shown, query) {
        if (query.isBlank()) shown.groupBy { sectionOf(it.label) }.toList() else emptyList()
    }
    // Grid position of each letter's header, so the letter strip can jump straight to it
    // The Recent row comes first when nothing is typed, so every letter's position is pushed down by its size
    val recentShown = if (query.isBlank()) recent else emptyList()
    val recentSize = if (recentShown.isEmpty()) 0 else 1 + recentShown.size
    val headerAt = remember(sections, recentSize) {
        var at = recentSize
        sections.associate { (letter, list) ->
            val here = at
            at += 1 + list.size
            letter to here
        }
    }
    val webSearch = {
        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query.trim()))))
    }

    Box(Modifier.fillMaxSize()) {
        SpaceBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DATABANK", style = MaterialTheme.typography.headlineMedium, color = Holo.Bright, modifier = Modifier.weight(1f))
                HoloButton("Close", diameter = 68.dp, onClick = onClose)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search apps, or type a sum") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val first = shown.firstOrNull()
                    if (first != null) onLaunch(first) else if (calc == null && query.isNotBlank()) webSearch()
                }),
                colors = holoFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            if (calc != null) {
                HoloPanel(
                    Modifier.fillMaxWidth(),
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("result", calc))
                        Toast.makeText(ctx, "Copied $calc", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "= $calc", fontSize = 28.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
                            color = Holo.signal.lit, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("Tap to copy", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (calc == null && query.isNotBlank() && shown.isEmpty()) {
                HoloPanel(Modifier.fillMaxWidth(), onClick = webSearch) {
                    Column {
                        Text("Search the web", style = MaterialTheme.typography.titleMedium, color = Holo.Bright)
                        Text(
                            "No app matches “${query.trim()}”. Tap to search DuckDuckGo.",
                            style = MaterialTheme.typography.bodyMedium, color = Holo.Text,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val tall = maxHeight
                val showStrip = query.isBlank() && sections.size > 3 && tall > 16.dp * sections.size
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = if (showStrip) 22.dp else 0.dp, bottom = 12.dp),
                ) {
                    if (query.isBlank()) {
                        if (recentShown.isNotEmpty()) {
                            item(key = "section_recent", span = { GridItemSpan(maxLineSpan) }) { LetterHeader("RECENT") }
                            items(recentShown, key = { "recent_" + it.component.flattenToString() }) { app -> AppCell(app, onLaunch, onLongPress) }
                        }
                        sections.forEach { (letter, list) ->
                            item(key = "section_$letter", span = { GridItemSpan(maxLineSpan) }) { LetterHeader(letter) }
                            items(list, key = { it.component.flattenToString() }) { app -> AppCell(app, onLaunch, onLongPress) }
                        }
                    } else {
                        items(shown, key = { it.component.flattenToString() }) { app -> AppCell(app, onLaunch, onLongPress) }
                    }
                }
                if (showStrip) {
                    Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                        sections.forEach { (letter, _) ->
                            Text(
                                letter, fontSize = 11.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.signal.lit,
                                modifier = Modifier
                                    .clickable { scope.launch { gridState.scrollToItem(headerAt[letter] ?: 0) } }
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(app: AppInfo, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconPort(app.icon, size = 62.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            app.label, fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
