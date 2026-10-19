package ai.droidrun.officetv

import ai.droidrun.officetv.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LogicTest {
    private fun sample() = requireNotNull(javaClass.getResource("/stats-sample.json")).readText()

    @Test fun sampleMapsAggregatesAndSeries() {
        val stats = StatsParser.parse(sample())
        assertEquals(1234L, stats.usersTotal)
        assertEquals(70L, stats.usersNew7d)
        assertEquals(7000L, stats.runnerSteps7d)
        assertEquals(10.0, stats.averageSteps7d!!, 0.001)
        assertEquals(1000L, stats.agentTurns7d)
        assertEquals(100L, stats.machines7d)
        assertEquals(30, stats.usersPerDay.size)
        assertEquals(SeriesPoint("2000-01-30", 10.0), stats.usersPerDay.last())
        assertEquals(24, stats.tasksPerHour.size)
        assertEquals(SeriesPoint("2000-01-30T23:00:00Z", 40.0), stats.tasksPerHour.last())
    }

    @Test fun columnsAreMappedByNameRegardlessOfOrder() {
        val root = JSONObject(sample())
        val names = root.getJSONArray("columns")
        val row = root.getJSONArray("results").getJSONArray(0)
        val order = (0 until names.length()).reversed()
        root.put("columns", JSONArray(order.map { names.get(it) }))
        root.put("results", JSONArray().put(JSONArray(order.map { row.get(it) })))
        assertEquals(StatsParser.parse(sample()), StatsParser.parse(root.toString()))
    }

    @Test fun nullableAndMissingFieldsStayAbsent() {
        val root = JSONObject().put("columns", JSONArray(listOf("users_total", "avg_steps_per_task_7d")))
            .put("results", JSONArray().put(JSONArray().put(12).put(JSONObject.NULL)))
        val stats = StatsParser.parse(root.toString())
        assertEquals(12L, stats.usersTotal)
        assertNull(stats.averageSteps7d)
        assertNull(stats.runnerSteps7d)
        assertTrue(stats.tasksPerHour.isEmpty())
    }

    @Test fun persistedAggregatesRoundTripWithoutRawEndpointMetadata() {
        val stats = StatsParser.parse(sample())
        val saved = StatsParser.encode(stats)
        assertEquals(stats, StatsParser.parse(saved.toString()))
        assertFalse(saved.has("last_refresh"))
        val lastGood = StatsState(cloud = CloudSnapshot(stats, 1234))
        val offline = lastGood.copy(statsStatus = "Stats-Schlüssel fehlt")
        assertEquals(lastGood.cloud, offline.cloud)
        assertNull(StatsState().cloud)
        assertNull(StatsState().stars)
    }

    @Test fun backoffDoublesCapsAndResetsIndependently() {
        val stats = Backoff()
        val github = Backoff()
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L),
            List(8) { stats.failureDelay() / 60_000 })
        assertEquals(60_000L, github.failureDelay())
        stats.reset()
        assertEquals(60_000L, stats.failureDelay())
    }

    @Test fun bypassUsesMonotonicDeadlineAndExpiresAtBoundary() {
        var now = 1_000L
        val originalClock = Bypass.now
        Bypass.now = { now }
        try {
            Bypass.pause(0)
            assertFalse(Bypass.isPaused())
            Bypass.pause()
            now = 30_999L
            assertTrue(Bypass.isPaused())
            now++
            assertFalse(Bypass.isPaused())
            Bypass.pause(10)
            now += 9
            assertTrue(Bypass.isPaused())
            Bypass.pause(0)
            assertFalse(Bypass.isPaused())
        } finally {
            Bypass.now = originalClock
        }
    }

    @Test fun takeoverLimiterCapsRollingMinuteAndResumesAfterFiveMinutes() {
        var now = 0L
        val limiter = TakeoverLimiter { now }
        repeat(3) { assertTrue(limiter.allowLaunch()); now += 10_000 }
        assertFalse(limiter.allowLaunch())
        now = 329_999
        assertFalse(limiter.allowLaunch())
        now++
        assertTrue(limiter.allowLaunch())
    }

    @Test fun takeoverLimiterExpiresLaunchesAtRollingMinuteBoundary() {
        var now = 0L
        val limiter = TakeoverLimiter { now }
        repeat(3) { assertTrue(limiter.allowLaunch()); now += 20_000 }
        assertTrue(limiter.allowLaunch()) // The launch at 0 is now outside the minute.
    }

    @Test fun dailyBarsHaveThirtyUtcDateSlotsAndZeroFillMissingDays() {
        val today = LocalDate.parse("2026-10-19")
        val slots = dailySlots(listOf(SeriesPoint("2026-10-18", 2.0), SeriesPoint("2026-10-01", 4.0),
            SeriesPoint("2026-09-19", 99.0), SeriesPoint("2026-10-20", 99.0)), today)
        assertEquals(30, slots.size)
        assertEquals(SeriesPoint("2026-09-20", 0.0), slots.first())
        assertEquals(SeriesPoint("2026-10-19", 0.0), slots.last())
        assertEquals(2.0, slots[28].count, 0.0)
        assertEquals(6.0, slots.sumOf { it.count }, 0.0)
    }

    @Test fun clockStateChangesAtMinuteBoundariesAndUsesUtcChartDates() {
        val instant = Instant.parse("2026-10-19T23:59:00Z")
        val zone = ZoneId.of("Asia/Tokyo")
        val clock = clockStateAt(instant.toEpochMilli(), zone)
        assertEquals(clock, clockStateAt(instant.plusSeconds(59).toEpochMilli(), zone))
        assertNotEquals(clock, clockStateAt(instant.plusSeconds(60).toEpochMilli(), zone))
        assertEquals(LocalDate.parse("2026-10-19"), clock.utcDate)
        assertEquals("08:59", clock.time)
    }

    @Test fun sourceScheduleRetainsDeadlinesAcrossVisibilityAndStartsFromCachedFetch() {
        var now = 10_000L
        val schedule = PollSchedule(intervalMs = 900_000, lastSuccessAt = 1_000) { now }
        assertEquals(891_000L, schedule.remainingDelay())
        now = 901_000 // A screen was hidden; the same source is due when it becomes visible again.
        assertEquals(0L, schedule.remainingDelay())
        schedule.failed()
        now += 20_000
        assertEquals(40_000L, schedule.remainingDelay())
        now += 40_000
        schedule.failed()
        assertEquals(120_000L, schedule.remainingDelay())
        schedule.retryNow() // Provisioning a different key retries without waiting for the old failure.
        assertEquals(0L, schedule.remainingDelay())
        schedule.succeeded(now)
        assertEquals(900_000L, schedule.remainingDelay())
        schedule.failed()
        assertEquals(60_000L, schedule.remainingDelay())
    }

    @Test fun provisioningAcceptsOnlyBoundedBearerTokens() {
        assertTrue(isValidStatsKey("a".repeat(16)))
        assertTrue(isValidStatsKey("a".repeat(512)))
        assertTrue(isValidStatsKey("Az09._~+/=-".repeat(2)))
        assertTrue(isValidStatsKey("example_stats_key"))
        assertTrue(isValidStatsKey("-" + "a".repeat(15)))
        val invalid = listOf(null, "", "a".repeat(15), "a".repeat(513),
            "a".repeat(16) + " ", "a".repeat(16) + "\n", "a".repeat(16) + "$",
            "a".repeat(16) + "\t", "a".repeat(16) + "\u0000")
        invalid.forEach { assertFalse("Expected invalid synthetic stats key", isValidStatsKey(it)) }
    }

    @Test fun numbersUseSpacesAndGermanDecimalComma() {
        assertEquals("9 700", formatNumber(9700))
        assertEquals("8,5", formatNumber(8.5))
        assertEquals("0", formatNumber(0))
        assertEquals("1 234 567", formatNumber(1234567))
    }
}
