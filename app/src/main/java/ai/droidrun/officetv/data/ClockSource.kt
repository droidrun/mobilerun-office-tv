package ai.droidrun.officetv.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ClockState(
    val time: String,
    val date: String,
    val utcDate: LocalDate,
    val utcHour: Long,
)

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

fun clockStateAt(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): ClockState {
    val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    return ClockState(
        time = "%02d:%02d".format(t.hour, t.minute),
        date = dateFormat.format(t),
        utcDate = t.withZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
        utcHour = epochMs / 3_600_000L,
    )
}

/** Emits once immediately and then on every minute boundary. */
fun clockFlow(nowMs: () -> Long = System::currentTimeMillis): Flow<ClockState> = flow {
    while (true) {
        val now = nowMs()
        emit(clockStateAt(now))
        delay(60_000L - now % 60_000L + 50L)
    }
}
