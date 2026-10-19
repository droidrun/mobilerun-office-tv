package ai.droidrun.officetv

/** At most three launches in a rolling minute; a fourth attempt opens a five-minute escape window. */
class TakeoverLimiter(private val now: () -> Long) {
    private val launches = ArrayDeque<Long>()
    private var pausedUntil = 0L

    fun allowLaunch(): Boolean {
        val time = now()
        if (time < pausedUntil) return false
        while (launches.isNotEmpty() && time - launches.first() >= 60_000) launches.removeFirst()
        if (launches.size >= 3) {
            pausedUntil = time + 300_000
            launches.clear()
            return false
        }
        launches.addLast(time)
        return true
    }
}
