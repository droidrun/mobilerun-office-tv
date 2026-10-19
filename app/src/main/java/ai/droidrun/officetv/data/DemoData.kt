package ai.droidrun.officetv.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** Entirely synthetic display data; never reads credentials, caches or the network. */
object DemoData {
    fun stats(now: Instant): StatsState {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        val users = (29 downTo 0).map { daysAgo ->
            val index = 29 - daysAgo
            SeriesPoint(today.minusDays(daysAgo.toLong()).toString(),
                (12 + index * 0.7 + 5 * cos(index * PI / 7)).roundToInt().toDouble())
        }
        val tasks = (23 downTo 0).map { hoursAgo ->
            val time = hour.minus(hoursAgo.toLong(), ChronoUnit.HOURS)
            val utcHour = time.atZone(ZoneOffset.UTC).hour
            SeriesPoint(time.toString(), (160 + 120 * cos((utcHour - 14) * PI / 12)).roundToInt().toDouble())
        }
        return StatsState(
            CloudSnapshot(CloudStats(1_250, 150, users, 7_500, 12.0, tasks, 1_200, 100), now.toEpochMilli()),
            StarsSnapshot(2_500, now.toEpochMilli()),
            demo = true,
        )
    }

    fun calendar(now: Instant, zone: ZoneId = ZoneId.systemDefault()): CalendarState {
        // Stay on today's date even when a screenshot is taken just before midnight.
        val lastStart = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)
        val start = minOf(now.plusSeconds(25 * 60), lastStart)
        val secondStart = minOf(start.plusSeconds(90 * 60), lastStart)
        return CalendarState(true, CalendarSnapshot(listOf(
            CalendarEvent("Weekly review", start, start.plusSeconds(60 * 60)),
            CalendarEvent("Project planning", secondStart, secondStart.plusSeconds(30 * 60)),
        ), now))
    }
}
