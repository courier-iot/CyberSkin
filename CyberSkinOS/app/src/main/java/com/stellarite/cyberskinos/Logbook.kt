package com.stellarite.cyberskinos

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private class LogEntry(val label: String, val minutes: Int)

private fun hasUsageAccess(ctx: Context): Boolean = try {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
} catch (e: Exception) {
    false
}

/** How long each app was in front today, longest first. Only apps you can open yourself, and not CyberSkinOS. */
private fun loadLog(ctx: Context): Pair<List<LogEntry>, Int> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm = ctx.packageManager
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
    val rows = stats.values
        .filter { it.packageName != ctx.packageName && it.totalTimeInForeground >= 60_000L && pm.getLaunchIntentForPackage(it.packageName) != null }
        .sortedByDescending { it.totalTimeInForeground }
    val total = (rows.sumOf { it.totalTimeInForeground } / 60_000L).toInt()
    val list = rows.take(8).map { s ->
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
        } catch (e: Exception) {
            s.packageName
        }
        LogEntry(label, (s.totalTimeInForeground / 60_000L).toInt())
    }
    return list to total
}

private fun hm(minutes: Int): String = if (minutes >= 60) (minutes / 60).toString() + "h " + (minutes % 60) + "m" else minutes.toString() + "m"

/** The Usage log widget: today's most-used apps as bars. Uses Android's usage access, which you switch on once, and reads nothing else. */
@Composable
fun LogbookContent(title: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val mh = maxHeight // read here: inside the Column the scope's maxHeight is out of reach
    var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
    var rows by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    val accent = Holo.signal.lit
    val bar = Holo.signal.glow

    LaunchedEffect(active) {
        while (active) {
            granted = hasUsageAccess(ctx) // you may have just come back from the settings page
            if (granted) {
                val (list, mins) = withContext(Dispatchers.IO) {
                    try {
                        loadLog(ctx)
                    } catch (e: Exception) {
                        emptyList<LogEntry>() to 0
                    }
                }
                rows = list
                total = mins
                loaded = true
            }
            delay(60_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
                color = accent, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (granted && loaded) Text("Today " + hm(total), fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
        }
        if (!granted) {
            Text(
                "Show which apps you used most today. Android keeps these figures; they are read on this phone only.",
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted,
            )
            Spacer(Modifier.height(6.dp))
            ButtonRow {
                HoloButton("Allow", diameter = 56.dp) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        // no such settings page on this phone
                    }
                }
            }
        } else if (rows.isEmpty()) {
            Text(
                if (loaded) "Nothing used for a minute or more yet today." else "Reading the usage log...",
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted,
            )
        } else {
            val fit = (((mh.value - 22f) / 24f).toInt()).coerceAtLeast(1)
            val most = rows.first().minutes.coerceAtLeast(1)
            rows.take(fit).forEach { r ->
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.label, Modifier.width(92.dp), fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.weight(1f).height(8.dp).background(Holo.Dim.copy(alpha = 0.35f))) {
                        Box(
                            Modifier.fillMaxWidth(r.minutes.toFloat() / most).fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(bar.copy(alpha = 0.5f), bar))),
                        )
                    }
                    Text(
                        hm(r.minutes), Modifier.width(54.dp), fontSize = 12.sp, fontFamily = Holo.font, color = accent,
                        maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}
