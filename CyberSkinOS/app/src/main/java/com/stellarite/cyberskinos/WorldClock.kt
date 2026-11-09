package com.stellarite.cyberskinos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val MAX_ZONES = 6

/** "America/New_York" becomes "New York". */
internal fun cityOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

/** Zone ids worth offering in the search: real regions only, not the old aliases. */
internal fun searchableZones(): List<String> =
    ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && !it.startsWith("US/") && !it.startsWith("Canada/") }.sorted()

private fun validZone(id: String) = try {
    ZoneId.of(id); true
} catch (e: Exception) {
    false
}

/** The difference from here, like "+9h" or "-3h 30m". */
private fun offsetText(diffMinutes: Int): String {
    if (diffMinutes == 0) return "same time"
    val sign = if (diffMinutes > 0) "+" else "-"
    val a = kotlin.math.abs(diffMinutes)
    return sign + (a / 60) + "h" + (if (a % 60 != 0) " " + (a % 60) + "m" else "")
}

/** The World time widget: the time here, then up to six cities you choose in the widget's settings. */
@Composable
fun WorldClockContent(now: LocalDateTime, is24: Boolean, zones: List<String>, title: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val here = ZoneId.systemDefault()
    val hereNow = now.atZone(here)
    val fmt = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a")
    val timeColour = Holo.signal.lit
    val roomy = maxHeight > 150.dp // tall enough for the small "tomorrow, +9h" line under each city
    val rowH = if (roomy) 38.dp else 28.dp
    val fit = (((maxHeight.value - 22f) / rowH.value).toInt()).coerceAtLeast(1)

    val rows = buildList {
        add(Triple("Here", hereNow, "")) // city, time, tag
        zones.filter { validZone(it) }.forEach { id ->
            val z = hereNow.withZoneSameInstant(ZoneId.of(id))
            val diff = (z.offset.totalSeconds - hereNow.offset.totalSeconds) / 60
            val day = when {
                z.toLocalDate().isAfter(hereNow.toLocalDate()) -> "tomorrow, "
                z.toLocalDate().isBefore(hereNow.toLocalDate()) -> "yesterday, "
                else -> ""
            }
            add(Triple(cityOf(id), z, day + offsetText(diff)))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
            color = timeColour, maxLines = 1, modifier = Modifier.height(22.dp),
        )
        rows.take(fit).forEach { (city, z, tag) ->
            Row(Modifier.fillMaxWidth().height(rowH), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(city, fontSize = 14.sp, fontFamily = Holo.font, color = Holo.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (tag.isNotEmpty() && roomy) {
                        Text(tag, fontSize = 10.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
                    }
                }
                Text(z.format(fmt), fontSize = 20.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = timeColour, maxLines = 1, softWrap = false)
            }
        }
        if (zones.isEmpty() && fit > 1) {
            Text(
                "Tap the ⚙ in Arrange mode to add cities.",
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted,
            )
        }
    }
}
