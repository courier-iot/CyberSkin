package com.stellarite.cyberskinos

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * One complete holographic colour scheme.
 * void: the deep space behind everything. hull and deck: the panel glass, darker and lighter.
 * line, bright and dim: the projected light that draws frames, buttons and dials.
 * alert and ok: warning and all-clear colours. text, onAccent and muted: the three text colours.
 */
class Palette(
    val name: String,
    val void: Color, val hull: Color, val deck: Color,
    val line: Color, val bright: Color, val dim: Color,
    val alert: Color, val ok: Color,
    val text: Color, val onAccent: Color, val muted: Color,
)

val Palettes = listOf(
    // Stellarite: the company colours, teal light with lime highlights on deep space
    Palette(
        "Stellarite",
        Color(0xFF060919), Color(0xFF0B1226), Color(0xFF111C36),
        Color(0xFF00B3A6), Color(0xFF7FF5E8), Color(0xFF00665F),
        Color(0xFFFF4D6A), Color(0xFFC4FF00),
        Color(0xFFE8F4FA), Color(0xFF04111A), Color(0xFF8FA6B8),
    ),
    Palette(
        "Cobalt",
        Color(0xFF050A18), Color(0xFF0A1530), Color(0xFF10204A),
        Color(0xFF3F8CFF), Color(0xFFA8D2FF), Color(0xFF1D4290),
        Color(0xFFFF5470), Color(0xFF4FE0C0),
        Color(0xFFE6F0FF), Color(0xFF050B1A), Color(0xFF8EA2C4),
    ),
    Palette(
        "Nebula",
        Color(0xFF0B0718), Color(0xFF150E2C), Color(0xFF201642),
        Color(0xFF9B6BFF), Color(0xFFD6C2FF), Color(0xFF4A2E8A),
        Color(0xFFFF5C8A), Color(0xFF6FE6C8),
        Color(0xFFF0EAFF), Color(0xFF0D0820), Color(0xFFA79CC4),
    ),
    Palette(
        "Pulsar",
        Color(0xFF14070F), Color(0xFF22101C), Color(0xFF33182A),
        Color(0xFFFF5FA8), Color(0xFFFFC2DD), Color(0xFF8A2A58),
        Color(0xFFFF6B4A), Color(0xFF5FE0C8),
        Color(0xFFFFEAF3), Color(0xFF1A0610), Color(0xFFC49EB2),
    ),
    Palette(
        "Solar",
        Color(0xFF120C04), Color(0xFF1E1508), Color(0xFF2C1F0C),
        Color(0xFFFFA62B), Color(0xFFFFD999), Color(0xFF8A5310),
        Color(0xFFFF4D4D), Color(0xFF6FE0B0),
        Color(0xFFFFF3E0), Color(0xFF1A1004), Color(0xFFC4B08E),
    ),
    Palette(
        "Ice",
        Color(0xFF060D12), Color(0xFF0C1A22), Color(0xFF132834),
        Color(0xFF5FE6FF), Color(0xFFC8F7FF), Color(0xFF1F6A80),
        Color(0xFFFF5F6F), Color(0xFF9CFF7A),
        Color(0xFFEAFBFF), Color(0xFF04141A), Color(0xFF92B4C0),
    ),
    Palette(
        "Biolume",
        Color(0xFF070C05), Color(0xFF0E1A0A), Color(0xFF162810),
        Color(0xFFA8E600), Color(0xFFE2FF8A), Color(0xFF4F7000),
        Color(0xFFFF5470), Color(0xFF00D1C1),
        Color(0xFFF0FFE0), Color(0xFF0A1204), Color(0xFFA4B892),
    ),
    Palette(
        "Graphite",
        Color(0xFF08090B), Color(0xFF101216), Color(0xFF181B21),
        Color(0xFFB8C2CC), Color(0xFFF2F5F8), Color(0xFF4A535C),
        Color(0xFFFF5A5A), Color(0xFF5FD8C0),
        Color(0xFFF2F5F8), Color(0xFF0A0C0E), Color(0xFF97A2AD),
    ),
)

/** The signal colour: the glowing light of the clock digits, needles, edges and the accent bar. */
class Signal(val name: String, val lit: Color, val glow: Color)

val Signals = listOf(
    Signal("Teal", Color(0xFF7FFFF2), Color(0xFF00C2B3)),
    Signal("Lime", Color(0xFFE4FF7A), Color(0xFFB8F000)),
    Signal("Cyan", Color(0xFFA8F4FF), Color(0xFF1FC8FF)),
    Signal("Blue", Color(0xFFA9C8FF), Color(0xFF3D7BFF)),
    Signal("Violet", Color(0xFFD8B8FF), Color(0xFF8A4DFF)),
    Signal("Magenta", Color(0xFFFFA8E6), Color(0xFFFF2DB4)),
    Signal("Amber", Color(0xFFFFD98A), Color(0xFFFF9F1A)),
    Signal("White", Color(0xFFF4FAFF), Color(0xFFB8D8FF)),
)

/** Everything about how CyberSkinOS looks. Every screen reads these, so a change repaints everything straight away. */
object Holo {
    var palette by mutableStateOf(Palettes[0])

    /** The glow colour of the clock digits, needles and edges. */
    var signal by mutableStateOf(Signals[0])

    // ----- your own colours -----

    var customLine by mutableStateOf(Color(0xFF00B3A6))
    var customDeck by mutableStateOf(Color(0xFF111C36))
    var customSignal by mutableStateOf(Color(0xFF00C2B3))

    /** Builds a whole palette from two colours you mix: the light of the frames and the glass of the panels. */
    private fun customPalette(line: Color, deck: Color) = Palette(
        "Custom",
        void = lerp(deck, Color.Black, 0.72f), hull = lerp(deck, Color.Black, 0.38f), deck = deck,
        line = line, bright = lerp(line, Color.White, 0.5f), dim = lerp(line, Color.Black, 0.55f),
        alert = Color(0xFFFF4D6A), ok = Color(0xFFC4FF00),
        text = Color(0xFFE8F4FA), onAccent = lerp(deck, Color.Black, 0.8f), muted = Color(0xFF8FA6B8),
    )

    private fun customSignalOf(c: Color) = Signal("Custom", lerp(c, Color.White, 0.45f), c)

    /** Live update while you slide. Call [saveCustom] when you let go. */
    fun applyCustomPalette(line: Color, deck: Color) {
        customLine = line
        customDeck = deck
        palette = customPalette(line, deck)
    }

    fun applyCustomSignal(c: Color) {
        customSignal = c
        signal = customSignalOf(c)
    }

    fun saveCustom(ctx: Context) {
        prefs(ctx).edit()
            .putInt("cust_line", customLine.toArgb()).putInt("cust_deck", customDeck.toArgb()).putInt("cust_signal", customSignal.toArgb())
            .putString("theme", palette.name).putString("signal", signal.name).apply()
    }

    /** The colours as they are right now, for saving in a preset. */
    fun currentColors() = LookColors(STYLE, palette.name, signal.name, customLine.toArgb(), customDeck.toArgb(), customSignal.toArgb())

    /** Applies the colours from a preset. Your own custom colours are only replaced if the preset uses them. */
    fun applyColors(ctx: Context, c: LookColors) {
        if (c.metal == "Custom") { customLine = Color(c.customMetal); customDeck = Color(c.customWood) }
        if (c.nixie == "Custom") customSignal = Color(c.customNeon)
        palette = if (c.metal == "Custom") customPalette(customLine, customDeck) else Palettes.firstOrNull { it.name == c.metal } ?: palette
        signal = if (c.nixie == "Custom") customSignalOf(customSignal) else Signals.firstOrNull { it.name == c.nixie } ?: signal
        saveCustom(ctx)
    }

    /** CyberSkinOS has one style. Kept in presets so the format can grow later. */
    const val STYLE = "holo"

    fun cleanStyle(@Suppress("UNUSED_PARAMETER") key: String?): String = STYLE

    /** Multiplies how long a ring takes to turn: 1.8 is slow, 1 normal, 0.5 fast. */
    var spin by mutableFloatStateOf(1f)

    /** "hud" (clean sans), "mono" (terminal), "serif" or "custom" (a font file you uploaded). */
    var fontKey by mutableStateOf("hud")
    private var custom by mutableStateOf<FontFamily?>(null)
    val hasCustomFont: Boolean get() = custom != null

    /** A picture you chose, drawn behind the widgets and the Databank. */
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set
    val hasWallpaper: Boolean get() = wallpaper != null

    /** How much the wallpaper is darkened, 0 to 0.85. */
    var wallDim by mutableFloatStateOf(0.45f)

    /** Holo icons: app icons are drawn in the light of your palette instead of their own colours. */
    var tintIcons by mutableStateOf(false)

    fun setTintIcons(ctx: Context, on: Boolean) {
        tintIcons = on
        prefs(ctx).edit().putBoolean("tint_icons", on).apply()
    }

    /** The font used for all text. Reading it repaints everything when you change it. */
    val font: FontFamily
        get() = when (fontKey) {
            "mono" -> FontFamily.Monospace
            "serif" -> FontFamily.Serif
            "custom" -> custom ?: FontFamily.SansSerif
            else -> FontFamily.SansSerif
        }

    val Void: Color get() = palette.void
    val Hull: Color get() = palette.hull
    val Deck: Color get() = palette.deck
    val Line: Color get() = palette.line
    val Bright: Color get() = palette.bright
    val Dim: Color get() = palette.dim
    val Alert: Color get() = palette.alert
    val Ok: Color get() = palette.ok
    val Text: Color get() = palette.text
    val OnAccent: Color get() = palette.onAccent
    val Muted: Color get() = palette.muted

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cyberskinos", Context.MODE_PRIVATE)

    /** Call once at start-up, before anything is drawn. */
    fun load(ctx: Context) {
        val p = prefs(ctx)
        customLine = Color(p.getInt("cust_line", customLine.toArgb()))
        customDeck = Color(p.getInt("cust_deck", customDeck.toArgb()))
        customSignal = Color(p.getInt("cust_signal", customSignal.toArgb()))
        val themeName = p.getString("theme", "Stellarite")
        palette = if (themeName == "Custom") customPalette(customLine, customDeck) else Palettes.firstOrNull { it.name == themeName } ?: Palettes[0]
        spin = p.getFloat("spin", 1f)
        val signalName = p.getString("signal", "Teal")
        signal = if (signalName == "Custom") customSignalOf(customSignal) else Signals.firstOrNull { it.name == signalName } ?: Signals[0]
        fontKey = p.getString("font", "hud") ?: "hud"
        readCustomFont(ctx)
        if (fontKey == "custom" && custom == null) fontKey = "hud"
        if (fontKey !in setOf("hud", "mono", "serif", "custom")) fontKey = "hud"
        wallDim = p.getFloat("wall_dim", 0.45f)
        tintIcons = p.getBoolean("tint_icons", false)
        readWallpaper(ctx)
        readPageWallpapers(ctx)
    }

    // ----- wallpaper -----

    private fun wallFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    private fun readWallpaper(ctx: Context) {
        val f = wallFile(ctx)
        wallpaper = if (f.exists()) {
            try { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (e: Exception) { null }
        } else {
            null
        }
    }

    /** Reads a picture, shrinks it to a sensible size and fixes its rotation. Null if it cannot be used. */
    private fun decodePicture(ctx: Context, uri: Uri): Bitmap? {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val turn = try {
                cr.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            if (turn != 0f) {
                val m = Matrix().apply { postRotate(turn) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    /** Shrinks the picture to a sensible size, fixes its rotation, and keeps a private copy. */
    fun installWallpaper(ctx: Context, uri: Uri): Boolean {
        val bmp = decodePicture(ctx, uri) ?: return false
        return try {
            wallFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            wallpaper = bmp.asImageBitmap()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ----- a wallpaper for one page only -----

    /** Pages that have a picture of their own. Pages without one show the main wallpaper. */
    val pageWallpapers = mutableStateMapOf<Int, ImageBitmap>()

    private const val PAGE_WALL_LIMIT = 16

    private fun pageWallFile(ctx: Context, page: Int) = File(ctx.filesDir, "wallpaper_p$page.jpg")

    /** The picture to draw behind a page: its own if it has one, otherwise the main wallpaper. */
    fun wallpaperFor(page: Int): ImageBitmap? = pageWallpapers[page] ?: wallpaper

    fun hasPageWallpaper(page: Int) = pageWallpapers.containsKey(page)

    private fun readPageWallpapers(ctx: Context) {
        pageWallpapers.clear()
        for (p in 0 until PAGE_WALL_LIMIT) {
            val f = pageWallFile(ctx, p)
            if (!f.exists()) continue
            try {
                BitmapFactory.decodeFile(f.path)?.let { pageWallpapers[p] = it.asImageBitmap() }
            } catch (e: Exception) {
                // an unreadable picture is skipped
            }
        }
    }

    fun installPageWallpaper(ctx: Context, page: Int, uri: Uri): Boolean {
        val bmp = decodePicture(ctx, uri) ?: return false
        return try {
            pageWallFile(ctx, page).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            pageWallpapers[page] = bmp.asImageBitmap()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removePageWallpaper(ctx: Context, page: Int) {
        pageWallFile(ctx, page).delete()
        pageWallpapers.remove(page)
    }

    /** Two pages swapped places, so their pictures swap too. */
    fun swapPageWallpapers(ctx: Context, a: Int, b: Int) {
        val fa = pageWallFile(ctx, a)
        val fb = pageWallFile(ctx, b)
        val tmp = File(ctx.filesDir, "wallpaper_swap.tmp")
        val hadA = fa.exists()
        val hadB = fb.exists()
        try {
            if (hadA) fa.copyTo(tmp, overwrite = true)
            if (hadB) fb.copyTo(fa, overwrite = true) else fa.delete()
            if (hadA) tmp.copyTo(fb, overwrite = true) else fb.delete()
            tmp.delete()
        } catch (e: Exception) {
            // leave whatever was copied
        }
        readPageWallpapers(ctx)
    }

    /** A page was removed: its picture goes and the later pages' pictures move down one. */
    fun closeGapAfterPage(ctx: Context, gone: Int, pageCountBefore: Int) {
        pageWallFile(ctx, gone).delete()
        try {
            for (p in gone + 1 until pageCountBefore) {
                val f = pageWallFile(ctx, p)
                if (f.exists()) f.copyTo(pageWallFile(ctx, p - 1), overwrite = true) else pageWallFile(ctx, p - 1).delete()
            }
            pageWallFile(ctx, pageCountBefore - 1).delete()
        } catch (e: Exception) {
            // leave whatever was copied
        }
        readPageWallpapers(ctx)
    }

    private val presetWall = Regex("pw_\\d+\\.jpg")

    /** Keeps a private copy of the current wallpaper for a preset. Returns the copy's name, or null if there is none. */
    fun copyWallpaperTo(ctx: Context, name: String): String? = try {
        val src = wallFile(ctx)
        if (!src.exists() || !presetWall.matches(name)) {
            null
        } else {
            src.copyTo(File(ctx.filesDir, name), overwrite = true)
            name
        }
    } catch (e: Exception) {
        null
    }

    /** Makes a preset's saved picture the wallpaper. Returns false if the picture is missing. */
    fun useWallpaperFile(ctx: Context, name: String): Boolean = try {
        val f = File(ctx.filesDir, name)
        if (!presetWall.matches(name) || !f.exists()) {
            false
        } else {
            f.copyTo(wallFile(ctx), overwrite = true)
            readWallpaper(ctx)
            wallpaper != null
        }
    } catch (e: Exception) {
        false
    }

    /** Deletes saved preset pictures that no preset uses any more. */
    fun sweepPresetWallpapers(ctx: Context, keep: Set<String>) {
        ctx.filesDir.listFiles()?.filter { presetWall.matches(it.name) && it.name !in keep }?.forEach { it.delete() }
    }

    fun removeWallpaper(ctx: Context) {
        wallFile(ctx).delete()
        wallpaper = null
    }

    fun setDim(ctx: Context, v: Float) {
        wallDim = v
        prefs(ctx).edit().putFloat("wall_dim", v).apply()
    }

    fun chooseSignal(ctx: Context, s: Signal) {
        signal = s
        prefs(ctx).edit().putString("signal", s.name).apply()
    }

    fun choose(ctx: Context, pal: Palette) {
        palette = pal
        prefs(ctx).edit().putString("theme", pal.name).apply()
    }

    private fun customFile(ctx: Context) = File(ctx.filesDir, "custom_font")

    private fun readCustomFont(ctx: Context) {
        val f = customFile(ctx)
        custom = if (f.exists()) {
            try { FontFamily(Typeface.createFromFile(f)) } catch (e: Exception) { null }
        } else {
            null
        }
    }

    fun chooseFont(ctx: Context, key: String) {
        fontKey = key
        prefs(ctx).edit().putString("font", key).apply()
    }

    private fun looksLikeFont(b: ByteArray): Boolean {
        val tag = String(b, Charsets.ISO_8859_1)
        val ttf = b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0
        return ttf || tag == "true" || tag == "OTTO" || tag == "ttcf"
    }

    /** Copies a .ttf or .otf you picked into the app's private storage. Returns false if it is not a font. */
    fun installCustomFont(ctx: Context, uri: Uri): Boolean {
        return try {
            val tmp = File(ctx.filesDir, "custom_font.tmp")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return false
            val head = ByteArray(4)
            val sizeOk = tmp.length() in 1_000L..25_000_000L
            val magicOk = sizeOk && tmp.inputStream().use { it.read(head) == 4 } && looksLikeFont(head)
            if (!magicOk) {
                tmp.delete()
                return false
            }
            tmp.copyTo(customFile(ctx), overwrite = true)
            tmp.delete()
            readCustomFont(ctx)
            if (custom == null) return false
            chooseFont(ctx, "custom")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeCustomFont(ctx: Context) {
        customFile(ctx).delete()
        custom = null
        if (fontKey == "custom") chooseFont(ctx, "hud")
    }

    fun setSpin(ctx: Context, factor: Float) {
        spin = factor
        prefs(ctx).edit().putFloat("spin", factor).apply()
    }
}

val LocalAnimations = compositionLocalOf { true }

/** False while the home screen is in the background, so sensors and timers can sleep. */
val LocalActive = compositionLocalOf { true }

private fun holoTypography(font: FontFamily): Typography {
    val base = Typography()
    // Line heights are relative (em) so they shrink with the text and small labels are never clipped.
    // Headings get wide letter spacing, like the read-outs on a ship's console.
    return Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 3.sp, lineHeight = 1.2.em),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, lineHeight = 1.2.em),
        titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.6.sp, lineHeight = 1.25.em),
        bodyLarge = base.bodyLarge.copy(fontFamily = font, fontSize = 16.sp, lineHeight = 1.3.em),
        bodyMedium = base.bodyMedium.copy(fontFamily = font, lineHeight = 1.3.em),
        labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, lineHeight = 1.25.em),
    )
}

@Composable
fun HoloTheme(content: @Composable () -> Unit) {
    // keep the status and navigation bars the colour of deep space
    val view = LocalView.current
    val bar = Holo.Void.toArgb()
    SideEffect {
        val window = (view.context as? Activity)?.window
        window?.statusBarColor = bar
        window?.navigationBarColor = bar
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Holo.Line, onPrimary = Holo.OnAccent,
            secondary = Holo.Ok, background = Holo.Void,
            surface = Holo.Hull, onSurface = Holo.Text, onBackground = Holo.Text,
        ),
        typography = remember(Holo.font) { holoTypography(Holo.font) },
        content = content,
    )
}
