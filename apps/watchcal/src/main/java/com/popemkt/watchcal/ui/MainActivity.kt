package com.popemkt.watchcal.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.popemkt.watchcal.App
import com.popemkt.watchcal.domain.ReminderDefaults
import com.popemkt.watchcal.domain.ReminderState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        setContent { WatchCalRoot(app) }
    }
}

@Composable
private fun WatchCalRoot(app: App) {
    var calendarGranted by remember { mutableStateOf(app.hasCalendarPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> calendarGranted = results[Manifest.permission.READ_CALENDAR] == true }

    LaunchedEffect(Unit) {
        if (!calendarGranted) permissionLauncher.launch(requiredPermissions())
    }

    if (!calendarGranted) {
        PermissionScreen(onRequest = { permissionLauncher.launch(requiredPermissions()) })
        return
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val entries by produceState(initialValue = emptyList<AgendaEntry>(), refreshTick) {
        app.reminderCoordinator.refresh()
        val now = System.currentTimeMillis()
        val instances = app.calendarSource.instances(now, now + ReminderDefaults.AGENDA_WINDOW_MILLIS)
        val states = app.stateStore.states()
        value = instances.map { AgendaEntry(it, states[it.instanceKey]) }
    }

    if (showSettings) {
        // Swipe-back leaves settings, not the app.
        BackHandler { showSettings = false }
        val interval by app.settingsStore.snoozeIntervalFlow
            .collectAsState(initial = ReminderDefaults.SNOOZE_INTERVAL_MILLIS)
        SettingsScreen(
            snoozeIntervalMillis = interval,
            onIntervalChange = { scope.launch { app.settingsStore.setSnoozeIntervalMillis(it) } },
        )
        return
    }

    AgendaScreen(
        entries = entries,
        onToggleDone = { entry ->
            scope.launch {
                val key = entry.instance.instanceKey
                // Undo = one snooze interval back in the nag loop (00-product § Agenda).
                if (entry.state == ReminderState.Done) {
                    app.reminderCoordinator.snooze(key)
                } else {
                    app.reminderCoordinator.markDone(key)
                }
                refreshTick++
            }
        },
        onOpenSettings = { showSettings = true },
        onGrantFullScreen = fullScreenGrantAction(app),
    )
}

/** Non-null only while the API 34+ full-screen-intent grant is missing (01-architecture § Permissions). */
private fun fullScreenGrantAction(app: App): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    val manager = app.getSystemService(NotificationManager::class.java)
    if (manager.canUseFullScreenIntent()) return null
    return {
        app.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun App.hasCalendarPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_CALENDAR)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
