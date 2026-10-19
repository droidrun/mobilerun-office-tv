package ai.droidrun.officetv

import ai.droidrun.officetv.data.*
import ai.droidrun.officetv.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CalendarTest {
    private val now = Instant.parse("2000-01-15T09:00:00Z")
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun event(start: String = "2000-01-15T09:12:00Z", end: String = "2000-01-15T10:00:00Z") =
        CalendarEvent("Example meeting", Instant.parse(start), Instant.parse(end))
    // Constructed synthetic credentials only: never use a real deployment or token in tests.
    private fun url(id: String = "example_".repeat(4), token: String = "example0".repeat(8)) =
        "https://script.google.com/macros/s/$id/exec?token=$token"

    @Test fun provisioningAcceptsOnlyExactBoundedAppsScriptUrl() {
        assertTrue(isValidCalendarUrl(url()))
        assertTrue(isValidCalendarUrl(url("a".repeat(20), "b".repeat(32))))
        assertTrue(isValidCalendarUrl(url("Z_-0".repeat(50), "a9".repeat(64))))
        val invalid = listOf(null, "", url().replace("https:", "http:"), url() + "\n",
            url() + "&extra=1", url() + "#fragment", " " + url(),
            url().replace("script.google.com", "script.google.com.example.invalid"),
            url().replace("script.google.com", "user@script.google.com"),
            url().replace("script.google.com", "script.google.com:443"),
            url().replace("/exec?", "/dev?"), url().replace("?token=", "?other="),
            url("a".repeat(19)), url("a".repeat(201)), url(token = "a".repeat(31)),
            url(token = "a".repeat(129)), url(token = "a_".repeat(32)), url(token = "a%20".repeat(16)))
        invalid.forEach { assertFalse("Expected invalid synthetic URL", isValidCalendarUrl(it)) }
    }

    @Test fun redirectsRequireExactHttpsHostWithoutCredentialsPortOrFragment() {
        assertTrue(isAllowedCalendarRedirect("https://script.googleusercontent.com/macros/echo?example=1"))
        val invalid = listOf(null, "", "/macros/echo", "//script.googleusercontent.com/macros/echo",
            "http://script.googleusercontent.com/macros/echo", "https://accounts.google.com/login",
            "https://script.googleusercontent.com.example.invalid/macros/echo",
            "https://script.googleusercontent.com@other.example.invalid/macros/echo",
            "https://user@script.googleusercontent.com/macros/echo",
            "https://script.googleusercontent.com:443/macros/echo",
            "https://script.googleusercontent.com/macros/echo#fragment", "https://[broken")
        invalid.forEach { assertFalse("Expected blocked redirect", isAllowedCalendarRedirect(it)) }
    }

    @Test fun responseRoundTripsOnlyDisplayFieldsAndAcceptsNulls() {
        val root = JSONObject(CalendarParser.encode(CalendarSnapshot(listOf(event()), now)))
        root.getJSONObject("next").put("description", "example extra field")
        root.put("location", "example extra field")
        val parsed = CalendarParser.parse(root.toString())
        assertEquals(CalendarSnapshot(listOf(event()), now), parsed)
        val encoded = CalendarParser.encode(parsed)
        assertFalse(encoded.contains("description"))
        assertFalse(encoded.contains("location"))
        assertEquals(emptyList<CalendarEvent>(), CalendarParser.parse(
            """{"generatedAt":"2000-01-15T09:00:00Z","next":null,"then":null}""").events)
        val reverse = CalendarSnapshot(listOf(event("2000-01-15T09:30:00Z"), event()), now)
        assertEquals(reverse.events.sortedBy { it.start }, CalendarParser.parse(CalendarParser.encode(reverse)).events)
    }

    @Test fun malformedAndUnauthorizedResponsesCannotReplaceLastGoodData() {
        val valid = JSONObject(CalendarParser.encode(CalendarSnapshot(listOf(event()), now)))
        val invalid = listOf("not json", "{}", "[]", valid.toString().replace("2000-01-15T09:00:00Z", "bad-date"),
            JSONObject(valid.toString()).put("next", "bad").toString(),
            JSONObject(valid.toString()).apply { getJSONObject("next").put("title", 123) }.toString(),
            JSONObject(valid.toString()).apply { getJSONObject("next").remove("end") }.toString(),
            JSONObject(valid.toString()).apply { getJSONObject("next").put("end", "2000-01-15T09:12:00Z") }.toString(),
            JSONObject(valid.toString()).apply { remove("then") }.toString())
        invalid.forEach { assertTrue(runCatching { CalendarParser.parse(it) }.isFailure) }
    }

    @Test fun localTodayFilterDropsEndedYesterdayAndTomorrowAcrossMidnight() {
        val midnight = Instant.parse("2000-01-15T23:00:00Z") // Jan 16 in Amsterdam.
        val previous = event("2000-01-15T22:59:00Z", "2000-01-15T23:30:00Z")
        val today = event("2000-01-15T23:00:00Z", "2000-01-16T00:00:00Z")
        val ended = event("2000-01-15T22:00:00Z", "2000-01-15T23:00:00Z")
        val tomorrow = event("2000-01-16T23:00:00Z", "2000-01-17T00:00:00Z")
        val events = listOf(tomorrow, previous, today, ended)
        assertEquals(listOf(today), todayCalendarEvents(events, midnight, zone))
        assertEquals(listOf(ended, previous), todayCalendarEvents(events, midnight.minusSeconds(1), zone))
        assertTrue(todayCalendarEvents(listOf(today), today.end, zone).isEmpty())
        // A different TV time zone must use its own date, not UTC or the script's zone.
        assertEquals(listOf(previous, today), todayCalendarEvents(listOf(today, previous), midnight, ZoneId.of("UTC")))
    }

    @Test fun countdownFormatsMinutesHoursRunningAndNoneAtBoundaries() {
        assertEquals("in 12 Min.", calendarCountdown(event(), now, zone))
        assertEquals("in 1 Min.", calendarCountdown(event("2000-01-15T09:00:01Z"), now, zone))
        assertEquals("in 1 Std. 5 Min.", calendarCountdown(event("2000-01-15T10:05:00Z", "2000-01-15T11:00:00Z"), now, zone))
        assertEquals("in 1 Std.", calendarCountdown(event("2000-01-15T10:00:00Z", "2000-01-15T11:00:00Z"), now, zone))
        assertEquals("läuft gerade · bis 11:00", calendarCountdown(event(), event().start, zone))
        assertEquals("läuft gerade · bis 11:00", calendarCountdown(event(), event().end.minusSeconds(1), zone))
        assertEquals("Heute keine Termine mehr", calendarCountdown(event(), event().end, zone))
        assertEquals("Heute keine Termine mehr", calendarCountdown(null, now, zone))
    }

    @Test fun displayDistinguishesUnconfiguredUnavailableAndLoadedEmptyCalendars() {
        assertNull(CalendarState().displayAt(now, zone))
        assertNull(CalendarState(false, CalendarSnapshot(listOf(event()), now)).displayAt(now, zone))
        assertEquals(CalendarDisplay(null, "Kalender nicht erreichbar"), CalendarState(true).displayAt(now, zone))
        val stale = CalendarSnapshot(listOf(event()), now.minusSeconds(86_400))
        assertEquals(CalendarDisplay(null, "Kalender nicht erreichbar"), CalendarState(true, stale).displayAt(now, zone))
        val loaded = CalendarState(true, CalendarSnapshot(emptyList(), now))
        assertEquals(CalendarDisplay(null, "Heute keine Termine mehr"), loaded.displayAt(now, zone))
        val good = CalendarState(true, CalendarSnapshot(listOf(event()), now))
        assertEquals(CalendarDisplay(event(), "in 12 Min."), good.displayAt(now, zone))
        assertEquals(CalendarDisplay(null, "Heute keine Termine mehr"), good.displayAt(event().end, zone))
        // A generatedAt from yesterday in UTC can still be today on the TV.
        val localMidnight = Instant.parse("2000-01-15T23:00:00Z")
        val localCache = CalendarState(true, CalendarSnapshot(emptyList(), localMidnight))
        assertEquals(CalendarDisplay(null, "Heute keine Termine mehr"), localCache.displayAt(localMidnight.plusSeconds(3600), zone))
        assertEquals(CalendarDisplay(null, "Kalender nicht erreichbar"), localCache.displayAt(localMidnight.plusSeconds(3600), ZoneId.of("UTC")))
        assertEquals(CalendarDisplay(null, "Kalender nicht erreichbar"), good.displayAt(localMidnight, zone))
    }

    @Test fun dreamShowsStartBeforeMeetingAndEndWhileRunning() {
        assertEquals("10:12", calendarDreamTime(event(), now, zone))
        assertEquals("bis 11:00", calendarDreamTime(event(), event().start, zone))
        assertEquals("bis 11:00", calendarDreamTime(event(), event().end.minusSeconds(1), zone))
        assertEquals("in 2 Std.", calendarCountdown(event("2000-01-15T11:00:00Z", "2000-01-15T12:00:00Z"), now, zone))
    }

    @Test fun homeProgressFillsTheLastHourThenTracksElapsedMeetingTime() {
        val meeting = event()
        assertEquals(0f, calendarProgress(meeting, meeting.start.minusSeconds(7200)), 0.0001f)
        assertEquals(0f, calendarProgress(meeting, meeting.start.minusSeconds(3600)), 0.0001f)
        assertEquals(0.5f, calendarProgress(meeting, meeting.start.minusSeconds(1800)), 0.0001f)
        assertEquals(59f / 60, calendarProgress(meeting, meeting.start.minusSeconds(60)), 0.0001f)
        assertEquals(0f, calendarProgress(meeting, meeting.start), 0.0001f)
        assertEquals(0.5f, calendarProgress(meeting, meeting.start.plusSeconds(24 * 60)), 0.0001f)
        assertEquals(1f, calendarProgress(meeting, meeting.end), 0.0001f)
        assertEquals(1f, calendarProgress(meeting, meeting.end.plusSeconds(60)), 0.0001f)
    }

    @Test fun homeDisplayIncludesThenAndPromotesItAfterTheFirstEventEnds() {
        val first = event()
        val then = event("2000-01-15T10:30:00Z", "2000-01-15T11:00:00Z").copy(title = "Example daily")
        val calendar = CalendarState(true, CalendarSnapshot(listOf(then, first), now))
        assertEquals(CalendarDisplay(first, "in 12 Min.", then), calendar.displayAt(now, zone))
        assertEquals(CalendarDisplay(first, "läuft gerade · bis 11:00", then), calendar.displayAt(first.start, zone))
        assertEquals(CalendarDisplay(then, "in 30 Min."), calendar.displayAt(first.end, zone))
        assertEquals(CalendarDisplay(null, "Heute keine Termine mehr"), calendar.displayAt(then.end, zone))
        assertNull(calendar.copy(configured = false).displayAt(now, zone))
        assertEquals(CalendarDisplay(null, "Kalender nicht erreichbar"),
            calendar.copy(snapshot = calendar.snapshot?.copy(generatedAt = now.minusSeconds(86_400))).displayAt(now, zone))
    }

    @Test fun recentPersistedGenerationSeedsFiveMinuteScheduleUntilProvisioning() {
        val cached = CalendarParser.parse(CalendarParser.encode(CalendarSnapshot(emptyList(), now)))
        val schedule = PollSchedule(5 * 60_000L, cached.generatedAt.toEpochMilli()) { now.plusSeconds(120).toEpochMilli() }
        assertEquals(180_000L, schedule.remainingDelay())
        schedule.retryNow()
        assertEquals(0L, schedule.remainingDelay())
        val expired = PollSchedule(5 * 60_000L, cached.generatedAt.toEpochMilli()) { now.plusSeconds(301).toEpochMilli() }
        assertEquals(0L, expired.remainingDelay())
    }

    @Test fun provisioningRejectsBeforeDiscardAndAcknowledgesOnlyCompletedMutations() {
        val operations = mutableListOf<String>()
        fun provision(value: String?, clear: Boolean, validate: (String?) -> Boolean): String =
            provisionSecret(value, clear, validate,
                beforeChange = { operations.add("discard") },
                store = { operations.add("store") }, remove = { operations.add("clear") })
        assertEquals("invalid", provision(null, false, ::isValidCalendarUrl))
        assertEquals("invalid", provision("example invalid", false, ::isValidCalendarUrl))
        assertEquals("invalid", provision("example invalid", false, ::isValidStatsKey))
        assertTrue(operations.isEmpty())
        assertEquals("stored", provision(url(), false, ::isValidCalendarUrl))
        assertEquals(listOf("discard", "store"), operations)
        operations.clear()
        assertEquals("stored", provision("example-stats-key", false, ::isValidStatsKey))
        assertEquals(listOf("discard", "store"), operations)
        operations.clear()
        assertEquals("cleared", provision(null, true) { error("Clear must not validate an absent value") })
        assertEquals(listOf("discard", "clear"), operations)
        operations.clear()
        assertTrue(runCatching {
            provisionSecret(url(), false, ::isValidCalendarUrl,
                beforeChange = { error("Example disk failure") },
                store = { operations.add("store") }, remove = { operations.add("clear") })
        }.isFailure)
        assertTrue(operations.isEmpty())
    }

    @Test fun carouselIncludesCalendarOnlyWhenAnEventExists() {
        assertEquals(listOf(Stat(StatLabels.CLOCK)), carouselStats(StatsState()))
        val available = carouselStats(StatsState(), event())
        assertEquals(listOf(Stat(StatLabels.CLOCK), Stat(StatLabels.CALENDAR, event = event())), available)
        assertEquals("Termin", StatLabels.accent(StatLabels.CALENDAR))
        assertEquals(StatLabels.CALENDAR, nextCarouselLabel(StatLabels.CLOCK, available))
        assertEquals(StatLabels.CLOCK, nextCarouselLabel(StatLabels.CALENDAR, available))
        assertEquals(StatLabels.CLOCK, nextCarouselLabel(StatLabels.CALENDAR, carouselStats(StatsState())))
    }
}
