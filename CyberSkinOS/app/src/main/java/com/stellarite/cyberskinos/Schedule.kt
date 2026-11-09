package com.stellarite.cyberskinos

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/**
 * Scheduled looks: while a rule matches (a time of day, or while charging) the colours switch to those of a
 * preset. When no rule matches any more, the look you had before comes back. Layouts, widgets and wallpaper never change.
 */
object LookSchedule {
    const val MAX_RULES = 4

    /** [kind] is "time" (from..to, in minutes after midnight) or "charge". */
    class Rule(val preset: String, val kind: String, val from: Int, val to: Int) {
        val key: String get() = "$preset|$kind|$from|$to"
    }

    fun load(prefs: SharedPreferences): List<Rule> = try {
        val a = JSONArray(prefs.getString("sched_v1", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val kind = if (o.optString("k") == "charge") "charge" else "time"
            val name = o.optString("p", "")
            if (name.isBlank()) null else Rule(name.take(60), kind, o.optInt("f", 0).coerceIn(0, 1439), o.optInt("t", 0).coerceIn(0, 1439))
        }.take(MAX_RULES)
    } catch (e: Exception) {
        emptyList()
    }

    fun save(prefs: SharedPreferences, rules: List<Rule>) {
        val a = JSONArray()
        rules.take(MAX_RULES).forEach { a.put(JSONObject().put("p", it.preset).put("k", it.kind).put("f", it.from).put("t", it.to)) }
        prefs.edit().putString("sched_v1", a.toString()).apply()
    }

    fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun describe(r: Rule): String =
        if (r.kind == "charge") "while charging" else clock(r.from) + " to " + clock(r.to)

    private fun matches(r: Rule, minute: Int, charging: Boolean): Boolean = when {
        r.kind == "charge" -> charging
        r.from < r.to -> minute >= r.from && minute < r.to
        r.from > r.to -> minute >= r.from || minute < r.to // runs past midnight
        else -> false
    }

    private fun isCharging(ctx: Context): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val s = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
    }

    /** Looks at the rules and, if the right look is not already on, switches to it (or back). Cheap enough to call every half minute. */
    fun check(ctx: Context, prefs: SharedPreferences, board: WidgetBoard) {
        val rules = load(prefs)
        val on = prefs.getString("sched_on", "") ?: ""
        if (rules.isEmpty() && on.isEmpty()) return
        val t = LocalTime.now()
        val minute = t.hour * 60 + t.minute
        val charging = isCharging(ctx)
        val hit = rules.firstOrNull { matches(it, minute, charging) && board.lookOf(it.preset)?.colors != null }
        if (hit != null) {
            if (hit.key == on) return
            if (on.isEmpty()) {
                // remember what you had, so it can come back
                prefs.edit().putString("sched_base", encodeLook(Look(Holo.currentColors(), null, Holo.wallDim))?.toString() ?: "").apply()
            }
            board.lookOf(hit.preset)?.colors?.let { Holo.applyColors(ctx, it) }
            prefs.edit().putString("sched_on", hit.key).apply()
        } else if (on.isNotEmpty()) {
            val base = try {
                decodeLook(JSONObject(prefs.getString("sched_base", "") ?: ""))?.colors
            } catch (e: Exception) {
                null
            }
            if (base != null) Holo.applyColors(ctx, base)
            prefs.edit().remove("sched_on").remove("sched_base").apply()
        }
    }
}

/** Settings > Look > Schedule: add, see and remove the rules. */
@Composable
fun ScheduleDialog(board: WidgetBoard, prefs: SharedPreferences, onClose: () -> Unit) {
    var rules by remember { mutableStateOf(LookSchedule.load(prefs)) }
    val names = remember { board.lookNames() }
    var preset by remember { mutableStateOf(names.firstOrNull() ?: "") }
    var kind by remember { mutableStateOf("time") }
    var from by remember { mutableIntStateOf(22 * 60) }
    var to by remember { mutableIntStateOf(6 * 60 + 30) }

    fun commit(list: List<LookSchedule.Rule>) {
        rules = list
        LookSchedule.save(prefs, list)
    }

    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("SCHEDULED LOOKS", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Text(
                    "While a rule fits, your colours switch to those of a preset, and when it stops fitting your own look comes back. " +
                        "Layouts, widgets and wallpaper never change. The first rule that fits wins. It is checked every half minute while the home screen is showing.",
                    style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                )

                Text("Your rules", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                if (rules.isEmpty()) {
                    Text("None yet.", style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                }
                rules.forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.preset + ": " + LookSchedule.describe(r), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = Holo.Text,
                        )
                        Text(
                            "✕", color = Holo.Alert, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { commit(rules.filter { it !== r }) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                if (rules.size < LookSchedule.MAX_RULES) {
                    Text("New rule", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    if (names.isEmpty()) {
                        Text(
                            "No preset has a look yet. Save a preset with Save colours switched on, or use one of the built-in looks.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                    } else {
                        Text("Look to use", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        names.chunked(2).forEach { rowOf ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowOf.forEach { n -> SpeedChip(n, preset == n, Modifier.weight(1f)) { preset = n } }
                                if (rowOf.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeedChip("Time of day", kind == "time", Modifier.weight(1f)) { kind = "time" }
                            SpeedChip("While charging", kind == "charge", Modifier.weight(1f)) { kind = "charge" }
                        }
                        if (kind == "time") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "From " + LookSchedule.clock(from), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium, color = Holo.Text,
                                )
                                HoloButton("-", diameter = 48.dp) { from = (from - 30 + 1440) % 1440 }
                                HoloButton("+", diameter = 48.dp) { from = (from + 30) % 1440 }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Until " + LookSchedule.clock(to), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium, color = Holo.Text,
                                )
                                HoloButton("-", diameter = 48.dp) { to = (to - 30 + 1440) % 1440 }
                                HoloButton("+", diameter = 48.dp) { to = (to + 30) % 1440 }
                            }
                        }
                    }
                }

                ButtonRow {
                    if (rules.size < LookSchedule.MAX_RULES && names.isNotEmpty()) {
                        HoloButton("Add rule") {
                            if (kind == "time" && from == to) return@HoloButton
                            commit(rules + LookSchedule.Rule(preset, kind, from, to))
                        }
                    }
                    HoloButton("Close", onClick = onClose)
                }
            }
        }
    }
}
