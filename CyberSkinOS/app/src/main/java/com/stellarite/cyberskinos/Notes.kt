package com.stellarite.cyberskinos

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** One note: plain text, or a checklist whose lines look like "[ ] milk" and "[x] eggs". */
private class Note(val list: Boolean, val body: String)

private class NoteBook(val notes: List<Note>, val cur: Int)

private const val MAX_NOTES = 12

/** Reads a widget's notes. A single old-style note becomes the first one. */
private fun loadBook(prefs: SharedPreferences, id: Long): NoteBook {
    val raw = prefs.getString("notes_$id", null)
    if (raw != null) {
        try {
            val o = JSONObject(raw)
            val arr = o.getJSONArray("n")
            val list = (0 until arr.length()).map { i ->
                val n = arr.getJSONObject(i)
                Note(n.optBoolean("l", false), n.optString("b", ""))
            }
            if (list.isNotEmpty()) return NoteBook(list, o.optInt("c", 0).coerceIn(0, list.size - 1))
        } catch (e: Exception) {
            // fall through to the old note
        }
    }
    return NoteBook(listOf(Note(false, prefs.getString("note_$id", "") ?: "")), 0)
}

private fun saveBook(prefs: SharedPreferences, id: Long, book: NoteBook) {
    val arr = JSONArray()
    book.notes.forEach { arr.put(JSONObject().put("l", it.list).put("b", it.body)) }
    prefs.edit().putString("notes_$id", JSONObject().put("c", book.cur).put("n", arr).toString()).apply()
}

private class Item(val done: Boolean, val label: String)

private fun parseItems(body: String): List<Item> = body.lines().filter { it.isNotBlank() }.map { line ->
    when {
        line.startsWith("[x] ") -> Item(true, line.substring(4))
        line.startsWith("[ ] ") -> Item(false, line.substring(4))
        else -> Item(false, line)
    }
}

private fun joinItems(items: List<Item>): String = items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.label }

/**
 * The Log. It holds several notes, each either plain text or a tick-box checklist. The small dots-menu in the
 * heading adds a note or a list, changes the one you are on between the two, or deletes it.
 */
@Composable
fun MemoContent(id: Long, prefs: SharedPreferences, editing: Boolean, title: String = "Log") {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var book by remember(id) { mutableStateOf(loadBook(prefs, id)) }
    fun put(b: NoteBook) {
        book = b
        saveBook(prefs, id, b)
    }
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val count = book.notes.size
    val cur = book.cur.coerceIn(0, count - 1)
    val note = book.notes[cur]
    val lit = Holo.signal.lit
    fun replaceCurrent(n: Note) = put(NoteBook(book.notes.mapIndexed { i, old -> if (i == cur) n else old }, cur))
    fun go(to: Int) {
        dropFocus(ctx, view, focus)
        put(NoteBook(book.notes, ((to % count) + count) % count))
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = lit,
                fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (count > 1) {
                Text("‹", color = lit, fontSize = 18.sp, modifier = Modifier.clickable { go(cur - 1) }.padding(horizontal = 6.dp))
                Text("${cur + 1}/$count", color = Holo.Muted, fontSize = 12.sp, maxLines = 1)
                Text("›", color = lit, fontSize = 18.sp, modifier = Modifier.clickable { go(cur + 1) }.padding(horizontal = 6.dp))
            }
            if (focused) {
                Text(
                    "Done", style = MaterialTheme.typography.titleMedium, color = lit, fontSize = 14.sp,
                    modifier = Modifier.clickable { dropFocus(ctx, view, focus) }.padding(horizontal = 6.dp),
                )
            }
            if (!editing) {
                Text("⋯", color = lit, fontSize = 18.sp, modifier = Modifier.clickable { menu = !menu }.padding(horizontal = 6.dp))
            }
        }
        if (menu && !editing) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val small = 12.sp
                if (count < MAX_NOTES) {
                    Text("+ Note", color = lit, fontSize = small, modifier = Modifier.clickable {
                        put(NoteBook(book.notes + Note(false, ""), count)); menu = false
                    })
                    Text("+ Check", color = lit, fontSize = small, modifier = Modifier.clickable {
                        put(NoteBook(book.notes + Note(true, ""), count)); menu = false
                    })
                }
                Text(if (note.list) "Text" else "Check", color = lit, fontSize = small, modifier = Modifier.clickable {
                    if (note.list) {
                        replaceCurrent(Note(false, parseItems(note.body).joinToString("\n") { it.label }))
                    } else {
                        replaceCurrent(Note(true, joinItems(note.body.lines().filter { it.isNotBlank() }.map { Item(false, it.trim()) })))
                    }
                    menu = false
                })
                Text("✕", color = Holo.Alert, fontSize = 16.sp, modifier = Modifier.clickable {
                    if (count > 1) {
                        put(NoteBook(book.notes.filterIndexed { i, _ -> i != cur }, (cur - 1).coerceAtLeast(0)))
                    } else {
                        put(NoteBook(listOf(Note(false, "")), 0))
                    }
                    menu = false
                })
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // a key per note, so each one starts with fresh focus state
            androidx.compose.runtime.key(id, cur) {
                if (note.list) {
                    ChecklistBody(note.body, editing, { replaceCurrent(Note(true, it)) }) { focused = it }
                } else {
                    TextBody(note.body, editing, { replaceCurrent(Note(false, it)) }) { focused = it }
                }
            }
        }
    }
}

@Composable
private fun TextBody(text: String, editing: Boolean, onText: (String) -> Unit, onFocus: (Boolean) -> Unit) {
    // The note can only take focus after you tap it, so it never pops the keyboard open by itself.
    var armed by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(armed) { if (armed) requester.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            enabled = !editing,
            textStyle = TextStyle(color = Holo.Text, fontFamily = Holo.font, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(Holo.signal.lit),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(requester)
                .focusProperties { canFocus = armed }
                .onFocusChanged { onFocus(it.isFocused); if (!it.isFocused) armed = false },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Tap to log a note", style = MaterialTheme.typography.labelLarge, color = Holo.Muted.copy(alpha = 0.6f))
                    inner()
                }
            },
        )
        if (!armed && !editing) {
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { armed = true },
            )
        }
    }
}

@Composable
private fun ChecklistBody(body: String, editing: Boolean, onBody: (String) -> Unit, onFocus: (Boolean) -> Unit) {
    val items = parseItems(body)
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) requester.requestFocus() }
    val lit = Holo.signal.lit
    val glow = Holo.signal.glow
    val box = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp)

    fun commit() {
        val t = draft.trim()
        if (t.isNotEmpty()) onBody(joinItems(items + Item(false, t)))
        draft = ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp)
                        .border(1.5.dp, lit, box)
                        .background(if (item.done) glow.copy(alpha = 0.5f) else Color.Transparent, box)
                        .clickable(enabled = !editing) {
                            onBody(joinItems(items.mapIndexed { j, it -> if (j == i) Item(!it.done, it.label) else it }))
                        },
                    contentAlignment = Alignment.Center,
                ) { if (item.done) Text("✓", color = Holo.Text, fontSize = 14.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    item.label,
                    modifier = Modifier.weight(1f).clickable(enabled = !editing) {
                        onBody(joinItems(items.mapIndexed { j, it -> if (j == i) Item(!it.done, it.label) else it }))
                    },
                    color = if (item.done) Holo.Muted else Holo.Text,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                    fontSize = 15.sp, fontFamily = Holo.font,
                )
                Text(
                    "✕", color = Holo.Muted, fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !editing) { onBody(joinItems(items.filterIndexed { j, _ -> j != i })) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (adding && !editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.replace("\n", " ") },
                singleLine = true,
                textStyle = TextStyle(color = Holo.Text, fontFamily = Holo.font, fontSize = 15.sp),
                cursorBrush = SolidColor(lit),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .focusRequester(requester)
                    .onFocusChanged {
                        onFocus(it.isFocused)
                        if (it.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            hadFocus = false
                            commit()
                            adding = false
                        }
                    },
            )
        } else {
            Text(
                if (items.isEmpty()) "+ Add the first item" else "+ Add item",
                color = lit, fontSize = 14.sp, fontFamily = Holo.font,
                modifier = Modifier.clickable(enabled = !editing) { adding = true }.padding(vertical = 4.dp),
            )
        }
    }
}
