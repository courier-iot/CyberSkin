package com.stellarite.cyberskinos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)        // bumps on resume so the app list refreshes
    private var homeSignal by mutableIntStateOf(0)  // bumps when Home is pressed
    private var active by mutableStateOf(false)     // true only while the home screen is in front

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SafeMode.begin(this) // counts this start, and steps in if the last few all failed

        // A home screen has nowhere to go back to, so Back on the bare home screen does nothing.
        // Registered before setContent, so the BackHandler in Launcher.kt (for the Databank, menus,
        // Settings and Arrange mode) still takes priority whenever one of those is open.
        onBackPressedDispatcher.addCallback(this) { /* stay on the home screen */ }

        Holo.load(this) // saved palette and motion speed, before anything is drawn
        Sfx.load(this) // sound effects and pulse rings, if you switched them on
        setContent { HoloTheme { LauncherRoot(tick, homeSignal, active) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
        active = true
    }

    override fun onPause() {
        active = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Reaching onStop means this start did not crash (a crash kills the process without calling it),
        // so a quick close or switch-away must not be counted towards safe mode.
        SafeMode.healthy(this)
    }

    @Deprecated("Only used for the app-widget permission and set-up screens")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AppWidgets.onResult(this, requestCode, resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }
}
