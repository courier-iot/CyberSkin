package com.stellarite.cyberskinos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Backup, restore and reset.
 *
 * A backup is one text (JSON) file holding every saved setting plus the few private files the launcher keeps
 * (your wallpaper, your own font, and the wallpaper copies kept inside presets). Nothing is sent anywhere:
 * Android's file picker decides where the file goes.
 */
object Backup {
    private const val MAGIC = "cyberskinos-backup"
    private const val VERSION = 1
    private const val MAX_BYTES = 48L * 1024 * 1024

    /** Private files that belong in a backup. The pattern also stops a hand-edited file from writing anywhere else. */
    private val backedUpFiles = Regex("wallpaper\\.jpg|wallpaper_p\\d+\\.jpg|custom_font|pw_\\d+\\.jpg")

    /** Everything that makes up the widget layout, including each widget's own notes, folders, links and settings. */
    private val layoutKeys = Regex(
        "(widgets_v1|widgets_land)(_p\\d+)?|gsig_.*|lo_\\d+|pages|grid|grid_done|grid_kind|fab_.*|" +
            "(wset|lname|llinks|note|notes|fname|fapps|chr_acc|chr_start|chr_lap|ch_run|ch_base|ch_acc|ch_lap|tm_mode|tm_slot|tm_set[12]?|tm_end[12]?|tm_left[12]?|al_min|al_at|aw)_\\d+",
    )

    /** The look and feel: palette, signal colour, your own colours, font, motion and sounds. */
    private val lookKeys = listOf(
        "theme", "signal", "cust_line", "cust_deck", "cust_signal", "spin", "font", "wall_dim", "anim", "tint_icons",
        "sfx_on", "sfx_vol", "sfx_pulses", "sched_v1",
    )

    /** Notes the launcher keeps about itself (reminders and safe mode). They are not part of a backup. */
    private val bookkeepingKeys = setOf(
        "sched_on", "sched_base", // which scheduled look is on, and the look to go back to
        "boot_count", "safe_stage", "safe_notice", "last_backup", "remind_snooze", "first_seen",
        "wx_json", "wx_time", "wx_spot", // the Weather widget's last answer
    )

    private const val DAY = 24L * 60 * 60 * 1000
    private const val REMIND_AFTER_DAYS = 30L
    private const val SNOOZE_DAYS = 7L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cyberskinos", Context.MODE_PRIVATE)

    // ───────────── reminder ─────────────

    /** When you last saved a backup (milliseconds), or 0 if you never have. */
    fun lastBackup(ctx: Context): Long = prefs(ctx).getLong("last_backup", 0L)

    fun markBackedUp(ctx: Context) {
        Sfx.play(Sfx.Kind.CHIME)
        prefs(ctx).edit().putLong("last_backup", System.currentTimeMillis()).apply()
    }

    fun remindOn(ctx: Context): Boolean = prefs(ctx).getBoolean("remind_backup", true)

    fun setRemind(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("remind_backup", on).apply()
    }

    /** "Later" on the reminder: stay quiet for a week. */
    fun snooze(ctx: Context) {
        prefs(ctx).edit().putLong("remind_snooze", System.currentTimeMillis()).apply()
    }

    /**
     * True when it is time to suggest a backup: reminders are on, 30 days have passed since your last backup
     * (or since CyberSkinOS was first opened, if you never made one), and you have not said "Later" this week.
     */
    fun reminderDue(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean("remind_backup", true)) return false
        val now = System.currentTimeMillis()
        val first = p.getLong("first_seen", 0L)
        if (first == 0L) {
            p.edit().putLong("first_seen", now).apply()
            return false
        }
        val last = maxOf(p.getLong("last_backup", 0L), first)
        if (now - last < REMIND_AFTER_DAYS * DAY) return false
        return now - p.getLong("remind_snooze", 0L) >= SNOOZE_DAYS * DAY
    }

    // ───────────── rescue copy (used by safe mode) ─────────────

    private fun rescueFile(ctx: Context) = File(ctx.filesDir, "rescue.json")

    fun hasRescue(ctx: Context): Boolean = rescueFile(ctx).exists()

    internal fun saveRescue(ctx: Context) {
        try {
            rescueFile(ctx).outputStream().use { export(ctx, it) }
        } catch (e: Exception) {
            // a failed rescue copy must never stop the launcher from starting
        }
    }

    /** Brings back what CyberSkinOS had before safe mode reset it. */
    fun restoreRescue(ctx: Context): Boolean = try {
        val f = rescueFile(ctx)
        f.exists() && f.inputStream().use { restore(ctx, it) }
    } catch (e: Exception) {
        false
    }

    // ───────────── backup ─────────────

    fun export(ctx: Context, out: OutputStream) {
        val saved = JSONObject()
        for ((key, value) in prefs(ctx).all) {
            if (key in bookkeepingKeys) continue
            if (key.startsWith("aw_")) continue // other apps' widgets are tied to this phone and cannot be carried to another
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Boolean -> entry.put("t", "b").put("v", value)
                is Float -> if (value.isFinite()) entry.put("t", "f").put("v", value.toDouble()) else continue
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            saved.put(key, entry)
        }
        val files = JSONObject()
        ctx.filesDir.listFiles()?.filter { it.isFile && backedUpFiles.matches(it.name) }?.forEach {
            files.put(it.name, Base64.encodeToString(it.readBytes(), Base64.NO_WRAP))
        }
        val root = JSONObject()
            .put("format", MAGIC)
            .put("version", VERSION)
            .put("created", System.currentTimeMillis())
            .put("prefs", saved)
            .put("files", files)
        out.write(root.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun readLimited(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(tmp)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            buf.write(tmp, 0, n)
        }
        return buf.toString("UTF-8")
    }

    /**
     * Reads a backup and replaces everything with it. The whole file is checked first, so a file that is
     * not a CyberSkinOS backup, or is damaged, changes nothing. Returns false in that case.
     */
    fun restore(ctx: Context, input: InputStream): Boolean {
        return try {
            val text = readLimited(input) ?: return false
            val root = JSONObject(text)
            if (root.optString("format") != MAGIC) return false
            if (root.optInt("version", 0) > VERSION) return false

            val saved = root.getJSONObject("prefs")
            val writes = ArrayList<(SharedPreferences.Editor) -> Unit>()
            val keys = saved.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val e = saved.getJSONObject(key)
                when (e.getString("t")) {
                    "s" -> { val v = e.getString("v"); writes.add { it.putString(key, v) } }
                    "i" -> { val v = e.getInt("v"); writes.add { it.putInt(key, v) } }
                    "l" -> { val v = e.getLong("v"); writes.add { it.putLong(key, v) } }
                    "b" -> { val v = e.getBoolean("v"); writes.add { it.putBoolean(key, v) } }
                    "f" -> { val v = e.getDouble("v").toFloat(); writes.add { it.putFloat(key, v) } }
                    "ss" -> {
                        val arr = e.getJSONArray("v")
                        val v = HashSet<String>().apply { for (i in 0 until arr.length()) add(arr.getString(i)) }
                        writes.add { it.putStringSet(key, v) }
                    }
                    else -> return false
                }
            }

            val fileData = HashMap<String, ByteArray>()
            root.optJSONObject("files")?.let { files ->
                val names = files.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    if (backedUpFiles.matches(name)) fileData[name] = Base64.decode(files.getString(name), Base64.DEFAULT)
                }
            }

            // Everything parsed: now replace what is here.
            ctx.filesDir.listFiles()?.filter { backedUpFiles.matches(it.name) }?.forEach { it.delete() }
            fileData.forEach { (name, bytes) -> File(ctx.filesDir, name).writeBytes(bytes) }
            val ed = prefs(ctx).edit().clear()
            writes.forEach { it(ed) }
            ed.commit()
        } catch (e: Exception) {
            false
        }
    }

    // ───────────── reset ─────────────

    /**
     * Puts the widgets back to the Bridge layout on one page. Notes, folders, links and widget settings go with
     * them, and the home star and grid size return to their defaults. Colours, presets and hidden apps are kept.
     */
    fun resetLayout(ctx: Context) {
        TimerEngine.cancelAll(ctx) // timers and alarms belong to widgets that are about to go
        val p = prefs(ctx)
        AppWidgets.releaseAll(ctx, p) // so do the widgets borrowed from other apps
        val ed = p.edit()
        p.all.keys.filter { layoutKeys.matches(it) }.forEach { ed.remove(it) }
        ed.commit()
        // the extra pages are gone, so their wallpapers go too
        for (i in 0 until 16) File(ctx.filesDir, "wallpaper_p$i.jpg").delete()
    }

    /**
     * The Stellarite palette, Teal signal, HUD font, normal motion speed, motion on and no wallpaper.
     * Layout, presets and hidden apps are kept.
     */
    fun restoreDefaults(ctx: Context) {
        val ed = prefs(ctx).edit()
        lookKeys.forEach { ed.remove(it) }
        ed.remove("sched_on").remove("sched_base") // a scheduled look that was on must not bring the old look back afterwards
        ed.commit()
        File(ctx.filesDir, "wallpaper.jpg").delete()
        for (i in 0 until 16) File(ctx.filesDir, "wallpaper_p$i.jpg").delete()
        File(ctx.filesDir, "custom_font").delete()
        Holo.customLine = Color(0xFF00B3A6)
        Holo.customDeck = Color(0xFF111C36)
        Holo.customSignal = Color(0xFF00C2B3)
    }

    /** Rebuilds the screen so everything is read in again from what is saved. */
    fun refresh(ctx: Context) {
        var c: Context = ctx
        while (c is ContextWrapper) {
            if (c is Activity) { c.recreate(); return }
            c = c.baseContext
        }
    }
}

/**
 * Safe mode. If CyberSkinOS fails to open three times in a row, it starts again from the Bridge layout, after
 * keeping a rescue copy of everything. If it still fails three more times, it also goes back to the default look.
 * "Opened" is counted when the activity starts and cleared once the launcher has been running for five seconds.
 */
object SafeMode {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cyberskinos", Context.MODE_PRIVATE)

    /** Call first thing in MainActivity.onCreate, before anything is read. */
    fun begin(ctx: Context) {
        val p = prefs(ctx)
        val n = p.getInt("boot_count", 0) + 1
        p.edit().putInt("boot_count", n).commit()
        val stage = p.getInt("safe_stage", 0)
        try {
            if (n >= 6 && stage < 2) {
                Backup.restoreDefaults(ctx)
                p.edit().putInt("safe_stage", 2).putInt("safe_notice", 2).commit()
            } else if (n >= 3 && stage < 1) {
                Backup.saveRescue(ctx)
                Backup.resetLayout(ctx)
                p.edit().putInt("safe_stage", 1).putInt("safe_notice", 1).commit()
            }
        } catch (e: Exception) {
            // starting up matters more than tidying
        }
    }

    /** Call once the launcher has been up for a few seconds: the next start is a normal one. */
    fun healthy(ctx: Context) {
        prefs(ctx).edit().putInt("boot_count", 0).putInt("safe_stage", 0).apply()
    }

    /** 0 for nothing to report, 1 after a layout reset, 2 after the look was reset as well. */
    fun notice(ctx: Context): Int = prefs(ctx).getInt("safe_notice", 0)

    fun clearNotice(ctx: Context) {
        prefs(ctx).edit().remove("safe_notice").apply()
    }
}

/** A one-button message box. */
@Composable
fun NoticeDialog(title: String, message: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                ButtonRow { HoloButton("OK") { onClose() } }
            }
        }
    }
}

/** The monthly nudge to make a backup. */
@Composable
fun BackupReminderDialog(onBackup: () -> Unit, onLater: () -> Unit, onStop: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TIME FOR A BACKUP?", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Text(
                    "It has been a while since you saved a backup of your layout, notes, folders and colours. It takes a moment, and the file stays wherever you put it. Later asks again in a week. Stop turns reminders off, and you can turn them back on in Settings.",
                    style = MaterialTheme.typography.bodyMedium, color = Holo.Text,
                )
                ButtonRow {
                    HoloButton("Back up") { onBackup() }
                    HoloButton("Later") { onLater() }
                    HoloButton("Stop") { onStop() }
                }
            }
        }
    }
}

/** A themed yes/no box used before anything that cannot be undone. */
@Composable
fun ConfirmDialog(title: String, message: String, action: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                ButtonRow {
                    HoloButton(action) { onConfirm() }
                    HoloButton("Cancel") { onCancel() }
                }
            }
        }
    }
}
