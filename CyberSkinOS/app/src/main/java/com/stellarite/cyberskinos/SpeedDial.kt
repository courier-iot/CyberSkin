package com.stellarite.cyberskinos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val MAX_DIAL = 8

/** A Comms entry is saved as "Name|number". */
internal class DialEntry(val name: String, val number: String)

internal fun dialEntryOf(raw: String): DialEntry? {
    val i = raw.indexOf('|')
    if (i <= 0 || i == raw.length - 1) return null
    return DialEntry(raw.substring(0, i), raw.substring(i + 1))
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun startSafely(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app on this phone can do that.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * The Comms widget: a few people as round tiles. Tap one to open the dialler with their number ready (you still
 * press call), press and hold to start a text message. Needs no permission: the contact picker hands over only the
 * contact you choose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialContent(entries: List<String>, title: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val people = entries.mapNotNull { dialEntryOf(it) }
    val accent = Holo.signal.lit
    val glow = Holo.signal.glow
    val perRow = (maxWidth / 72.dp).toInt().coerceAtLeast(1)
    val rowsFit = (((maxHeight.value - 22f) / 76f).toInt()).coerceAtLeast(1)

    Column(Modifier.fillMaxSize()) {
        Text(
            title, fontSize = 14.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold,
            color = accent, maxLines = 1, modifier = Modifier.height(22.dp),
        )
        if (people.isEmpty()) {
            Text(
                "Tap the ⚙ in Arrange mode and choose Add contact.",
                fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Muted,
            )
        } else {
            people.chunked(perRow).take(rowsFit).forEach { rowOf ->
                Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowOf.forEach { p ->
                        Column(
                            Modifier.width(68.dp).combinedClickable(
                                onClick = { startSafely(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p.number)))) },
                                onLongClick = { startSafely(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(p.number)))) },
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(Holo.Deck, Holo.Void)))
                                    .border(1.5.dp, Brush.verticalGradient(listOf(glow, Holo.Dim)), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(initialsOf(p.name), fontSize = 17.sp, fontFamily = Holo.font, fontWeight = FontWeight.Bold, color = accent)
                            }
                            Text(
                                p.name.substringBefore(' '), fontSize = 12.sp, fontFamily = Holo.font, color = Holo.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
