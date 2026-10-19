package ai.droidrun.officetv.data

import android.content.Context
import ai.droidrun.officetv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** One small snapshot, one independent schedule; no credentials or calendar data are logged. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepository(context: Context, private val urls: KeyStore, interactive: StateFlow<Boolean>, scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("calendar_snapshot", Context.MODE_PRIVATE)
    private var snapshot = runCatching {
        CalendarParser.parse(prefs.getString("snapshot", null) ?: return@runCatching null)
    }.getOrNull()
    private val schedule = PollSchedule(5 * 60_000L, snapshot?.generatedAt?.toEpochMilli())
    private var generation = 0L
    private val current = MutableStateFlow(CalendarState(urls.hasValue, snapshot))
    val state = current.asStateFlow()

    /** Also used by the receiver to keep cache discard and credential mutation atomic. */
    @Synchronized fun discardCache() {
        generation++
        snapshot = null
        schedule.retryNow()
        current.value = CalendarState(urls.hasValue)
        check(prefs.edit().clear().commit())
    }

    init {
        if (!BuildConfig.OFFICETV_DEMO) scope.launch(Dispatchers.IO) {
            // Both UI and service subscribe to one poller; the screen gates even a lingering UI collector.
            combine(current.subscriptionCount, interactive) { count, screenOn ->
                count > 0 && screenOn
            }.distinctUntilChanged().collectLatest { active ->
                if (!active) return@collectLatest
                urls.changes.collectLatest {
                    val configured = synchronized(this@CalendarRepository) {
                        current.value = CalendarState(urls.hasValue, snapshot)
                        urls.hasValue
                    }
                    if (configured) poll()
                }
            }
        }
    }

    private suspend fun poll() {
        while (true) {
            delay(synchronized(this) { schedule.remainingDelay() })
            var attemptGeneration = 0L
            try {
                // Read under the receiver's lock; the decrypted URL never crosses a suspension point.
                val body = synchronized(this) {
                    attemptGeneration = generation
                    urls.read()
                }?.let(::get) ?: throw IOException()
                val parsed = CalendarParser.parse(body)
                currentCoroutineContext().ensureActive()
                synchronized(this) {
                    // A request started before provisioning must never restore the discarded calendar.
                    if (attemptGeneration == generation) {
                        snapshot = parsed.copy(events = todayCalendarEvents(parsed.events, Instant.now()))
                        prefs.edit().putString("snapshot", CalendarParser.encode(requireNotNull(snapshot))).apply()
                        schedule.succeeded(System.currentTimeMillis())
                        current.value = CalendarState(urls.hasValue, snapshot)
                    }
                }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                synchronized(this) {
                    if (attemptGeneration == generation) schedule.failed()
                }
            }
        }
    }

    private fun get(url: String): String {
        check(isValidCalendarUrl(url))
        val first = connection(url)
        try {
            if (first.responseCode in setOf(301, 302, 303, 307, 308)) {
                val location = first.getHeaderField("Location")
                if (!isAllowedCalendarRedirect(location)) throw IOException()
                val redirected = connection(requireNotNull(location))
                try { return body(redirected) } finally { redirected.disconnect() }
            }
            return body(first)
        } finally { first.disconnect() }
    }

    private fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 15_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
    }

    private fun body(connection: HttpURLConnection): String {
        if (connection.responseCode !in 200..299 || connection.contentLengthLong > 32_768) throw IOException()
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 32_768) throw IOException()
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }
}
