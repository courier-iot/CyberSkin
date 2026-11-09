package com.stellarite.cyberskinos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Having this service declared in the manifest is what lets Android list CyberSkinOS under
 * Notification access. It does nothing itself: it only gives us the right to read which track is playing.
 */
class MediaListenerService : NotificationListenerService()

private fun listenerEnabled(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
}

/** Opens Android's Notification access screen, where CyberSkinOS can be switched on. */
internal fun openListenerSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Open Android Settings, then Notifications, then Notification access", Toast.LENGTH_LONG).show()
    }
}

/** Play, pause and skip work for any music app and need no permission at all. */
private fun sendMediaKey(ctx: Context, code: Int) {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
}

/** The name of an app, or its package name if that cannot be found. */
private fun appLabelOf(ctx: Context, pkg: String): String = try {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    pkg
}

/** The music apps that have a player running right now (package, name). Needs Notification access, otherwise empty. */
internal fun activePlayers(ctx: Context): List<Pair<String, String>> = try {
    if (!listenerEnabled(ctx)) {
        emptyList()
    } else {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
            .map { it.packageName to appLabelOf(ctx, it.packageName) }.distinctBy { it.first }
    }
} catch (e: Exception) {
    emptyList()
}

/** The name to show for a player package chosen earlier. */
internal fun playerName(ctx: Context, pkg: String): String = appLabelOf(ctx, pkg)

/** Opens the player that is being shown, on its own now-playing screen if it offers one. Otherwise the phone's music app. */
private fun openPlayer(ctx: Context, st: MusicState) {
    try {
        val pi = st.controller?.sessionActivity
        if (pi != null) {
            pi.send()
            return
        }
    } catch (e: Exception) {
        // try the next way
    }
    try {
        val launch = if (st.pkg.isNotBlank()) ctx.packageManager.getLaunchIntentForPackage(st.pkg) else null
        val intent = launch ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No music app found", Toast.LENGTH_SHORT).show()
    }
}

/** What the player knows right now. The track details and progress need Notification access. */
private class MusicState {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var playing by mutableStateOf(false)
    var progress by mutableFloatStateOf(-1f) // 0 to 1, or -1 when unknown
    var access by mutableStateOf(false)
    var pkg by mutableStateOf("")                 // the app whose player is being shown
    var controller: MediaController? = null       // used to send play, pause and skip to that very player

    /** [follow] is the package of the player to show, or blank to take whichever is playing. */
    fun refresh(ctx: Context, follow: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var ctl: MediaController? = null
        access = listenerEnabled(ctx)
        if (access) {
            try {
                val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val list = msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
                ctl = if (follow.isNotBlank()) {
                    list.firstOrNull { it.packageName == follow }
                } else {
                    list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
                }
            } catch (e: SecurityException) {
                access = false
            }
        }
        controller = ctl
        pkg = ctl?.packageName ?: ""
        if (ctl == null) {
            title = ""
            artist = ""
            progress = -1f
            playing = if (follow.isNotBlank() && access) false else am.isMusicActive
            return
        }
        val md = ctl.metadata
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val ps = ctl.playbackState
        playing = ps?.state == PlaybackState.STATE_PLAYING
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        progress = if (ps != null && dur > 0L) {
            var pos = ps.position
            if (playing) pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
            (pos.toFloat() / dur).coerceIn(0f, 1f)
        } else {
            -1f
        }
    }
}

/**
 * The Audio deck: a holographic data disc with working controls. Wide and short it becomes a strip, taller and
 * narrower it becomes a square card, so resize the widget to change its shape. It glows in your signal colour.
 */
@Composable
fun MusicContent(follow: String = "") {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val scope = rememberCoroutineScope()
    val st = remember { MusicState() }

    LaunchedEffect(active, follow) {
        if (!active) return@LaunchedEffect
        while (true) {
            st.refresh(ctx, follow)
            delay(if (st.playing) 1000L else 2500L)
        }
    }

    val lit = Holo.signal.lit
    val trackKey = st.title + "|" + st.artist
    // a fresh random tint for the disc every time the track changes
    val tint = remember(trackKey) { if (trackKey == "|") lit else Color(android.graphics.Color.HSVToColor(floatArrayOf(Random.nextFloat() * 360f, 0.6f, 0.95f))) }

    // With a player chosen, the buttons talk to that player. Otherwise they go to whichever app Android treats as current.
    val press: (Int) -> Unit = { code ->
        val tc = if (follow.isNotBlank()) st.controller?.transportControls else null
        if (tc != null) {
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
                KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
                else -> if (st.playing) tc.pause() else tc.play()
            }
        } else {
            sendMediaKey(ctx, code)
        }
        scope.launch {
            delay(350)
            st.refresh(ctx, follow)
        }
    }
    val onPrev = { press(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
    val onPlay = { press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
    val onNext = { press(KeyEvent.KEYCODE_MEDIA_NEXT) }
    val onAllow = { openListenerSettings(ctx) }
    val onOpen = { openPlayer(ctx, st) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mw = maxWidth
        val mh = maxHeight
        val strip = mw >= 230.dp && mw > mh * 1.5f
        if (strip) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                DiscArt(Modifier.size(minOf(mh, mw * 0.3f)).clickable(onClick = onOpen), st.playing, tint)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    TrackText(st, lit, true, false, 16f, onAllow, onOpen)
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(st.progress, lit)
                    Spacer(Modifier.height(6.dp))
                    ControlsRow(st.playing, Arrangement.Start, onPrev, onPlay, onNext)
                }
            }
        } else {
            val tall = mh >= 125.dp
            val textH = if (tall) 36.dp else 20.dp
            val art = maxOf(minOf(mh - 28.dp - textH - 4.dp - 18.dp, mw), 30.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                DiscArt(Modifier.size(art).clickable(onClick = onOpen), st.playing, tint)
                TrackText(st, lit, tall, true, 14f, onAllow, onOpen)
                ProgressLine(st.progress, lit)
                ControlsRow(st.playing, Arrangement.Center, onPrev, onPlay, onNext)
            }
        }
    }
}

@Composable
private fun TrackText(st: MusicState, lit: Color, showArtist: Boolean, center: Boolean, size: Float, onAllow: () -> Unit, onOpen: () -> Unit) {
    val glow = Holo.signal.glow
    val align = if (center) TextAlign.Center else TextAlign.Start
    val head: String
    val sub: String
    when {
        !st.access -> { head = "Now playing"; sub = "" }
        st.title.isBlank() -> { head = "Nothing playing"; sub = "Press play to start" }
        else -> { head = st.title; sub = st.artist }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            head, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
            modifier = Modifier.clickable(onClick = onOpen),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = size.sp,
                shadow = Shadow(glow.copy(alpha = 0.85f), Offset.Zero, 14f),
            ),
            color = if (st.access) lit else Holo.Text,
        )
        if (!st.access) {
            Text(
                "Tap to show track info", maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
                modifier = Modifier.clickable(onClick = onAllow),
                style = MaterialTheme.typography.labelLarge, fontSize = (size - 3f).coerceAtLeast(11f).sp,
                color = lit, textDecoration = TextDecoration.Underline,
            )
        } else if (showArtist && sub.isNotBlank()) {
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
                style = MaterialTheme.typography.labelLarge, fontSize = (size - 3f).coerceAtLeast(11f).sp,
                color = Holo.Text,
            )
        }
    }
}

/** A thin line of light that fills as the track plays. It only shows progress, it is not a seek bar. */
@Composable
private fun ProgressLine(progress: Float, lit: Color) {
    val shown by animateFloatAsState(progress.coerceAtLeast(0f), tween(900, easing = LinearEasing), label = "track")
    val glow = Holo.signal.glow
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (progress >= 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val y = h / 2f
                drawLine(lit.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = h * 0.5f, cap = StrokeCap.Round)
                if (shown > 0.005f) {
                    val x = maxOf(h, size.width * shown)
                    drawLine(glow.copy(alpha = 0.3f), Offset(0f, y), Offset(x, y), strokeWidth = h, cap = StrokeCap.Round)
                    drawLine(lit, Offset(0f, y), Offset(x, y), strokeWidth = h * 0.5f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun ControlsRow(playing: Boolean, arrangement: Arrangement.Horizontal, onPrev: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        MusicButton(0, playing, 28.dp, onPrev)
        Spacer(Modifier.width(10.dp))
        MusicButton(1, playing, 36.dp, onPlay)
        Spacer(Modifier.width(10.dp))
        MusicButton(2, playing, 28.dp, onNext)
    }
}

/** kind 0 = previous, 1 = play or pause (it shows what a tap will do), 2 = next. */
@Composable
private fun MusicButton(kind: Int, playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val lit = Holo.signal.lit
    val glow = Holo.signal.glow
    Canvas(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick)) {
        val w = this.size.width
        val line = 2.dp.toPx()
        fun neon(path: Path) {
            drawPath(path, glow.copy(alpha = 0.3f), style = Stroke(line * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, lit, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) = Path().apply {
            moveTo(ax * w, ay * w); lineTo(bx * w, by * w); lineTo(cx * w, cy * w); close()
        }
        fun bar(x: Float, y1: Float, y2: Float) = Path().apply { moveTo(x * w, y1 * w); lineTo(x * w, y2 * w) }
        when (kind) {
            0 -> {
                neon(bar(0.28f, 0.3f, 0.7f))
                neon(tri(0.72f, 0.3f, 0.38f, 0.5f, 0.72f, 0.7f))
            }
            2 -> {
                neon(tri(0.28f, 0.3f, 0.62f, 0.5f, 0.28f, 0.7f))
                neon(bar(0.72f, 0.3f, 0.7f))
            }
            else -> {
                // a hexagon around play and pause, like a console key
                neon(Path().apply {
                    for (i in 0 until 6) {
                        val p = polar(Offset(w / 2f, w / 2f), w * 0.46f, i * 60.0 - 90.0)
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                })
                if (playing) {
                    neon(bar(0.4f, 0.33f, 0.67f))
                    neon(bar(0.6f, 0.33f, 0.67f))
                } else {
                    neon(tri(0.42f, 0.32f, 0.7f, 0.5f, 0.42f, 0.68f))
                }
            }
        }
    }
}

/** The holographic data disc. It turns while music plays and its core takes the tint chosen for the current track. */
@Composable
private fun DiscArt(modifier: Modifier, playing: Boolean, tint: Color) {
    val spin = playing && LocalAnimations.current
    val angle: State<Float>? = if (spin) {
        rememberInfiniteTransition(label = "disc").animateFloat(
            0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "spin",
        )
    } else {
        null
    }
    val lit = Holo.signal.lit
    val glow = Holo.signal.glow
    Canvas(modifier) {
        drawDisc(lit, glow, tint, angle?.value ?: 0f)
    }
}

private fun DrawScope.drawDisc(lit: Color, glow: Color, tint: Color, angle: Float) {
    val w = size.width
    val h = size.height
    val rim = 2.dp.toPx()
    val cut = minOf(w, h) * 0.14f
    // a cut-corner frame of light
    val frame = Path().apply {
        moveTo(rim + cut, rim); lineTo(w - rim, rim); lineTo(w - rim, h - rim - cut)
        lineTo(w - rim - cut, h - rim); lineTo(rim, h - rim); lineTo(rim, rim + cut); close()
    }
    drawPath(frame, Holo.Void)
    drawPath(frame, glow.copy(alpha = 0.22f), style = Stroke(rim * 3f))
    drawPath(frame, lit, style = Stroke(rim))

    val c = Offset(w / 2f, h / 2f)
    val r = minOf(w, h) * 0.38f
    drawCircle(tint.copy(alpha = 0.12f), r, c)
    drawCircle(glow.copy(alpha = 0.22f), r, c, style = Stroke(rim * 2.4f))
    drawCircle(lit, r, c, style = Stroke(1.6.dp.toPx()))
    rotate(angle, c) {
        // data tracks: broken rings that turn with the disc
        for ((k, frac) in listOf(0.84f, 0.68f, 0.52f).withIndex()) {
            for (s in 0 until 4) {
                drawArc(
                    lit.copy(alpha = 0.45f), startAngle = s * 90f + k * 25f, sweepAngle = 58f, useCenter = false,
                    topLeft = Offset(c.x - r * frac, c.y - r * frac), size = Size(r * frac * 2f, r * frac * 2f),
                    style = Stroke(1.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        drawCircle(tint, r * 0.3f, c)
        drawCircle(Holo.Void, r * 0.07f, c)
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.045f, Offset(c.x + r * 0.2f, c.y))
    }
}
