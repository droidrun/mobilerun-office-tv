package ai.droidrun.officetv.ui

import ai.droidrun.officetv.data.StatsState
import ai.droidrun.officetv.data.CalendarEvent

/** Stable, bounded positions; monotonic time keeps clock/time-zone changes from jumping the layout. */
object BurnInProtection {
    const val INTERVAL_MS = 180_000L
    private val offsets = listOf(0 to 0, 6 to 3, 3 to -6, -6 to -3, -3 to 6, 6 to -6, -6 to 6)

    fun offsetAt(elapsedMs: Long): Pair<Int, Int> =
        offsets[Math.floorMod(elapsedMs / INTERVAL_MS, offsets.size.toLong()).toInt()]

    fun isNight(hour: Int): Boolean = hour >= 22 || hour < 7
}

internal object StatLabels {
    const val CLOCK = "Uhrzeit"
    const val CALENDAR = "Nächster Termin"
    const val USERS = "Nutzer in der Cloud"
    const val RUNNER_STEPS = "Task Runner Steps · 7 T"
    const val TASKS = "Tasks · 24 h"
    const val MACHINES = "Cloud-Maschinen · 7 T"
    const val STARS = "GitHub-Stars"

    fun accent(label: String): String? = when (label) {
        CALENDAR -> "Termin"
        USERS -> "Cloud"
        RUNNER_STEPS -> "Steps"
        TASKS -> "Tasks"
        MACHINES -> "Maschinen"
        STARS -> "Stars"
        else -> null
    }
}

/** Clock and calendar are nonnumeric; unloaded statistics are omitted. */
internal data class Stat(val label: String, val value: Long? = null, val event: CalendarEvent? = null)

internal fun carouselStats(stats: StatsState, event: CalendarEvent? = null): List<Stat> = buildList {
    add(Stat(StatLabels.CLOCK))
    event?.let { add(Stat(StatLabels.CALENDAR, event = it)) }
    val cloud = stats.cloud?.stats
    cloud?.usersTotal?.let { add(Stat(StatLabels.USERS, it)) }
    cloud?.runnerSteps7d?.let { add(Stat(StatLabels.RUNNER_STEPS, it)) }
    cloud?.tasks24h?.let { add(Stat(StatLabels.TASKS, it)) }
    cloud?.machines7d?.let { add(Stat(StatLabels.MACHINES, it)) }
    stats.stars?.let { add(Stat(StatLabels.STARS, it.count)) }
}

internal fun nextCarouselLabel(current: String, available: List<Stat>): String {
    if (available.isEmpty()) return StatLabels.CLOCK
    return available[(available.indexOfFirst { it.label == current } + 1) % available.size].label
}
