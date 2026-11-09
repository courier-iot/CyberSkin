package com.stellarite.cyberskinos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private class InfoSection(val heading: String, val body: String)

private val CREDITS = listOf(
    InfoSection(
        "CyberSkinOS 1.0",
        "A holographic space-station home screen, written in Kotlin with Jetpack Compose. The only thing in CyberSkinOS that goes online is the Weather widget. " +
            "Links and web searches open in your browser, and your layouts, notes and settings stay on this phone.",
    ),
    InfoSection(
        "Made by",
        "Created by Courier, of Stellarite Corporation. The star mark is Stellarite's own.",
    ),
    InfoSection(
        "Weather",
        "Weather data by Open-Meteo.com, used under the Creative Commons Attribution 4.0 licence (CC BY 4.0). " +
            "The Weather widget sends only your location, rounded to about a kilometre, to open-meteo.com, and keeps the last answer on the phone.",
    ),
    InfoSection(
        "Planet positions",
        "Worked out on the phone from the orbital elements in \"Keplerian Elements for Approximate Positions of the Major Planets\" " +
            "by E. M. Standish, NASA Jet Propulsion Laboratory, Solar System Dynamics Group (Tables 1 and 2a).",
    ),
    InfoSection(
        "Sunrise and sunset",
        "Uses the method published in the Almanac for Computers (1990) by the Nautical Almanac Office, United States Naval Observatory.",
    ),
    InfoSection(
        "Moon phase",
        "Counted from the average length of a lunar month, starting from the new moon of 6 January 2000. It is good to about half a day.",
    ),
    InfoSection(
        "Sound effects",
        "The sounds were made for CyberSkinOS from generated tones: soft glassy pings, sweeps and chimes. They are played on the phone and nothing is recorded.",
    ),
    InfoSection(
        "Software",
        "Jetpack Compose, AndroidX and Material 3 by Google, used under the Apache License, Version 2.0.",
    ),
    InfoSection(
        "Notification access",
        "Used only by the Audio deck. It lets CyberSkinOS read which track is playing, so it can show the title and progress. " +
            "It reads only the media information and nothing else, and nothing leaves your phone. Play, pause, next and previous need no permission.",
    ),
    InfoSection(
        "Search",
        "If no app matches what you type in the Databank, you can search the web with DuckDuckGo. That happens in your browser, only when you tap.",
    ),
)

private val HELP = listOf(
    InfoSection(
        "The star",
        "Tap the Stellarite star to open the Databank. Double-tap it for the Console. Press and hold it, then drag, to move it anywhere you like.",
    ),
    InfoSection(
        "Pages",
        "Swipe left or right on the home screen to change page, or tap the dots at the bottom. Console > Pages adds, removes and reorders pages, and can give a page its own wallpaper. " +
            "Each page has its own widgets. Pressing Home takes you back to page 1. In Arrange mode, drag a widget past the left or right edge to send it to the next page.",
    ),
    InfoSection(
        "Arranging widgets",
        "Press and hold empty space, or choose Console > Arrange. Drag a widget to move it and pull its corner to resize. " +
            "The red cross removes it. Add puts a new one on the page you are on.",
    ),
    InfoSection(
        "Widget settings",
        "In Arrange mode, tap the ⚙ on a widget. Depending on the widget you can choose a 12 or 24 hour clock, Celsius or Fahrenheit, " +
            "a title of your own, how solid its panel is, and which page it lives on. The Vitals widget also lets you choose its readings and a colour for each. Tap a vial to see its exact figure.",
    ),
    InfoSection(
        "The Databank",
        "Every app, filed under A, B, C. Type to search: it finds initials (\"gm\" finds Google Maps) and does sums (try 18*4+6). " +
            "Long-press an app to pin it, put it in a folder, hide it, see its info or uninstall it.",
    ),
    InfoSection(
        "Audio deck",
        "Add an Audio deck and resize it: wide and short is a strip, taller and narrower is a square card. " +
            "The buttons control whatever music app is playing. For the track name and progress, tap the prompt on the widget and switch on Notification access once. " +
            "Tap the disc or the title to open the music app. If several players are running, the widget's ⚙ lets you choose which one to follow.",
    ),
    InfoSection(
        "Log",
        "One widget can hold several notes. Tap the three dots in its heading to add a note or a checklist, switch the one you are on between Text and Check, or delete it with the cross, " +
            "and use the arrows to move between notes. In a list, tap a line to tick it and the cross to remove it.",
    ),
    InfoSection(
        "Timer, Schedule feed and Floodlight",
        "Timer is a countdown and an alarm clock: tap its title to switch between them. They use Android's alarm service, so they ring even when the launcher is closed. " +
            "Android asks once if the launcher may send notifications, which is how it rings. Coming back to the home screen, tapping the notification or pressing Quiet stops the sound. " +
            "Schedule feed lists what is coming up in your calendar after you tap Allow. Floodlight switches your torch on and off with a tap and needs no permission. " +
            "A countdown has three slots (tap 1, 2 or 3 beside its title; a dot means it is running), and when one rings you can press Snooze for five minutes. " +
            "The ⚙ on a Timer chooses its ringtone. The ⚙ on a Floodlight can put the torch out by itself after a few minutes and, on phones with Android 13 or later that allow it, dim it.",
    ),
    InfoSection(
        "Atmos",
        "The Atmos widget shows the weather now and for the next three days. Tap Allow once so it can find roughly where you are; it uses the internet only for this, " +
            "and sends only your location rounded to about a kilometre. The ⚙ sets Celsius or Fahrenheit.",
    ),
    InfoSection(
        "Widgets from other apps",
        "Add > External widget lists the widgets your other apps offer. Android may ask once to let CyberSkinOS show it, and some open their own set-up screen first. " +
            "They are drawn by the other app, so they keep its own look. Move and resize them in Arrange mode. " +
            "They belong to this phone, so presets and backups leave them out.",
    ),
    InfoSection(
        "World time, Comms and Usage log",
        "World time shows your own time and up to six cities; add them in its ⚙. Comms holds up to eight people as round tiles: tap one to open the dialler with the number ready, press and hold to write a text. " +
            "You pick each contact in its ⚙, and CyberSkinOS sees only the contacts you pick, which are kept in the widget and in your backup file. " +
            "Usage log shows which apps you used most today. Tap Allow and switch on Usage access for CyberSkinOS once; Android keeps the figures and CyberSkinOS only reads them.",
    ),
    InfoSection(
        "Scheduled looks",
        "Settings > Look > Schedule switches your colours by the time of day or while charging, using the look of a preset such as Night Watch. " +
            "When the rule stops fitting, your own look comes back. Layouts, widgets and wallpaper never change, and it is checked every half minute while the home screen is showing.",
    ),
    InfoSection(
        "Sound and motion",
        "Settings > Look has Sound effects and Pulse rings, both off until you switch them on. The sounds are soft glassy pings and sweeps for opening an app, the star, the Console, arranging, putting a widget down and changing page, " +
            "and they follow your phone's volume, so silent mode mutes them. Pulse rings spread out from the star when you open the Console or start arranging. " +
            "The panels shimmer now and then, and a scan line sweeps down the screen when you change page. Both stop when Motion is off.",
    ),
    InfoSection(
        "Recent apps and Holo icons",
        "The Databank shows the apps you opened lately in a Recent row at the top. Settings > Apps turns the row off or clears it. " +
            "Settings > Look > Holo icons draws every app icon in the light of your palette so they match.",
    ),
    InfoSection(
        "Folders and links",
        "Add a Data folder or Link cache widget and tap it to open. To put an app in a folder, long-press it and choose Folder. " +
            "Link caches have an Add link button beside Close.",
    ),
    InfoSection(
        "Presets",
        "Console > Presets saves and applies layouts. A preset changes the page you are on, and your previous layout is kept so you can undo it. " +
            "Deep Space, Mission Control, Nebula Drift, Night Watch and Stellarite Prime come with a colour scheme as well, but never touch your wallpaper. " +
            "A preset of yours can also save your colours and your wallpaper, using the switches when you save. " +
            "Share sends a layout and its colours as a text code, never the wallpaper, and Import adds one you received.",
    ),
    InfoSection(
        "Settings",
        "Console > Settings has the palette, the signal colour, motion speed, font, wallpaper, your own mixed colours, the fixed grid and its size (Auto, 5 × 10 or 4 × 9), hidden apps and your default home app.",
    ),
    InfoSection(
        "Backup and reset",
        "Settings > Backup and reset. Backup saves everything to one file wherever you choose, and Restore reads one back in. " +
            "Layout starts the widgets again from the Bridge layout. Defaults puts the colours, font, wallpaper and motion back to how CyberSkinOS came. " +
            "Each of them asks before it changes anything. About once a month CyberSkinOS offers to make a backup; Settings has a switch to turn that off. " +
            "If CyberSkinOS ever fails to start several times, it starts again from the Bridge layout and keeps a copy of how things were. Rescued, in the same place, brings it back.",
    ),
)

@Composable
private fun InfoDialog(title: String, sections: List<InfoSection>, onClose: () -> Unit, footer: String? = null) {
    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { s ->
                        Column {
                            Text(s.heading, style = MaterialTheme.typography.titleMedium, color = Holo.signal.lit)
                            Text(s.body, style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                        }
                    }
                }
                if (footer != null) {
                    Text(
                        footer, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge, fontSize = 11.sp,
                        fontStyle = FontStyle.Italic, color = Holo.Muted.copy(alpha = 0.7f),
                    )
                }
                ButtonRow { HoloButton("Close", onClick = onClose) }
            }
        }
    }
}

/** Credits and licences, under the Stellarite star. */
@Composable
fun CreditsDialog(onClose: () -> Unit) = InfoDialog(
    "CREDITS", CREDITS, onClose,
    footer = "Ruthlessness is mercy upon ourselves. Adapt to the times, and fight for what is right.",
)

/** A short guide to every gesture. */
@Composable
fun HelpDialog(onClose: () -> Unit) = InfoDialog("HELP", HELP, onClose)

/** The Stellarite mark: the star on its orbit. Used on the credits and the Console. */
@Composable
fun StellariteMark(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        OrbitRing(Modifier.fillMaxSize(), speed = 24f)
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize(0.8f)) {
            stellariteStar(center, size.minDimension / 2f)
        }
    }
}
