package com.popemkt.watchcal

import android.app.Application
import com.popemkt.watchcal.calendar.CalendarSource
import com.popemkt.watchcal.calendar.WearCalendarSource
import com.popemkt.watchcal.reminders.AlarmScheduler
import com.popemkt.watchcal.reminders.ReminderCoordinator
import com.popemkt.watchcal.reminders.ReminderGraphOwner
import com.popemkt.watchcal.reminders.ReminderNotifier
import com.popemkt.watchcal.reminders.ReminderStateStore
import com.popemkt.watchcal.reminders.SyncWorker

/** Composition root — the only place concrete implementations are wired together. */
class App : Application(), ReminderGraphOwner {

    val calendarSource: CalendarSource by lazy { WearCalendarSource(contentResolver) }
    val stateStore by lazy { ReminderStateStore(this) }
    private val notifier by lazy { ReminderNotifier(this) }

    override val reminderCoordinator by lazy {
        ReminderCoordinator(
            calendarSource = calendarSource,
            stateStore = stateStore,
            notifier = notifier,
            alarmScheduler = AlarmScheduler(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        SyncWorker.schedule(this)
    }
}
