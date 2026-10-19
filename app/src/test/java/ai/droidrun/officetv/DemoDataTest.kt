package ai.droidrun.officetv

import ai.droidrun.officetv.data.DemoData
import ai.droidrun.officetv.data.dailySlots
import ai.droidrun.officetv.data.StatsState
import ai.droidrun.officetv.data.formatNumber
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DemoDataTest {
    @Test fun syntheticStatsCoverCurrentChartWindows() {
        val now = Instant.parse("2000-01-30T12:00:00Z")
        val state = DemoData.stats(now)
        val cloud = requireNotNull(state.cloud)
        assertTrue(requireNotNull(cloud.stats.usersTotal) > 0)
        assertEquals(30, cloud.stats.usersPerDay.size)
        assertEquals(cloud.stats.usersPerDay, dailySlots(cloud.stats.usersPerDay, now.atZone(ZoneOffset.UTC).toLocalDate()))
        assertEquals(24, cloud.stats.tasksPerHour.size)
        assertTrue(cloud.stats.tasksPerHour.all { it.count > 0 })
        assertEquals(now.toEpochMilli(), cloud.fetchedAt)
        assertTrue(requireNotNull(state.stars).count > 0)
        assertNull(state.statsStatus)
    }

    @Test fun demoStatsShowPlaceholdersWhileNormalStatsKeepNumbers() {
        val demo = DemoData.stats(Instant.parse("2000-01-30T12:00:00Z"))
        assertTrue(demo.demo)
        val cloud = requireNotNull(demo.cloud).stats
        val values = listOfNotNull(cloud.usersTotal, cloud.usersNew7d, cloud.runnerSteps7d,
            cloud.averageSteps7d, cloud.tasks24h, cloud.machines7d, demo.stars?.count)
        assertEquals(7, values.size)
        for (value in values) assertEquals("—", formatNumber(value, demo = demo.demo))
        assertEquals("+— in 7 Tagen", "+${formatNumber(requireNotNull(cloud.usersNew7d), demo = demo.demo)} in 7 Tagen")
        assertEquals("Ø — je Task", "Ø ${formatNumber(requireNotNull(cloud.averageSteps7d), demo = demo.demo)} je Task")

        val normal = StatsState(cloud = demo.cloud, stars = demo.stars)
        assertFalse(normal.demo)
        for (value in values) assertEquals(formatNumber(value), formatNumber(value, demo = normal.demo))
        assertEquals("1 250", formatNumber(requireNotNull(cloud.usersTotal), demo = normal.demo))
        assertEquals("12,5", formatNumber(12.5, demo = normal.demo))
    }

    @Test fun syntheticCalendarHasUpcomingEventsTodayIncludingNearMidnight() {
        for (now in listOf(Instant.parse("2000-01-30T12:00:00Z"), Instant.parse("2000-01-30T23:59:59Z"))) {
            val display = requireNotNull(DemoData.calendar(now, ZoneOffset.UTC).displayAt(now, ZoneOffset.UTC))
            val event = requireNotNull(display.event)
            assertEquals("Weekly review", event.title)
            assertEquals(now.atZone(ZoneOffset.UTC).toLocalDate(), event.start.atZone(ZoneOffset.UTC).toLocalDate())
            assertTrue(event.end > now)
            assertNotNull(display.then)
        }
        val now = Instant.parse("2000-01-30T12:00:00Z")
        val event = requireNotNull(DemoData.calendar(now, ZoneOffset.UTC).displayAt(now, ZoneOffset.UTC)?.event)
        assertEquals(now.plusSeconds(25 * 60), event.start)
        assertEquals(event.start.plusSeconds(60 * 60), event.end)
    }
}
