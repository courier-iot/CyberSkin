package com.stellarite.cyberskinos

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/**
 * Widgets that belong to other apps (a weather app's, a music app's, a Google widget...). CyberSkinOS acts as their host:
 * Android draws them inside CyberSkinOS, and CyberSkinOS only decides where they sit and how big they are.
 * Each hosted widget keeps its Android widget number in the setting "aw_<widget id>".
 */
object AppWidgets {
    private const val HOST_ID = 4242
    private const val REQ_BIND = 7101
    private const val REQ_CONFIG = 7102

    private var host: AppWidgetHost? = null
    private var listeners = 0

    // the add-a-widget conversation with Android, which spans two screens that report back to the activity
    private var done: ((Int) -> Unit)? = null
    private var workingId = -1
    private var workingInfo: AppWidgetProviderInfo? = null

    fun host(ctx: Context): AppWidgetHost =
        host ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it }

    fun manager(ctx: Context): AppWidgetManager = AppWidgetManager.getInstance(ctx.applicationContext)

    /** Every widget the installed apps offer, sorted by name. */
    fun providers(ctx: Context): List<AppWidgetProviderInfo> = try {
        val pm = ctx.packageManager
        manager(ctx).installedProviders.filter { it.provider.packageName != ctx.packageName }
            .sortedBy { (it.loadLabel(pm) ?: "").lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    /** The name of the app a widget comes from. */
    fun appName(ctx: Context, info: AppWidgetProviderInfo): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    } catch (e: Exception) {
        info.provider.packageName
    }

    fun activityOf(ctx: Context): Activity? {
        var c: Context = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Starts adding a widget: allocates its number, asks Android's permission to bind it if needed, and runs the app's
     * own set-up screen if it has one. [finished] gets the new Android widget number, or -1 if it did not work out.
     */
    fun begin(activity: Activity, info: AppWidgetProviderInfo, finished: (Int) -> Unit) {
        val id = try {
            host(activity).allocateAppWidgetId()
        } catch (e: Exception) {
            finished(-1)
            return
        }
        done = finished
        workingId = id
        workingInfo = info
        val bound = try {
            manager(activity).bindAppWidgetIdIfAllowed(id, info.provider)
        } catch (e: Exception) {
            false
        }
        if (bound) {
            configure(activity)
        } else {
            val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(ask, REQ_BIND)
            } catch (e: Exception) {
                fail(activity)
            }
        }
    }

    private fun configure(activity: Activity) {
        if (workingInfo?.configure == null) {
            finish()
            return
        }
        try {
            host(activity).startAppWidgetConfigureActivityForResult(activity, workingId, 0, REQ_CONFIG, null)
        } catch (e: Exception) {
            fail(activity)
        }
    }

    /** Called by the activity for every result. Returns true if it was one of ours. */
    fun onResult(activity: Activity, request: Int, result: Int): Boolean {
        when (request) {
            REQ_BIND -> if (result == Activity.RESULT_OK) configure(activity) else fail(activity)
            REQ_CONFIG -> if (result == Activity.RESULT_OK) finish() else fail(activity)
            else -> return false
        }
        return true
    }

    private fun finish() {
        val id = workingId
        val cb = done
        done = null
        workingId = -1
        workingInfo = null
        cb?.invoke(id)
    }

    private fun fail(ctx: Context) {
        if (workingId >= 0) release(ctx, workingId)
        val cb = done
        done = null
        workingId = -1
        workingInfo = null
        cb?.invoke(-1)
    }

    /** Gives a widget number back to Android when its widget is removed. */
    fun release(ctx: Context, awId: Int) {
        if (awId < 0) return
        try {
            host(ctx).deleteAppWidgetId(awId)
        } catch (e: Exception) {
            // already gone
        }
    }

    /** Releases the widget numbers held by every hosted widget in the saved settings. Used when the layout is reset. */
    fun releaseAll(ctx: Context, prefs: SharedPreferences) {
        prefs.all.keys.filter { it.startsWith("aw_") }.forEach { k ->
            release(ctx, prefs.getInt(k, -1))
        }
    }

    /** Hosted widgets only update while someone is listening. Counted so several widgets can share one subscription. */
    fun listen(ctx: Context) {
        listeners += 1
        if (listeners == 1) {
            try {
                host(ctx).startListening()
            } catch (e: Exception) {
                // a broken widget must not stop the launcher
            }
        }
    }

    fun unlisten(ctx: Context) {
        if (listeners <= 0) return
        listeners -= 1
        if (listeners == 0) {
            try {
                host(ctx).stopListening()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }
}

/** What a hosted widget shows. */
@Composable
fun AppWidgetContent(id: Long, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val awId = prefs.getInt("aw_$id", -1)
    val info = remember(awId) {
        if (awId < 0) null else try {
            AppWidgets.manager(ctx).getAppWidgetInfo(awId)
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        AppWidgets.listen(ctx)
        onDispose { AppWidgets.unlisten(ctx) }
    }
    if (info == null) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
            Text("Widget not available", style = MaterialTheme.typography.titleMedium, color = Holo.Bright)
            Text(
                "Its app may have been removed, or this layout came from another phone. Remove this widget and add it again.",
                style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
            )
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        AndroidView(
            factory = { c -> AppWidgets.host(c).createView(c, awId, info) },
            modifier = Modifier.fillMaxSize(),
            update = { v -> v.updateAppWidgetSize(null, w, h, w, h) },
        )
    }
}

/** The list of widgets other apps offer. Tapping one starts adding it. */
@Composable
fun AppWidgetChooser(onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { AppWidgets.providers(ctx) }
    Dialog(onDismissRequest = onClose) {
        HoloPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("EXTERNAL WIDGETS", style = MaterialTheme.typography.titleLarge, color = Holo.Bright)
                Text(
                    "Pick one. Android may ask once to let CyberSkinOS show it, and some widgets open their own set-up screen first. " +
                        "You can move and resize it in Arrange mode like any other.",
                    style = MaterialTheme.typography.labelLarge, color = Holo.Muted,
                )
                if (list.isEmpty()) {
                    Text("No other app offers a widget on this phone.", style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                }
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    list.forEach { info ->
                        val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                        Column(
                            Modifier.fillMaxWidth().clip(shape)
                                .background(Holo.Void.copy(alpha = 0.6f))
                                .border(1.dp, Holo.Dim, shape)
                                .clickable { onPick(info) }
                                .padding(12.dp),
                        ) {
                            Text(info.loadLabel(ctx.packageManager) ?: "Widget", style = MaterialTheme.typography.titleMedium, color = Holo.Bright)
                            Text(AppWidgets.appName(ctx, info), style = MaterialTheme.typography.bodyMedium, color = Holo.Text)
                        }
                    }
                }
                ButtonRow { HoloButton("Cancel", onClick = onClose) }
            }
        }
    }
}
