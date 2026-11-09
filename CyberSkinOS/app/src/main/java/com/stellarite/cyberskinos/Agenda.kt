package com.stellarite.cyberskinos

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private class AgendaEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** One line of the feed: either a day heading or an event. */
private class AgendaRow(val heading: String?, val time: String = "", val title: String = "", val now: Boolean = false)

private fun hasCalendarPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** Events from today to [days] days ahead, from the calendars you have switched on in your calendar app. */
private fun loadAgenda(ctx: Context, days: Int): List<AgendaEvent> {
    val zone = ZoneId.systemDefault()
    val nowMs = System.currentTimeMillis()
    val startOfToday = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val to = startOfToday + days * 86_400_000L
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, startOfToday)
        ContentUris.appendId(it, to)
    }.build()
    val out = ArrayList<AgendaEvent>()
    ctx.contentResolver.query(
        uri,
        arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
        "${CalendarContract.Calendars.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 60) {
            val allDay = c.getInt(3) == 1
            var begin = c.getLong(1)
            var end = c.getLong(2)
            if (allDay) {
                // all-day events are stored at midnight UTC: move them to midnight where you are
                begin = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                end = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            }
            if (end <= nowMs) continue // already over
            out.add(AgendaEvent(c.getString(0)?.takeIf { it.isNotBlank() } ?: "(no title)", begin, end, allDay))
        }
    }
    return out.sortedBy { it.begin }
}

private fun agendaRows(events: List<AgendaEvent>, is24: Boolean): List<AgendaRow> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val nowMs = System.currentTimeMillis()
    val timeFmt = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a")
    val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val rows = ArrayList<AgendaRow>()
    var lastDay: LocalDate? = null
    events.forEach { e ->
        val day = maxOf(Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate(), today)
        if (day != lastDay) {
            lastDay = day
            rows.add(
                AgendaRow(
                    when (day) {
                        today -> "Today"
                        today.plusDays(1) -> "Tomorrow"
                        else -> day.format(dayFmt)
                    },
                ),
            )
        }
        val time = if (e.allDay) "All day" else Instant.ofEpochMilli(e.begin).atZone(zone).format(timeFmt)
        rows.add(AgendaRow(null, time, e.title, now = e.begin <= nowMs))
    }
    return rows
}

/** The Schedule feed widget: what is coming up in your calendar. Reads the calendar on the phone only, and only after you allow it. */
@Composable
fun AgendaContent(is24: Boolean, title: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val mh = maxHeight
    var granted by remember { mutableStateOf(hasCalendarPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var events by remember { mutableStateOf<List<AgendaEvent>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(granted, active) {
        while (granted && active) {
            events = withContext(Dispatchers.IO) {
                try {
                    loadAgenda(ctx, 14)
                } catch (e: Exception) {
                    emptyList() // permission taken away, or no calendar on this phone
                }
            }
            loaded = true
            delay(60_000)
        }
    }

    val open = {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            // no calendar app installed
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
            color = Holo.signal.lit, maxLines = 1,
            modifier = Modifier.height(22.dp),
        )
        if (!granted) {
            Text(
                "Show what is coming up in your calendar. It is read on this phone only.",
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted,
            )
            Spacer(Modifier.height(6.dp))
            ButtonRow { HoloButton("Allow", diameter = 56.dp) { ask.launch(Manifest.permission.READ_CALENDAR) } }
        } else {
            val rows = agendaRows(events, is24)
            if (rows.isEmpty()) {
                Text(
                    if (loaded) "Nothing coming up in the next two weeks." else "Reading your calendar...",
                    fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted, modifier = Modifier.clickable { open() },
                )
            } else {
                val fit = (((mh.value - 22f) / 20f).toInt()).coerceAtLeast(1)
                Column(Modifier.fillMaxWidth().clickable { open() }) {
                    rows.take(fit).forEach { r ->
                        if (r.heading != null) {
                            Text(
                                r.heading.uppercase(), fontSize = 11.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp, color = Holo.Muted, maxLines = 1, modifier = Modifier.height(20.dp),
                            )
                        } else {
                            Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    r.time, fontSize = 12.sp, fontFamily = Holo.font, maxLines = 1,
                                    color = if (r.now) Holo.Ok else Holo.signal.lit,
                                    modifier = Modifier.width(64.dp),
                                )
                                Text(
                                    r.title, fontSize = 13.sp, fontFamily = Holo.font, color = Holo.Text,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
