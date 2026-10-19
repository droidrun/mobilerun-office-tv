package ai.droidrun.officetv.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Null outside today's future events; zero means the TV woke inside the reminder window. */
fun reminderDelayMillis(event: CalendarEvent, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (event.start <= now || event.start.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()) return null
    return Duration.between(now, event.start.minusSeconds(60)).toMillis().coerceAtLeast(0)
}

/** Process-local day memory survives service reconnects; event end edits do not replay a reminder. */
class ReminderHistory {
    private var day: LocalDate? = null
    private val shown = mutableSetOf<Pair<Instant, String>>()

    private fun rollDay(now: Instant, zone: ZoneId) {
        val today = now.atZone(zone).toLocalDate()
        if (day != today) {
            shown.clear()
            day = today
        }
    }

    fun next(state: CalendarState, now: Instant, zone: ZoneId = ZoneId.systemDefault()): CalendarEvent? {
        rollDay(now, zone)
        // Reuse the display's configured/fresh snapshot gate before scheduling any event.
        if (state.displayAt(now, zone)?.event == null) return null
        return state.snapshot?.events?.sortedBy { it.start }?.firstOrNull {
            reminderDelayMillis(it, now, zone) != null && (it.start to it.title) !in shown
        }
    }

    fun markShown(event: CalendarEvent, now: Instant, zone: ZoneId = ZoneId.systemDefault()) {
        rollDay(now, zone)
        shown.add(event.start to event.title)
    }
}
