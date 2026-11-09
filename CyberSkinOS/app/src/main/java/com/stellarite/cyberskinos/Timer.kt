package com.stellarite.cyberskinos

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The Timer widget: a countdown and an alarm clock in one panel.
 *
 * Both use Android's alarm service, so they ring even when CyberSkinOS is closed. A ringing notification plays the
 * alarm sound until you open the launcher, tap the notification, or press Quiet. Notifications need permission on
 * newer Android, which the widget asks for once. If you say no, it plays the sound itself while it can.
 */
object TimerEngine {
    private const val CHANNEL = "cyberskinos_timer"
    private const val NOTE_ID = 7001
    private const val ACTION = "com.stellarite.cyberskinos.RING"

    const val KIND_TIMER = 0
    const val KIND_ALARM = 1
    const val TIMER_SLOTS = 3
    private const val SNOOZE_MIN = 5

    /** Minutes a snooze lasts. */
    const val SNOOZE_MINUTES = SNOOZE_MIN

    /** Each widget can run up to three countdowns. The first uses the original kind, the others use 2 and 3 (1 is the alarm). */
    fun timerKind(slot: Int) = if (slot == 0) KIND_TIMER else slot + 1

    private fun ringIntent(ctx: Context, id: Long, kind: Int, label: String, tone: String = ""): PendingIntent {
        val i = Intent(ctx, TimerReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("cyberskinos://timer/$id/$kind"))
            .putExtra("label", label)
            .putExtra("tone", tone)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Asks Android to ring at this moment. Scheduling the same widget and kind again replaces the old one. */
    fun schedule(ctx: Context, id: Long, kind: Int, atMs: Long, label: String, tone: String = "") {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), ringIntent(ctx, id, kind, label, tone))
        } catch (e: Exception) {
            // no alarm service: the widget still counts, it just cannot ring in the background
        }
    }

    fun cancel(ctx: Context, id: Long, kind: Int) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(ringIntent(ctx, id, kind, ""))
        } catch (e: Exception) {
        }
    }

    /** Cancels every timer and alarm this launcher has scheduled, for a layout reset. */
    fun cancelAll(ctx: Context) {
        val p = ctx.getSharedPreferences("cyberskinos", Context.MODE_PRIVATE)
        p.all.keys.forEach { k ->
            Regex("(tm_end|al_at)(\\d?)_(\\d+)").matchEntire(k)?.let { m ->
                val id = m.groupValues[3].toLongOrNull() ?: return@let
                val slot = m.groupValues[2].toIntOrNull() ?: 0
                cancel(ctx, id, if (m.groupValues[1] == "tm_end") timerKind(slot) else KIND_ALARM)
            }
        }
        silence(ctx)
    }

    fun forget(ctx: Context, id: Long) {
        for (slot in 0 until TIMER_SLOTS) cancel(ctx, id, timerKind(slot))
        cancel(ctx, id, KIND_ALARM)
    }

    /** The next time the clock shows this many minutes past midnight. */
    fun nextOccurrence(minuteOfDay: Int): Long {
        val now = ZonedDateTime.now()
        var t = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    private fun notifier(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Called when an alarm goes off, even if CyberSkinOS was closed. */
    fun ring(ctx: Context, label: String, tone: String = "") {
        val nm = notifier(ctx)
        val canNotify = try {
            nm.areNotificationsEnabled()
        } catch (e: Exception) {
            false
        }
        if (!canNotify) {
            Ringer.start(ctx, toneUri = tone)
            return
        }
        try {
            val sound = Ringer.soundFor(tone)
            // a notification channel's sound cannot change once made, so each chosen ringtone gets a channel of its own
            val channelId = if (tone.isBlank()) CHANNEL else CHANNEL + "_" + Integer.toHexString(tone.hashCode())
            val chan = NotificationChannel(channelId, "Timer and alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a CyberSkinOS countdown or alarm finishes"
                setSound(
                    sound,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(chan)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label)
                .setContentText("Tap to silence")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000L)
                .build()
            n.flags = n.flags or Notification.FLAG_INSISTENT // keep the sound going until it is dealt with
            nm.notify(NOTE_ID, n)
        } catch (e: Exception) {
            Ringer.start(ctx, toneUri = tone)
        }
    }

    fun isRinging(ctx: Context): Boolean {
        if (Ringer.ringing) return true
        return try {
            notifier(ctx).activeNotifications.any { it.id == NOTE_ID }
        } catch (e: Exception) {
            false
        }
    }

    /** Stops the sound and clears the notification. */
    fun silence(ctx: Context) {
        Ringer.stop()
        try {
            notifier(ctx).cancel(NOTE_ID)
        } catch (e: Exception) {
        }
    }
}

/** Plays the alarm sound itself. Only used when notifications are switched off. */
object Ringer {
    var ringing by mutableStateOf(false)
        private set
    private var tone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    /** The sound to use: the one chosen for the widget, or the phone's alarm sound. */
    fun soundFor(tone: String): Uri? =
        (if (tone.isNotBlank()) try { Uri.parse(tone) } catch (e: Exception) { null } else null)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun start(ctx: Context, forMs: Long = 60_000L, toneUri: String = "") {
        stop()
        try {
            val uri = soundFor(toneUri)
            val r = RingtoneManager.getRingtone(ctx.applicationContext, uri) ?: return
            if (Build.VERSION.SDK_INT >= 28) {
                r.isLooping = true
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            } else {
                @Suppress("DEPRECATION")
                r.streamType = AudioManager.STREAM_ALARM
            }
            r.play()
            tone = r
            ringing = true
            handler.postDelayed(stopper, forMs)
        } catch (e: Exception) {
            ringing = false
        }
    }

    fun stop() {
        handler.removeCallbacks(stopper)
        try {
            tone?.stop()
        } catch (e: Exception) {
        }
        tone = null
        ringing = false
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerEngine.ring(context.applicationContext, intent.getStringExtra("label") ?: "Time is up", intent.getStringExtra("tone") ?: "")
    }
}

private fun mmss(ms: Long): String {
    val total = (ms + 999L) / 1000L // round up, so the display reaches 00:00 exactly when it rings
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun clockOf(minuteOfDay: Int, is24: Boolean): String {
    val t = java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    return t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
}

private fun clockAt(ms: Long, is24: Boolean): String =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))

private fun inText(ms: Long): String {
    val mins = (ms + 59_999L) / 60_000L
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

/** Preference key for one countdown slot. Slot 0 keeps the original names so older timers carry on. */
private fun slotKey(base: String, slot: Int, id: Long) = if (slot == 0) "${base}_$id" else "$base${slot}_$id"

@Composable
fun TimerContent(id: Long, prefs: SharedPreferences, is24: Boolean, tone: String = "") = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val mw = maxWidth
    val mh = maxHeight

    var mode by remember(id) { mutableIntStateOf(prefs.getInt("tm_mode_$id", 0)) }       // 0 countdown, 1 alarm
    var slot by remember(id) { mutableIntStateOf(prefs.getInt("tm_slot_$id", 0).coerceIn(0, TimerEngine.TIMER_SLOTS - 1)) }
    var setMin by remember(id, slot) { mutableIntStateOf(prefs.getInt(slotKey("tm_set", slot, id), 5)) }      // countdown length, minutes
    var end by remember(id, slot) { mutableLongStateOf(prefs.getLong(slotKey("tm_end", slot, id), 0L)) }      // when it rings, 0 = not running
    var left by remember(id, slot) { mutableLongStateOf(prefs.getLong(slotKey("tm_left", slot, id), 0L)) }    // time left while paused
    var alMin by remember(id) { mutableIntStateOf(prefs.getInt("al_min_$id", 7 * 60)) }  // alarm time, minutes after midnight
    var alAt by remember(id) { mutableLongStateOf(prefs.getLong("al_at_$id", 0L)) }      // when the alarm rings, 0 = off
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ringing by remember { mutableStateOf(false) }
    val timerKind = TimerEngine.timerKind(slot)
    val timerLabel = if (slot == 0) "Countdown finished" else "Countdown ${slot + 1} finished"

    fun save() {
        prefs.edit().putInt("tm_mode_$id", mode).putInt("tm_slot_$id", slot)
            .putInt(slotKey("tm_set", slot, id), setMin).putLong(slotKey("tm_end", slot, id), end).putLong(slotKey("tm_left", slot, id), left)
            .putInt("al_min_$id", alMin).putLong("al_at_$id", alAt).apply()
    }

    // Asked once, the first time you start something: notifications are what make it ring in the background.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notify_asked", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("notify_asked", true).apply()
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // After a restart Android forgets scheduled alarms, so put back any that are still to come.
    LaunchedEffect(id, tone) {
        val t = System.currentTimeMillis()
        for (s in 0 until TimerEngine.TIMER_SLOTS) {
            val e = prefs.getLong(slotKey("tm_end", s, id), 0L)
            if (e > t) TimerEngine.schedule(ctx, id, TimerEngine.timerKind(s), e, if (s == 0) "Countdown finished" else "Countdown ${s + 1} finished", tone)
        }
        if (alAt > t) TimerEngine.schedule(ctx, id, TimerEngine.KIND_ALARM, alAt, "Alarm", tone)
    }

    // the other countdowns, so their numbers can show that they are running
    fun slotRunning(s: Int): Boolean =
        if (s == slot) end > nowMs || left > 0L else prefs.getLong(slotKey("tm_end", s, id), 0L) > nowMs || prefs.getLong(slotKey("tm_left", s, id), 0L) > 0L
    val pending = alAt != 0L || (0 until TimerEngine.TIMER_SLOTS).any { s ->
        if (s == slot) end != 0L else prefs.getLong(slotKey("tm_end", s, id), 0L) != 0L
    }
    LaunchedEffect(pending, active) {
        while (active) {
            nowMs = System.currentTimeMillis()
            ringing = TimerEngine.isRinging(ctx)
            if (!pending) break
            delay(250)
        }
    }

    val running = end > nowMs
    val timerDone = end != 0L && end <= nowMs
    val paused = end == 0L && left > 0L
    val armed = alAt > nowMs
    val alarmRang = alAt != 0L && alAt <= nowMs

    fun snoozeTimer() {
        TimerEngine.silence(ctx)
        ringing = false
        end = System.currentTimeMillis() + TimerEngine.SNOOZE_MINUTES * 60_000L
        left = 0L
        TimerEngine.schedule(ctx, id, timerKind, end, timerLabel, tone)
        save()
    }

    fun snoozeAlarm() {
        TimerEngine.silence(ctx)
        ringing = false
        alAt = System.currentTimeMillis() + TimerEngine.SNOOZE_MINUTES * 60_000L
        TimerEngine.schedule(ctx, id, TimerEngine.KIND_ALARM, alAt, "Alarm", tone)
        save()
    }

    val fs = minOf(mw.value * 0.14f, mh.value * 0.30f).coerceIn(18f, 40f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (mode == 0) "COUNTDOWN" else "ALARM") + "  ⇄",
                modifier = Modifier.clickable { mode = 1 - mode; save() },
                fontSize = 12.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Bright, maxLines = 1,
            )
            if (mode == 0) {
                // three countdown slots: the one you are looking at is bright, and a running one is marked
                for (s in 0 until TimerEngine.TIMER_SLOTS) {
                    val here = s == slot
                    val busy = slotRunning(s)
                    Text(
                        (s + 1).toString() + if (busy) "•" else "",
                        modifier = Modifier.clickable {
                            if (!here) {
                                save()
                                slot = s
                                prefs.edit().putInt("tm_slot_$id", s).apply()
                            }
                        }.padding(start = 8.dp),
                        fontSize = 12.sp, fontFamily = Holo.font, fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                        color = if (here) Holo.Bright else if (busy) Holo.Ok else Holo.Muted, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val status = when {
                mode == 0 && running -> "rings at " + clockAt(end, is24)
                mode == 0 && timerDone -> "time's up"
                mode == 0 && paused -> "paused"
                mode == 1 && armed -> "rings in " + inText(alAt - nowMs)
                mode == 1 && alarmRang -> "rang at " + clockAt(alAt, is24)
                else -> ""
            }
            Text(status, fontSize = 11.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 1)
        }
        val shown = if (mode == 0) {
            mmss(if (running) end - nowMs else if (paused) left else if (timerDone) 0L else setMin * 60_000L)
        } else {
            clockOf(alMin, is24)
        }
        Text(shown, fontSize = fs.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Holo.signal.lit, maxLines = 1)
        if (mode == 0 && (running || paused)) {
            val total = (setMin * 60_000L).coerceAtLeast(1L)
            val frac = ((if (running) end - nowMs else left).toFloat() / total).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(3.dp).background(Holo.Dim.copy(alpha = 0.5f))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Holo.signal.glow))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mode == 0) {
                when {
                    running -> {
                        HoloButton("Pause", diameter = 50.dp) {
                            left = end - System.currentTimeMillis()
                            end = 0L
                            TimerEngine.cancel(ctx, id, timerKind)
                            save()
                        }
                        HoloButton("Reset", diameter = 50.dp) {
                            end = 0L; left = 0L
                            TimerEngine.cancel(ctx, id, timerKind)
                            save()
                        }
                    }
                    paused -> {
                        HoloButton("Resume", diameter = 50.dp) {
                            end = System.currentTimeMillis() + left
                            left = 0L
                            TimerEngine.schedule(ctx, id, timerKind, end, timerLabel, tone)
                            save()
                        }
                        HoloButton("Reset", diameter = 50.dp) { left = 0L; save() }
                    }
                    timerDone -> {
                        if (ringing) HoloButton("Quiet", diameter = 50.dp) { TimerEngine.silence(ctx); ringing = false }
                        HoloButton("Snooze", diameter = 50.dp) { snoozeTimer() }
                        HoloButton("Reset", diameter = 50.dp) {
                            end = 0L
                            TimerEngine.silence(ctx)
                            save()
                        }
                    }
                    else -> {
                        HoloButton("-1", diameter = 50.dp) { setMin = (setMin - 1).coerceAtLeast(1); save() }
                        HoloButton("+1", diameter = 50.dp) { setMin = (setMin + 1).coerceAtMost(600); save() }
                        HoloButton("+5", diameter = 50.dp) { setMin = (setMin + 5).coerceAtMost(600); save() }
                        HoloButton("Start", diameter = 50.dp) {
                            ensureNotifyPermission()
                            end = System.currentTimeMillis() + setMin * 60_000L
                            left = 0L
                            TimerEngine.schedule(ctx, id, timerKind, end, timerLabel, tone)
                            save()
                        }
                    }
                }
            } else {
                when {
                    armed -> HoloButton("Cancel", diameter = 50.dp) {
                        alAt = 0L
                        TimerEngine.cancel(ctx, id, TimerEngine.KIND_ALARM)
                        save()
                    }
                    alarmRang -> {
                        if (ringing) HoloButton("Quiet", diameter = 50.dp) { TimerEngine.silence(ctx); ringing = false }
                        HoloButton("Snooze", diameter = 50.dp) { snoozeAlarm() }
                        HoloButton("Clear", diameter = 50.dp) {
                            alAt = 0L
                            TimerEngine.silence(ctx)
                            save()
                        }
                    }
                    else -> {
                        HoloButton("-5m", diameter = 50.dp) { alMin = (alMin + 24 * 60 - 5) % (24 * 60); save() }
                        HoloButton("+5m", diameter = 50.dp) { alMin = (alMin + 5) % (24 * 60); save() }
                        HoloButton("+1h", diameter = 50.dp) { alMin = (alMin + 60) % (24 * 60); save() }
                        HoloButton("Set", diameter = 50.dp) {
                            ensureNotifyPermission()
                            alAt = TimerEngine.nextOccurrence(alMin)
                            TimerEngine.schedule(ctx, id, TimerEngine.KIND_ALARM, alAt, "Alarm", tone)
                            save()
                        }
                    }
                }
            }
        }
    }
}
