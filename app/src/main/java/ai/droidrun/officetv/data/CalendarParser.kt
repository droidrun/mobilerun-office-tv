package ai.droidrun.officetv.data

import org.json.JSONObject
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val calendarUrlPattern = Regex(
    "^https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]{20,200}/exec\\?token=[A-Za-z0-9]{32,128}$",
)
fun isValidCalendarUrl(url: String?): Boolean = url != null && calendarUrlPattern.matches(url)

/** Absolute, default-port HTTPS only; no credentials, fragments, or lookalike hosts. */
fun isAllowedCalendarRedirect(location: String?): Boolean = runCatching {
    val uri = URI(location ?: return false)
    uri.scheme == "https" && uri.host == "script.googleusercontent.com" &&
        uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null && uri.rawPath.startsWith("/")
}.getOrDefault(false)

data class CalendarEvent(val title: String, val start: Instant, val end: Instant)
data class CalendarSnapshot(val events: List<CalendarEvent>, val generatedAt: Instant)
data class CalendarDisplay(val event: CalendarEvent?, val status: String, val then: CalendarEvent? = null)
data class CalendarState(val configured: Boolean = false, val snapshot: CalendarSnapshot? = null) {
    /** Null hides unconfigured calendars; an old/missing snapshot cannot claim there are no events. */
    fun displayAt(now: Instant, zone: ZoneId = ZoneId.systemDefault()): CalendarDisplay? {
        if (!configured) return null
        if (snapshot == null || snapshot.generatedAt.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()) {
            return CalendarDisplay(null, "Kalender nicht erreichbar")
        }
        val events = todayCalendarEvents(snapshot.events, now, zone)
        val event = events.firstOrNull()
        return CalendarDisplay(event, calendarCountdown(event, now, zone), events.getOrNull(1))
    }
}

object CalendarParser {
    /** Invalid/auth-failure payloads fail rather than overwriting a last good snapshot with no events. */
    fun parse(text: String): CalendarSnapshot {
        val root = JSONObject(text)
        val generatedAt = Instant.parse(root.getString("generatedAt"))
        val events = listOf("next", "then").mapNotNull { key ->
            val value = root.get(key)
            if (value == JSONObject.NULL) return@mapNotNull null
            val event = value as? JSONObject ?: error("Invalid calendar event")
            val title = event.get("title") as? String ?: error("Invalid calendar title")
            val start = Instant.parse(event.getString("start"))
            val end = Instant.parse(event.getString("end"))
            require(end > start)
            CalendarEvent(title, start, end)
        }.sortedBy { it.start }
        return CalendarSnapshot(events, generatedAt)
    }

    /** Store only the display contract, never the raw response or provisioned URL. */
    fun encode(snapshot: CalendarSnapshot): String {
        fun event(index: Int): Any = snapshot.events.getOrNull(index)?.let {
            JSONObject().put("title", it.title).put("start", it.start.toString()).put("end", it.end.toString())
        } ?: JSONObject.NULL
        return JSONObject().put("generatedAt", snapshot.generatedAt.toString())
            .put("next", event(0)).put("then", event(1)).toString()
    }
}

fun todayCalendarEvents(events: List<CalendarEvent>, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
    events.filter { it.start.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() && it.end > now }
        .sortedBy { it.start }

private val calendarTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
fun calendarTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    calendarTimeFormat.withZone(zone).format(instant)

fun calendarDreamTime(event: CalendarEvent, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    if (event.start <= now && event.end > now) "bis ${calendarTime(event.end, zone)}" else calendarTime(event.start, zone)

/** The final hour fills toward the start; during a meeting, show its elapsed share. */
fun calendarProgress(event: CalendarEvent, now: Instant): Float {
    if (now >= event.end) return 1f
    val fraction = if (now < event.start) {
        1.0 - Duration.between(now, event.start).toMillis() / 3_600_000.0
    } else {
        Duration.between(event.start, now).toMillis().toDouble() /
            Duration.between(event.start, event.end).toMillis().coerceAtLeast(1)
    }
    return fraction.toFloat().coerceIn(0f, 1f)
}

fun calendarCountdown(event: CalendarEvent?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    if (event == null || event.end <= now) return "Heute keine Termine mehr"
    if (event.start <= now) return "läuft gerade · bis ${calendarTime(event.end, zone)}"
    val minutes = (Duration.between(now, event.start).seconds + 59) / 60
    return if (minutes < 60) "in $minutes Min."
    else if (minutes % 60 == 0L) "in ${minutes / 60} Std."
    else "in ${minutes / 60} Std. ${minutes % 60} Min."
}
