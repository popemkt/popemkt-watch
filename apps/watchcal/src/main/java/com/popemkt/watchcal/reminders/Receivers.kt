package com.popemkt.watchcal.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] on a coroutine while holding the broadcast alive via goAsync. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

private fun Context.coordinator(): ReminderCoordinator =
    (applicationContext as ReminderGraphOwner).reminderCoordinator

/** Composition root contract — implemented by the Application class. */
interface ReminderGraphOwner {
    val reminderCoordinator: ReminderCoordinator
}

/** Fires at the planned next-wake time; the refresh re-arms the chain. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        runAsync { context.coordinator().refresh() }
}

/** Alarms do not survive reboot or clock changes — re-derive and re-arm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        runAsync { context.coordinator().refresh() }
}

/** Handles the Snooze / Done notification actions (and swipe-away = snooze). */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val instanceKey = intent.getStringExtra(EXTRA_INSTANCE_KEY) ?: return
        when (intent.action) {
            ACTION_SNOOZE -> runAsync { context.coordinator().snooze(instanceKey) }
            ACTION_DONE -> runAsync { context.coordinator().markDone(instanceKey) }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.popemkt.watchcal.SNOOZE"
        const val ACTION_DONE = "com.popemkt.watchcal.DONE"
        const val EXTRA_INSTANCE_KEY = "instance_key"
    }
}
