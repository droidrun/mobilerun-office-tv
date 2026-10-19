package ai.droidrun.officetv

import android.app.Application
import ai.droidrun.officetv.data.DemoData
import ai.droidrun.officetv.data.clockFlow
import ai.droidrun.officetv.data.CalendarRepository
import ai.droidrun.officetv.data.isValidCalendarUrl
import ai.droidrun.officetv.data.KeyStore
import ai.droidrun.officetv.data.StatsRepository
import ai.droidrun.officetv.data.ReminderHistory
import ai.droidrun.officetv.data.ScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

class OfficeApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val keys by lazy { KeyStore(this) }
    val calendarUrl by lazy { KeyStore(this, "officetv_calendar", "calendar_url", ::isValidCalendarUrl) }
    val screen by lazy { ScreenState(this, scope) }
    val reminderHistory = ReminderHistory()
    internal var showTestReminder: (() -> Boolean)? = null
    val calendar by lazy { CalendarRepository(this, calendarUrl, screen.interactive, scope) }
    val stats by lazy { StatsRepository(this, keys, scope) }
    // Demo mode never constructs the network repositories for display or reminders.
    private val demoClock by lazy {
        clockFlow().map { Instant.now() }
            .stateIn(scope, SharingStarted.WhileSubscribed(0), Instant.now())
    }
    val statsState by lazy {
        if (BuildConfig.OFFICETV_DEMO) demoClock.map(DemoData::stats)
            .stateIn(scope, SharingStarted.WhileSubscribed(0), DemoData.stats(Instant.now()))
        else stats.state
    }
    val calendarState by lazy {
        if (BuildConfig.OFFICETV_DEMO) demoClock.map { DemoData.calendar(it) }
            .stateIn(scope, SharingStarted.WhileSubscribed(0), DemoData.calendar(Instant.now()))
        else calendar.state
    }
}
