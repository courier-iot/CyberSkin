package com.stellarite.cyberskinos

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Sound effects and pulse rings. Both are off until you switch them on in Settings > Look.
 *
 * The sounds are short files kept in the app's assets/sounds folder. If a file is missing the effect is simply silent,
 * so nothing can go wrong. They use the system's own sound volume, so silent mode silences them too.
 */
object Sfx {
    enum class Kind(val file: String) {
        TAP("tap.wav"),           // opening an app
        OPEN("open.wav"),         // the star, opening the Databank
        MENU("menu.wav"),         // the Console menu
        ARRANGE("arrange.wav"),   // entering Arrange mode
        DROP("drop.wav"),         // putting a widget down
        SWIPE("swipe.wav"),       // changing page
        CHIME("chime.wav"),       // something saved or applied
    }

    var enabled by mutableStateOf(false)
        private set
    var pulses by mutableStateOf(false)
        private set
    var volumePct by mutableIntStateOf(65)
        private set

    private var pool: SoundPool? = null
    private val ids = HashMap<Kind, Int>()
    private val ready = HashSet<Int>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cyberskinos", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        enabled = p.getBoolean("sfx_on", false)
        pulses = p.getBoolean("sfx_pulses", false)
        volumePct = p.getInt("sfx_vol", 65).coerceIn(10, 100)
        if (enabled) start(ctx)
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        enabled = on
        prefs(ctx).edit().putBoolean("sfx_on", on).apply()
        if (on) {
            start(ctx)
            play(Kind.OPEN) // so you can hear that it works
        } else {
            stop()
        }
    }

    fun setPulses(ctx: Context, on: Boolean) {
        pulses = on
        prefs(ctx).edit().putBoolean("sfx_pulses", on).apply()
    }

    fun setVolume(ctx: Context, pct: Int) {
        volumePct = pct.coerceIn(10, 100)
        prefs(ctx).edit().putInt("sfx_vol", volumePct).apply()
        play(Kind.TAP)
    }

    private fun start(ctx: Context) {
        if (pool != null) return
        try {
            val sp = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            sp.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) ready.add(sampleId) }
            for (k in Kind.values()) {
                try {
                    ctx.applicationContext.assets.openFd("sounds/" + k.file).use { fd -> ids[k] = sp.load(fd, 1) }
                } catch (e: Exception) {
                    // this sound file is not there, so that effect stays silent
                }
            }
            pool = sp
        } catch (e: Exception) {
            pool = null
        }
    }

    private fun stop() {
        try {
            pool?.release()
        } catch (e: Exception) {
            // nothing to do
        }
        pool = null
        ids.clear()
        ready.clear()
    }

    fun play(kind: Kind) {
        if (!enabled) return
        val id = ids[kind] ?: return
        if (id !in ready) return
        val v = volumePct / 100f
        try {
            pool?.play(id, v, v, 1, 0, 1f)
        } catch (e: Exception) {
            // a missed click is not worth a crash
        }
    }
}

/**
 * Three rings of light that spread out from the star and fade, like a sonar ping. Plays each time [key] changes,
 * if pulse rings are switched on and motion is allowed.
 */
@Composable
fun PulseRings(key: Int, modifier: Modifier = Modifier) {
    val animate = LocalAnimations.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key > 0 && Sfx.pulses && animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1100, easing = LinearEasing))
        }
    }
    val p = progress.value
    if (p >= 1f) return
    val glow = Holo.signal.glow
    Canvas(modifier.size(72.dp)) {
        val r0 = size.width / 2f
        for (i in 0 until 3) {
            val q = ((p - i * 0.18f) / 0.64f).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) continue
            val a = (1f - q) * 0.8f
            drawCircle(glow.copy(alpha = a), r0 * (1f + q * 1.6f), center, style = Stroke((3f - 2f * q).dp.toPx()))
        }
    }
}
