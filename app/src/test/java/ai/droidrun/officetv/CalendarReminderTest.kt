package ai.droidrun.officetv

import ai.droidrun.officetv.data.CalendarEvent
import ai.droidrun.officetv.data.CalendarSnapshot
import ai.droidrun.officetv.data.CalendarState
import ai.droidrun.officetv.data.ReminderHistory
import ai.droidrun.officetv.data.reminderDelayMillis
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CalendarReminderTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val start = Instant.parse("2000-01-15T09:00:00Z")
    private val event = CalendarEvent("Example review", start, start.plusSeconds(1800))
    private fun state(vararg events: CalendarEvent, generatedAt: Instant = start.minusSeconds(300)) =
        CalendarState(true, CalendarSnapshot(events.toList(), generatedAt))

    @Test fun triggerWaitsUntilOneMinuteAndFiresImmediatelyInsideTheWindow() {
        assertEquals(240_000L, reminderDelayMillis(event, start.minusSeconds(300), zone))
        assertEquals(1_000L, reminderDelayMillis(event, start.minusSeconds(61), zone))
        assertEquals(0L, reminderDelayMillis(event, start.minusSeconds(60), zone))
        assertEquals(0L, reminderDelayMillis(event, start.minusSeconds(30), zone))
        assertEquals(0L, reminderDelayMillis(event, start.minusMillis(1), zone))
        assertNull(reminderDelayMillis(event, start, zone))
        assertNull(reminderDelayMillis(event, start.plusSeconds(1), zone))
        assertNull(reminderDelayMillis(event, event.end, zone))
    }

    @Test fun screenOnLateUsesCurrentTimeAndSkipsAnEventThatAlreadyStarted() {
        val history = ReminderHistory()
        val calendar = state(event)
        val early = start.minusSeconds(300)
        assertEquals(event, history.next(calendar, early, zone))
        // The TV wakes after the originally scheduled trigger; no old delay is reused.
        val late = start.minusSeconds(10)
        assertEquals(event, history.next(calendar, late, zone))
        assertEquals(0L, reminderDelayMillis(event, late, zone))
        assertNull(history.next(calendar, start, zone))
    }

    @Test fun dedupeUsesStartAndTitleAcrossSnapshotRefreshesAndEndEdits() {
        val history = ReminderHistory()
        val now = start.minusSeconds(60)
        val second = event.copy(start = start.plusSeconds(120), end = event.end.plusSeconds(120))
        assertEquals(event, history.next(state(second, event), now, zone))
        history.markShown(event, now, zone)
        assertNull(history.next(state(event.copy(end = event.end.plusSeconds(60))), now, zone))
        assertEquals(second, history.next(state(event, second), now, zone))
        history.markShown(second, now, zone)
        assertNull(history.next(state(event, second), now, zone))
        val renamed = event.copy(title = "Example planning")
        assertEquals(renamed, history.next(state(renamed), now, zone))
    }

    @Test fun onlyConfiguredFreshSnapshotsAndTodaysFutureEventsAreScheduled() {
        val history = ReminderHistory()
        val now = start.minusSeconds(60)
        assertNull(history.next(CalendarState(), now, zone))
        assertNull(history.next(state(event).copy(configured = false), now, zone))
        assertNull(history.next(CalendarState(true), now, zone))
        assertNull(history.next(state(event, generatedAt = now.minusSeconds(86_400)), now, zone))
        val tomorrow = event.copy(start = start.plusSeconds(86_400), end = event.end.plusSeconds(86_400))
        assertNull(reminderDelayMillis(tomorrow, now, zone))
        assertNull(history.next(state(tomorrow), now, zone))
        // A running first event must not hide the following event's reminder window.
        val running = event.copy(start = start.minusSeconds(120))
        assertEquals(event, history.next(state(running, event), now, zone))
    }

    @Test fun todayFollowsTheTvTimeZoneAndHistoryRollsAtLocalMidnight() {
        val history = ReminderHistory()
        val midnight = Instant.parse("2000-01-15T23:00:00Z")
        val meeting = event.copy(start = midnight.plusSeconds(120), end = midnight.plusSeconds(600))
        val before = midnight.minusSeconds(30)
        assertNull(reminderDelayMillis(meeting, before, zone))
        assertEquals(90_000L, reminderDelayMillis(meeting, before, ZoneId.of("UTC")))
        history.markShown(meeting, before, zone)
        assertEquals(meeting, history.next(state(meeting, generatedAt = midnight), midnight, zone))
    }

    @Test fun eventsWithTheSameStartRemainPendingUntilEachIsShown() {
        val history = ReminderHistory()
        val first = event.copy(title = "Example first review")
        val second = event.copy(title = "Example second review")
        val calendar = state(first, second)
        val now = start.minusSeconds(60)
        assertEquals(first, history.next(calendar, now, zone))
        history.markShown(first, now, zone)
        val afterFirstCard = now.plusSeconds(11)
        assertEquals(second, history.next(calendar, afterFirstCard, zone))
        assertEquals(0L, reminderDelayMillis(second, afterFirstCard, zone))
        history.markShown(second, afterFirstCard, zone)
        assertNull(history.next(calendar, afterFirstCard.plusSeconds(11), zone))
    }
}
