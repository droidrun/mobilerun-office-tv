package ai.droidrun.officetv.data

class Backoff {
    private var next = 60_000L
    fun failureDelay(): Long = next.also { next = (next * 2).coerceAtMost(3_600_000) }
    fun reset() { next = 60_000 }
}

/** Keeps the next attempt when a screen stops/starts, so visibility changes cannot hammer a source. */
class PollSchedule(
    private val intervalMs: Long,
    lastSuccessAt: Long? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val backoff = Backoff()
    private var nextAt = lastSuccessAt?.plus(intervalMs) ?: 0L
    fun remainingDelay(): Long = (nextAt - now()).coerceIn(0, 3_600_000)
    fun succeeded(at: Long) { backoff.reset(); nextAt = at + intervalMs }
    fun failed() { nextAt = now() + backoff.failureDelay() }
    fun retryNow() { backoff.reset(); nextAt = 0 }
}
