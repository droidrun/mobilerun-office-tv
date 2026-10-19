package ai.droidrun.officetv.data

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class SeriesPoint(val time: String, val count: Double)
data class CloudStats(
    val usersTotal: Long?,
    val usersNew7d: Long?,
    val usersPerDay: List<SeriesPoint>,
    val runnerSteps7d: Long?,
    val averageSteps7d: Double?,
    val tasksPerHour: List<SeriesPoint>,
    val machines7d: Long?,
) {
    /** Sum of the hourly series; null until the series has loaded. */
    val tasks24h: Long? get() = tasksPerHour.takeIf { it.isNotEmpty() }?.sumOf { it.count }?.toLong()
}

object StatsParser {
    private val columns = listOf("users_total", "users_new_7d", "users_per_day", "task_runner_steps_7d",
        "avg_steps_per_task_7d", "tasks_per_hour_24h", "machines_provisioned_7d")

    fun parse(text: String): CloudStats {
        val root = JSONObject(text)
        val names = root.getJSONArray("columns")
        val row = root.getJSONArray("results").getJSONArray(0)
        val indices = (0 until names.length()).associate { names.getString(it) to it }
        fun value(name: String): Any? = indices[name]?.let { row.opt(it) }
        fun number(name: String): Double? = (value(name) as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0 }
        fun series(name: String): List<SeriesPoint> {
            val array = value(name) as? JSONArray ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val pair = array.optJSONArray(index) ?: return@mapNotNull null
                val time = pair.opt(0) as? String ?: return@mapNotNull null
                val count = (pair.opt(1) as? Number)?.toDouble() ?: return@mapNotNull null
                if (count.isFinite() && count >= 0) SeriesPoint(time, count) else null
            }.sortedBy { it.time }
        }
        return CloudStats(number("users_total")?.toLong(), number("users_new_7d")?.toLong(),
            series("users_per_day"), number("task_runner_steps_7d")?.toLong(),
            number("avg_steps_per_task_7d"), series("tasks_per_hour_24h"),
            number("machines_provisioned_7d")?.toLong())
    }

    /** Persist only the whitelisted aggregates; never persist the raw endpoint response. */
    fun encode(stats: CloudStats): JSONObject {
        fun series(points: List<SeriesPoint>) = JSONArray().apply {
            points.forEach { put(JSONArray().put(it.time).put(it.count)) }
        }
        val values = listOf(stats.usersTotal, stats.usersNew7d, series(stats.usersPerDay), stats.runnerSteps7d,
            stats.averageSteps7d, series(stats.tasksPerHour), stats.machines7d)
        return JSONObject().put("columns", JSONArray(columns)).put("results", JSONArray().put(
            JSONArray().apply { values.forEach { put(it ?: JSONObject.NULL) } },
        ))
    }
}

/** Thirty UTC calendar days including today, keeping gaps in their actual date positions. */
fun dailySlots(points: List<SeriesPoint>, today: LocalDate): List<SeriesPoint> {
    val counts = points.associate { it.time to it.count }
    return (29L downTo 0L).map { daysAgo ->
        val date = today.minusDays(daysAgo).toString()
        SeriesPoint(date, counts[date] ?: 0.0)
    }
}
