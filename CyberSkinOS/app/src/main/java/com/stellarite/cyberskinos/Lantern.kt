package com.stellarite.cyberskinos

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The camera that has the torch, usually the back one. Null if this phone has none. */
private fun torchCamera(cm: CameraManager): String? = try {
    val withFlash = cm.cameraIdList.filter { id ->
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    withFlash.firstOrNull { id ->
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: withFlash.firstOrNull()
} catch (e: Exception) {
    null
}

/** How bright the torch can go: 1 on a phone that cannot dim it, otherwise the number of steps it offers (Android 13 and later). */
internal fun lanternMaxLevel(ctx: Context): Int {
    if (Build.VERSION.SDK_INT < 33) return 1
    return try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = torchCamera(cm) ?: return 1
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } catch (e: Exception) {
        1
    }
}

/** Puts the torch out by itself after a while. It lives outside the screen, so closing the launcher does not forget it. */
private object LanternTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun arm(cm: CameraManager, camId: String, minutes: Int) {
        disarm()
        val r = Runnable {
            try {
                cm.setTorchMode(camId, false)
            } catch (e: Exception) {
                // already off, or the camera is in use
            }
        }
        pending = r
        handler.postDelayed(r, minutes * 60_000L)
    }

    fun disarm() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}

/**
 * The Floodlight widget: tap to switch the phone's torch on or off. It needs no permission. It follows the torch, so
 * if you switch it on from the quick settings panel the floodlight lights up here too.
 */
@Composable
fun LanternContent(autoOffMin: Int = 0, levelPct: Int = 100) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val mw = maxWidth
    val mh = maxHeight
    val cm = remember { ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val camId = remember { torchCamera(cm) }
    var on by remember { mutableStateOf(false) }

    DisposableEffect(camId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camId) on = enabled
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == camId) on = false
            }
        }
        try {
            cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // no torch service
        }
        onDispose {
            try {
                cm.unregisterTorchCallback(callback)
            } catch (e: Exception) {
            }
        }
    }

    // an automatic switch-off follows the torch, however it was switched on
    LaunchedEffect(on, autoOffMin, camId) {
        if (on && autoOffMin > 0 && camId != null) LanternTimer.arm(cm, camId, autoOffMin) else LanternTimer.disarm()
    }

    val maxLevel = remember { lanternMaxLevel(ctx) }
    val toggle = {
        if (camId == null) {
            Toast.makeText(ctx, "This phone has no torch.", Toast.LENGTH_SHORT).show()
        } else {
            try {
                if (!on && maxLevel > 1 && levelPct < 100 && Build.VERSION.SDK_INT >= 33) {
                    val level = (maxLevel * levelPct / 100).coerceIn(1, maxLevel)
                    cm.turnOnTorchWithStrengthLevel(camId, level)
                } else {
                    cm.setTorchMode(camId, !on)
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "The torch is busy. Try again in a moment.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val lamp = minOf(mh, mw * 0.42f)
    val lit = Holo.signal.lit
    val glow = Holo.signal.glow
    val beamColor = Color(0xFFF4FBFF)
    Row(Modifier.fillMaxSize().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(lamp)) {
            val w = size.width
            val h = size.height
            // the emitter: a hexagonal lens on a short mount, shining upwards
            val c = Offset(w * 0.5f, h * 0.68f)
            val r = w * 0.2f
            if (on) {
                val beam = Path().apply {
                    moveTo(c.x - r * 0.8f, c.y - r * 0.4f)
                    lineTo(w * 0.04f, 0f)
                    lineTo(w * 0.96f, 0f)
                    lineTo(c.x + r * 0.8f, c.y - r * 0.4f)
                    close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, beamColor.copy(alpha = 0.55f)), startY = 0f, endY = c.y))
                drawCircle(Brush.radialGradient(listOf(beamColor.copy(alpha = 0.7f), Color.Transparent), center = c, radius = r * 2.4f), r * 2.4f, c)
            }
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val p = polar(c, r, i * 60.0 - 90.0)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(hex, if (on) beamColor else Holo.Void)
            drawPath(hex, glow.copy(alpha = 0.3f), style = Stroke(w * 0.06f))
            drawPath(hex, lit, style = Stroke(w * 0.022f))
            drawCircle(if (on) glow.copy(alpha = 0.6f) else Holo.Dim, r * 0.42f, c, style = Stroke(w * 0.018f))
            // the mount
            drawRect(Holo.Dim, Offset(c.x - r * 0.18f, c.y + r), Size(r * 0.36f, h * 0.12f))
            drawRect(Holo.Line, Offset(c.x - r * 1.1f, h * 0.92f), Size(r * 2.2f, h * 0.05f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Floodlight", fontSize = 16.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
                color = lit, maxLines = 1,
            )
            Text(
                when {
                    camId == null -> "No torch on this phone"
                    on -> "On. Tap to switch off" + (if (autoOffMin > 0) " (goes off after $autoOffMin min)" else "")
                    else -> "Off. Tap to switch on"
                },
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted, maxLines = 2,
            )
        }
    }
}
