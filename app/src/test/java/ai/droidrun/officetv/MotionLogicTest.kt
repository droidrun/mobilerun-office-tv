package ai.droidrun.officetv

import ai.droidrun.officetv.data.CloudSnapshot
import ai.droidrun.officetv.data.CloudStats
import ai.droidrun.officetv.data.SeriesPoint
import ai.droidrun.officetv.data.StarsSnapshot
import ai.droidrun.officetv.data.StatsState
import ai.droidrun.officetv.ui.*
import org.junit.Assert.*
import org.junit.Test

class MotionLogicTest {
    @Test fun driftStaysBoundedAndChangesOnlyAtThreeMinuteBoundaries() {
        val interval = BurnInProtection.INTERVAL_MS
        assertEquals(180_000L, interval)
        repeat(100) { index ->
            val start = index * interval
            val offset = BurnInProtection.offsetAt(start)
            assertTrue(offset.first in -6..6)
            assertTrue(offset.second in -6..6)
            assertEquals(offset, BurnInProtection.offsetAt(start + interval - 1))
            assertNotEquals(offset, BurnInProtection.offsetAt(start + interval))
        }
        assertEquals(BurnInProtection.offsetAt(0), BurnInProtection.offsetAt(interval * 7))
    }

    @Test fun nightWindowIncludesTenPmAndEndsAtSevenAm() {
        assertFalse(BurnInProtection.isNight(21))
        assertTrue(BurnInProtection.isNight(22))
        assertTrue(BurnInProtection.isNight(6))
        assertFalse(BurnInProtection.isNight(7))
        assertTrue(BurnInProtection.isNight(0))
    }

    @Test fun carouselSkipsUnloadedValuesButIncludesLoadedZeroesInOrder() {
        assertEquals(listOf(Stat(StatLabels.CLOCK)), carouselStats(StatsState()))
        val cloud = CloudStats(0, null, emptyList(), null, null, listOf(SeriesPoint("2000-01-30T00:00:00Z", 3.0)), null)
        val state = StatsState(CloudSnapshot(cloud, 0), StarsSnapshot(0, 0))
        val available = carouselStats(state)
        assertEquals(listOf(Stat(StatLabels.CLOCK), Stat(StatLabels.USERS, 0),
            Stat(StatLabels.TASKS, 3), Stat(StatLabels.STARS, 0)), available)
        assertEquals(StatLabels.USERS, nextCarouselLabel(StatLabels.CLOCK, available))
        assertEquals(StatLabels.CLOCK, nextCarouselLabel(StatLabels.STARS, available))
        assertEquals(StatLabels.CLOCK, nextCarouselLabel(StatLabels.MACHINES, available))
        assertEquals(StatLabels.CLOCK, nextCarouselLabel(StatLabels.CLOCK, emptyList()))
        val all = state.copy(cloud = CloudSnapshot(cloud.copy(runnerSteps7d = 1, machines7d = 2), 0))
        assertEquals(listOf(StatLabels.CLOCK, StatLabels.USERS, StatLabels.RUNNER_STEPS,
            StatLabels.TASKS, StatLabels.MACHINES, StatLabels.STARS), carouselStats(all).map { it.label })
    }
}
