package com.popemkt.watchcal

import android.app.Application
import android.app.PendingIntent
import com.popemkt.watchcal.calendar.CalendarSource
import com.popemkt.watchcal.calendar.WearCalendarSource
import com.popemkt.watchcal.domain.EventInstance
import com.popemkt.watchcal.reminders.AlarmScheduler
import com.popemkt.watchcal.reminders.ReminderCoordinator
import com.popemkt.watchcal.reminders.ReminderGraphOwner
import com.popemkt.watchcal.reminders.ReminderNotifier
import com.popemkt.watchcal.reminders.ReminderSettingsStore
import com.popemkt.watchcal.reminders.ReminderStateStore
import com.popemkt.watchcal.reminders.SyncWorker
import com.popemkt.watchcal.ui.AlarmActivity

/** Composition root — the only place concrete implementations are wired together. */
class App : Application(), ReminderGraphOwner {

    val calendarSource: CalendarSource by lazy { WearCalendarSource(contentResolver) }
    val stateStore by lazy { ReminderStateStore(this) }
    val settingsStore by lazy { ReminderSettingsStore(this) }
    private val notifier by lazy { ReminderNotifier(this, ::alarmFullScreenIntent) }

    override val reminderCoordinator by lazy {
        ReminderCoordinator(
            calendarSource = calendarSource,
            stateStore = stateStore,
            settingsStore = settingsStore,
            notifier = notifier,
            alarmScheduler = AlarmScheduler(this),
        )
    }

    /** Root-wired so the reminders layer never imports ui (specs/01 § fence note). */
    private fun alarmFullScreenIntent(instance: EventInstance): PendingIntent =
        PendingIntent.getActivity(
            this,
            instance.instanceKey.hashCode(),
            AlarmActivity.intent(this, instance.instanceKey, instance.title, instance.beginMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        SyncWorker.schedule(this)
    }
}
