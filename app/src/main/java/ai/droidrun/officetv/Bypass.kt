package ai.droidrun.officetv

import android.os.SystemClock

/** Monotonic time: a wall-clock correction cannot extend or end the escape window. */
object Bypass {
    internal var now: () -> Long = SystemClock::elapsedRealtime
    @Volatile private var until = 0L
    fun pause(ms: Long = 30_000) { until = now() + ms.coerceAtLeast(0) }
    fun isPaused(): Boolean = now() < until
}
