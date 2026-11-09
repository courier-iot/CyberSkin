package com.stellarite.cyberskinos

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Settings that belong to one widget only. The defaults mean "behave as before". */
data class WidgetSettings(
    val clock: Int = 0,             // 0 = follow the phone, 12 or 24
    val fahrenheit: Boolean = false,
    val title: String = "",         // blank = the widget's own title
    val opacity: Float = 1f,        // 0.2 to 1: how solid the panel behind the widget is
    val vials: List<String> = DEFAULT_VIALS, // Vitals widget: which readings, in order
    val vialColors: Map<String, Int> = emptyMap(), // Vitals widget: own liquid colour per reading (ARGB). Missing = automatic
    val tone: String = "",          // Timer: ringtone (a content address), blank = the phone's alarm sound
    val lanternMin: Int = 0,        // Floodlight: switch off by itself after this many minutes, 0 = never
    val lanternLevel: Int = 100,    // Floodlight: brightness in percent, on phones that can dim the torch
    val player: String = "",        // Audio deck: package of the app to follow, blank = whichever is playing
    val zones: List<String> = emptyList(), // World time: time-zone ids such as "Asia/Tokyo"
    val dial: List<String> = emptyList(),  // Comms: "Name|number" for each person
)

internal fun encodeSettings(s: WidgetSettings): String {
    val vc = JSONObject()
    s.vialColors.forEach { (k, v) -> vc.put(k, v) }
    return JSONObject().put("c", s.clock).put("f", s.fahrenheit).put("t", s.title).put("o", s.opacity.toDouble())
        .put("v", s.vials.joinToString(",")).put("vc", vc).put("p", s.player).put("tn", s.tone).put("lm", s.lanternMin).put("ll", s.lanternLevel)
        .put("z", JSONArray(s.zones)).put("sd", JSONArray(s.dial)).toString()
}

internal fun decodeSettings(raw: String?): WidgetSettings {
    if (raw == null) return WidgetSettings()
    return try {
        val o = JSONObject(raw)
        WidgetSettings(
            clock = o.optInt("c", 0).let { if (it == 12 || it == 24) it else 0 },
            fahrenheit = o.optBoolean("f", false),
            title = o.optString("t", ""),
            opacity = o.optDouble("o", 1.0).toFloat().coerceIn(0.2f, 1f),
            vials = o.optString("v", "").split(",")
                .filter { k -> VIAL_METRICS.any { it.key == k } }.distinct().take(MAX_VIALS)
                .ifEmpty { DEFAULT_VIALS },
            vialColors = o.optJSONObject("vc")?.let { vc ->
                vc.keys().asSequence().filter { k -> VIAL_METRICS.any { it.key == k } }.associateWith { vc.optInt(it, 0) }
            }.orEmpty(),
            player = o.optString("p", "").take(150),
            tone = o.optString("tn", "").take(400),
            lanternMin = o.optInt("lm", 0).let { if (it in listOf(2, 5, 10, 30)) it else 0 },
            lanternLevel = o.optInt("ll", 100).coerceIn(10, 100),
            zones = o.optJSONArray("z")?.let { a -> (0 until a.length()).map { a.optString(it, "") } }.orEmpty()
                .filter { id -> id.contains('/') && id.length < 60 && java.time.ZoneId.getAvailableZoneIds().contains(id) }.distinct().take(MAX_ZONES),
            dial = o.optJSONArray("sd")?.let { a -> (0 until a.length()).map { a.optString(it, "") } }.orEmpty()
                .filter { dialEntryOf(it) != null }.distinct().take(MAX_DIAL),
        )
    } catch (e: Exception) {
        WidgetSettings()
    }
}

/** Widgets that show a time, so a 12 or 24 hour choice makes sense. */
internal fun WType.hasClockFormat() = this == WType.CLOCK || this == WType.NIXIE || this == WType.ANALOG || this == WType.DIGITAL || this == WType.SUN ||
    this == WType.TIMER || this == WType.AGENDA || this == WType.WORLD

/** Widgets that show a temperature. */
internal fun WType.hasTemperature() = this == WType.ENGINE || this == WType.VIALS || this == WType.WEATHER

/** Widgets with a heading you can rename. Folders and link caches are renamed from inside. */
internal fun WType.hasTitle() = this == WType.BOILER || this == WType.CARGO || this == WType.BAROMETER ||
    this == WType.MEMO || this == WType.SHELF || this == WType.PICKS || this == WType.AGENDA || this == WType.WEATHER ||
    this == WType.WORLD || this == WType.CONTACTS || this == WType.LOGBOOK

/** The heading a widget shows when you have not typed one: its own name. */
internal fun WType.defaultTitle(): String = label()

/** The settings pop-up shown when you tap ⚙ in Arrange mode. Every change is saved straight away. */
@Composable
fun WidgetSettingsDialog(w: WidgetItem, board: WidgetBoard, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cur = board.settingsOf(w.id)
    var title by remember(w.id) { mutableStateOf(cur.title) }
    fun change(f: (WidgetSettings) -> WidgetSettings) = board.setSettings(w.id, f(board.settingsOf(w.id)))

    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(w.type.label().uppercase(), style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Text("These settings belong to this widget only.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)

                if (w.type.hasClockFormat()) {
                    Text("Hour format", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedChip("Phone", cur.clock == 0, Modifier.weight(1f)) { change { it.copy(clock = 0) } }
                        SpeedChip("12-hour", cur.clock == 12, Modifier.weight(1f)) { change { it.copy(clock = 12) } }
                        SpeedChip("24-hour", cur.clock == 24, Modifier.weight(1f)) { change { it.copy(clock = 24) } }
                    }
                }

                if (w.type == WType.MUSIC) {
                    Text("Signal colour", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "The deck glows in your signal colour. Change it in Settings > Look > Signal colour. " +
                            "Resize the widget to change its shape: wide and short is a strip, taller and narrower is a square card.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    Text("Track info", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Play, pause, next and previous always work. The track name, artist and progress line need Notification access, " +
                            "which Android asks you to switch on once. On some phones you may first need to allow restricted settings " +
                            "for CyberSkinOS in its App info.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow { HoloButton("Notification access") { openListenerSettings(ctx) } }
                    Text("Player to follow", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Automatic follows whichever app is playing. Pick one to stay with that app even when others are running. " +
                            "Only apps that are playing right now are listed, so start the one you want first. Needs Notification access.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    val players = remember { activePlayers(ctx) }
                    val choices = buildList {
                        add("" to "Automatic")
                        addAll(players)
                        if (cur.player.isNotBlank() && players.none { it.first == cur.player }) add(cur.player to playerName(ctx, cur.player))
                    }
                    choices.chunked(2).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { (pkg, name) ->
                                SpeedChip(name, cur.player == pkg, Modifier.weight(1f)) { change { it.copy(player = pkg) } }
                            }
                            if (rowOf.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (w.type == WType.TIMER) {
                    Text("Ringtone", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    val pickTone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                        if (res.resultCode == Activity.RESULT_OK) {
                            val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                            }
                            change { it.copy(tone = picked?.toString() ?: "") }
                        }
                    }
                    val toneName = remember(cur.tone) {
                        if (cur.tone.isBlank()) "The phone's alarm sound" else try {
                            RingtoneManager.getRingtone(ctx, Uri.parse(cur.tone))?.getTitle(ctx) ?: "Your choice"
                        } catch (e: Exception) {
                            "Your choice"
                        }
                    }
                    Text(
                        "$toneName. Used by every countdown and the alarm in this widget. " +
                            "A countdown has three slots: tap 1, 2 or 3 beside its title to switch. A number with a dot is running.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Choose") {
                            val ask = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Timer ringtone")
                            if (cur.tone.isNotBlank()) ask.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur.tone))
                            try {
                                pickTone.launch(ask)
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "No ringtone chooser on this phone.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (cur.tone.isNotBlank()) HoloButton("Default") { change { it.copy(tone = "") } }
                    }
                }

                if (w.type == WType.WORLD) {
                    Text("Cities", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Your own time is always the first line. Add up to $MAX_ZONES cities by typing part of the name, for example Tokyo or New York. " +
                            "Cities are listed under their time-zone name, so Mumbai is Kolkata. Tap a city below to remove it.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    cur.zones.chunked(2).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { id ->
                                SpeedChip("✕ " + cityOf(id), true, Modifier.weight(1f)) { change { s -> s.copy(zones = s.zones - id) } }
                            }
                            if (rowOf.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    var query by remember(w.id) { mutableStateOf("") }
                    val allZones = remember { searchableZones() }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(30) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search for a city") },
                        colors = holoFieldColors(),
                    )
                    if (query.trim().length >= 2) {
                        val found = allZones.filter { it.replace('_', ' ').contains(query.trim(), ignoreCase = true) && it !in cur.zones }.take(6)
                        if (found.isEmpty()) {
                            Text("No city by that name.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                        }
                        found.chunked(2).forEach { rowOf ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowOf.forEach { id ->
                                    SpeedChip(cityOf(id), false, Modifier.weight(1f)) {
                                        if (board.settingsOf(w.id).zones.size < MAX_ZONES) {
                                            change { s -> s.copy(zones = s.zones + id) }
                                            query = ""
                                        } else {
                                            Toast.makeText(ctx, "That is the most cities this widget holds.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                if (rowOf.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (w.type == WType.CONTACTS) {
                    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                        val uri = res.data?.data
                        if (res.resultCode == Activity.RESULT_OK && uri != null) {
                            try {
                                ctx.contentResolver.query(
                                    uri,
                                    arrayOf(
                                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                                    ),
                                    null, null, null,
                                )?.use { c ->
                                    if (c.moveToFirst()) {
                                        val name = (c.getString(0) ?: "").replace("|", " ").trim().take(30)
                                        val number = (c.getString(1) ?: "").trim().take(40)
                                        if (name.isNotEmpty() && number.isNotEmpty()) {
                                            change { s -> s.copy(dial = (s.dial + (name + "|" + number)).distinct().take(MAX_DIAL)) }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Could not read that contact.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Text("People", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Choose up to $MAX_DIAL people. Tapping one opens the dialler with the number ready; press and hold to write a text. " +
                            "CyberSkinOS never sees your other contacts: Android hands over only the one you pick. The names and numbers are kept in this widget and in your backup file. Tap a name below to remove it.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    cur.dial.mapNotNull { raw -> dialEntryOf(raw)?.let { raw to it } }.chunked(2).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { (raw, e) ->
                                SpeedChip("✕ " + e.name, true, Modifier.weight(1f)) { change { s -> s.copy(dial = s.dial - raw) } }
                            }
                            if (rowOf.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    ButtonRow {
                        if (cur.dial.size < MAX_DIAL) {
                            HoloButton("Add contact") {
                                try {
                                    pickContact.launch(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "No contacts app on this phone.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                if (w.type == WType.LOGBOOK) {
                    Text("Usage access", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "The Usage log adds up how long each app was in front today, using the figures Android already keeps. " +
                            "Android asks you to switch on Usage access for CyberSkinOS once. Nothing is stored or sent anywhere.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    ButtonRow {
                        HoloButton("Usage access") {
                            try {
                                ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "This phone has no Usage access page.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                if (w.type == WType.LANTERN) {
                    Text("Switch off by itself", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Puts the torch out after this long, so it cannot drain the battery in a pocket.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Never", 2 to "2 min", 5 to "5 min").forEach { (m, label) ->
                            SpeedChip(label, cur.lanternMin == m, Modifier.weight(1f)) { change { it.copy(lanternMin = m) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10 to "10 min", 30 to "30 min").forEach { (m, label) ->
                            SpeedChip(label, cur.lanternMin == m, Modifier.weight(1f)) { change { it.copy(lanternMin = m) } }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Text("Brightness", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    if (remember { lanternMaxLevel(ctx) } > 1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25 to "Low", 60 to "Medium", 100 to "Full").forEach { (p, label) ->
                                SpeedChip(label, cur.lanternLevel == p, Modifier.weight(1f)) { change { it.copy(lanternLevel = p) } }
                            }
                        }
                        Text("Takes effect the next time you switch it on.", style = MaterialTheme.typography.labelLarge, color = Holo.Muted)
                    } else {
                        Text(
                            "This phone cannot dim its torch, or has an Android older than 13, so it always shines at full brightness.",
                            style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                        )
                    }
                }

                if (w.type == WType.VIALS) {
                    Text("Readings", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Tap to add or remove a reading. They appear in the order you pick them. Choose 1 to $MAX_VIALS.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    VIAL_METRICS.chunked(3).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { m ->
                                SpeedChip(m.label, m.key in cur.vials, Modifier.weight(1f)) {
                                    change { s ->
                                        val now = board.settingsOf(w.id).vials
                                        s.copy(
                                            vials = when {
                                                m.key in now -> if (now.size > 1) now - m.key else now // keep at least one
                                                now.size < MAX_VIALS -> now + m.key
                                                else -> now
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (w.type == WType.VIALS) {
                    Text("Bar colour", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Text(
                        "Auto turns amber, then red, as a level gets worrying. Pick a colour to keep a bar the same. Tap a bar on the home screen to see its exact figure.",
                        style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                    )
                    cur.vials.forEach { key ->
                        val metric = VIAL_METRICS.firstOrNull { it.key == key } ?: return@forEach
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(metric.label, Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge, color = Holo.Text, maxLines = 1)
                            VIAL_COLOURS.forEach { (name, argb) ->
                                val chosen = cur.vialColors[key] == argb
                                val swatch = if (argb == null) Holo.Void else Color(argb)
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(swatch)
                                        .border(if (chosen) 3.dp else 1.dp, if (chosen) Holo.Bright else Holo.Muted, CircleShape)
                                        .clickable {
                                            change { s ->
                                                s.copy(vialColors = if (argb == null) s.vialColors - key else s.vialColors + (key to argb))
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { if (argb == null) Text("A", color = Holo.Text, style = MaterialTheme.typography.labelLarge) }
                            }
                        }
                    }
                }

                if (w.type == WType.ENGINE || w.type == WType.WEATHER || (w.type == WType.VIALS && "heat" in cur.vials)) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedChip("Celsius", !cur.fahrenheit, Modifier.weight(1f)) { change { it.copy(fahrenheit = false) } }
                        SpeedChip("Fahrenheit", cur.fahrenheit, Modifier.weight(1f)) { change { it.copy(fahrenheit = true) } }
                    }
                }

                if (w.type.hasTitle()) {
                    Text("Title", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40); change { s -> s.copy(title = title) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(w.type.defaultTitle()) },
                        colors = holoFieldColors(),
                    )
                }

                Text("Opacity " + (cur.opacity * 100f).roundToInt() + "%", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                Text(
                    "How solid the glass behind the widget is. Lower it to let your wallpaper show through.",
                    style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                )
                Slider(
                    value = cur.opacity,
                    onValueChange = { v -> change { it.copy(opacity = v) } },
                    valueRange = 0.2f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Holo.Bright, activeTrackColor = Holo.Line, inactiveTrackColor = Holo.Void,
                    ),
                )

                if (board.pageCount > 1) {
                    Text("Move to page", style = MaterialTheme.typography.titleMedium, color = Holo.Text)
                    ButtonRow {
                        for (p in 0 until board.pageCount) {
                            if (p != board.page) {
                                HoloButton("Page ${p + 1}") {
                                    if (board.moveToPage(w.id, p)) {
                                        onClose()
                                    } else {
                                        Toast.makeText(ctx, "No room left on page ${p + 1}.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }

                ButtonRow {
                    HoloButton("Reset") {
                        title = ""
                        board.setSettings(w.id, WidgetSettings())
                    }
                    HoloButton("Done", onClick = onClose)
                }
            }
        }
    }
}
