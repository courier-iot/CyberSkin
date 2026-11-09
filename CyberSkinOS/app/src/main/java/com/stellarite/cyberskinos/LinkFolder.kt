package com.stellarite.cyberskinos

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject

/** One saved web link in a Link folder widget. */
data class WebLink(val title: String, val url: String)

internal fun encodeLinks(list: List<WebLink>): String = JSONArray().also { a ->
    list.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url)) }
}.toString()

internal fun decodeLinks(s: String?): List<WebLink> {
    if (s == null) return emptyList()
    return try {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val u = o.optString("u")
            if (u.isBlank()) null else WebLink(o.optString("t", u), u)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** Adds https:// when it is missing and rejects anything that does not look like a web address. */
private fun normalizeUrl(raw: String): String? {
    val s = raw.trim()
    if (s.isEmpty() || s.any { it.isWhitespace() }) return null
    val withScheme = if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) s else "https://$s"
    val host = Uri.parse(withScheme).host ?: return null
    return if (host.contains('.') || host == "localhost") withScheme else null
}

private fun hostOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

private fun openLink(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app could open that link.", Toast.LENGTH_SHORT).show()
    }
}

private val TILE_COLOURS = listOf(
    Color(0xFF3A7BD5), Color(0xFF2E8B57), Color(0xFFC2185B),
    Color(0xFF8E5BD6), Color(0xFFD9822B), Color(0xFF1F8A99),
)

/** A ring of light holding the first letter of a link's title, in a colour picked from the title. */
@Composable
private fun LetterPort(link: WebLink, diameter: Dp) {
    val tint = TILE_COLOURS[(link.title.hashCode() and 0x7fffffff) % TILE_COLOURS.size]
    val letter = (link.title.firstOrNull { it.isLetterOrDigit() } ?: '#').uppercaseChar().toString()
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            drawCircle(Holo.Hull, r * 0.9f, c)
            drawCircle(Holo.Line, r * 0.9f, c, style = Stroke(r * 0.05f))
            drawCircle(tint.copy(alpha = 0.85f), r * 0.66f, c)
        }
        Text(letter, fontSize = (diameter.value * 0.34f).sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Folded view on the home screen: a small 2x2 preview of the first links plus the folder's name. Tap to open. */
@Composable
fun LinksContent(name: String, links: List<WebLink>, onOpen: () -> Unit) =
    BoxWithConstraints(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
        val side = maxOf(minOf(maxWidth, maxHeight - 28.dp), 40.dp)
        val cell = (side - 18.dp) / 2
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(side).clip(shape)
                    .background(Holo.Void.copy(alpha = 0.6f))
                    .border(1.5.dp, Holo.Dim, shape)
                    .padding(6.dp),
            ) {
                if (links.isEmpty()) {
                    Text("No links", fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        links.take(4).chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { LetterPort(it, cell) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                name, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
                color = Holo.Bright, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

@Composable
private fun LinkRow(link: WebLink, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Holo.Void.copy(alpha = 0.6f))
            .border(1.dp, Holo.Dim, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(12.dp)) {
            Text(link.title, fontSize = 16.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Bright, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hostOf(link.url), fontSize = 13.sp, fontFamily = Holo.font, color = Holo.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("✎", Modifier.clickable(onClick = onEdit).padding(horizontal = 10.dp, vertical = 14.dp), color = Holo.Bright, fontSize = 20.sp)
        Text("✕", Modifier.clickable(onClick = onDelete).padding(horizontal = 12.dp, vertical = 14.dp), color = Holo.Alert, fontSize = 20.sp)
    }
}

/** The opened Link folder: your links, with Add link beside Close. Tap a link to open it in your browser. */
@Composable
fun LinksDialog(id: Long, board: WidgetBoard, onEdit: (Int) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val name = board.linkNames[id] ?: "Links"
    val links = board.linkItems[id].orEmpty()
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(name) }

    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (renaming) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true, colors = holoFieldColors())
                    } else {
                        Text(
                            name, fontSize = 22.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Bright,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HoloButton(if (renaming) "Save" else "Rename", diameter = 76.dp) {
                        if (renaming) {
                            board.renameLinks(id, draft.trim().ifEmpty { "Links" })
                        } else {
                            draft = name
                        }
                        renaming = !renaming
                    }
                }
                if (links.isEmpty()) {
                    Text(
                        "No links yet. Tap Add link to save your first one.",
                        fontSize = 15.sp, fontFamily = Holo.font, color = Holo.Muted,
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        links.forEachIndexed { i, link ->
                            LinkRow(
                                link,
                                onOpen = { openLink(ctx, link.url); onClose() },
                                onEdit = { onEdit(i) },
                                onDelete = { board.removeLink(id, i) },
                            )
                        }
                    }
                }
                ButtonRow {
                    HoloButton("Add link") { onEdit(-1) }
                    HoloButton("Close", onClick = onClose)
                }
            }
        }
    }
}

/** Add a new link (index -1) or edit an existing one. */
@Composable
fun LinkEditDialog(board: WidgetBoard, folder: Long, index: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val existing = board.linkItems[folder].orEmpty().getOrNull(index)
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }

    Dialog(onDismissRequest = onDone) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (existing == null) "ADD LINK" else "EDIT LINK",
                    fontSize = 22.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = Holo.Bright,
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Title (optional)") }, colors = holoFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Web address, such as example.com") }, colors = holoFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
                ButtonRow {
                    HoloButton("Paste") {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val pasted = clip.getItemAt(0).text?.toString()
                            if (pasted != null) url = pasted.trim()
                        }
                    }
                    HoloButton("Save") {
                        val clean = normalizeUrl(url)
                        if (clean == null) {
                            Toast.makeText(ctx, "That does not look like a web address.", Toast.LENGTH_LONG).show()
                        } else {
                            val link = WebLink(title.trim().ifEmpty { hostOf(clean) }, clean)
                            if (existing == null) board.addLink(folder, link) else board.updateLink(folder, index, link)
                            onDone()
                        }
                    }
                    HoloButton("Cancel", onClick = onDone)
                }
            }
        }
    }
}
