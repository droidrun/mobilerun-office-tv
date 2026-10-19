package ai.droidrun.officetv.data

import android.content.Context
import ai.droidrun.officetv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CloudSnapshot(val stats: CloudStats, val fetchedAt: Long)
data class StarsSnapshot(val count: Long, val fetchedAt: Long)
data class StatsState(
    val cloud: CloudSnapshot? = null,
    val stars: StarsSnapshot? = null,
    val statsStatus: String? = null,
    val demo: Boolean = false,
)
private data class CloudResult(val snapshot: CloudSnapshot?, val status: String?)

/** Independent source schedules. Cancellation stops polling when neither home nor dream is visible. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepository(context: Context, keys: KeyStore, scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("aggregate_snapshots", Context.MODE_PRIVATE)
    private val statsConfigured = BuildConfig.OFFICETV_STATS_URL.isNotEmpty()

    private fun cachedCloud(): CloudSnapshot? = runCatching {
        val saved = JSONObject(prefs.getString("cloud", null) ?: return null)
        CloudSnapshot(StatsParser.parse(saved.getJSONObject("data").toString()), saved.getLong("fetchedAt"))
    }.getOrNull()

    private fun cachedStars(): StarsSnapshot? = runCatching {
        val saved = JSONObject(prefs.getString("stars", null) ?: return null)
        StarsSnapshot(saved.getLong("count"), saved.getLong("fetchedAt"))
    }.getOrNull()

    private val initialState = StatsState(cachedCloud(), cachedStars(),
        if (statsConfigured) null else STATS_NOT_CONFIGURED)

    private val cloud: Flow<CloudResult> = keys.changes.flatMapLatest {
        flow {
            var snapshot = state.value.cloud
            if (!statsConfigured) {
                emit(CloudResult(snapshot, STATS_NOT_CONFIGURED))
                return@flow
            }
            val schedule = PollSchedule(15 * 60_000L)
            var status: String? = null
            emit(CloudResult(snapshot, status))
            while (true) {
                delay(schedule.remainingDelay())
                try {
                    // The decrypted key exists only during this synchronous request, never across delay/emit.
                    val response = keys.read()?.let { key -> get(BuildConfig.OFFICETV_STATS_URL, key) }
                    currentCoroutineContext().ensureActive()
                    if (response == null) {
                        status = "Stats-Schlüssel fehlt"
                        schedule.failed() // Includes transient keystore failures; retry without deleting the key.
                    } else {
                        val parsed = StatsParser.parse(response)
                        snapshot = CloudSnapshot(parsed, System.currentTimeMillis())
                        prefs.edit().putString("cloud", JSONObject()
                            .put("data", StatsParser.encode(parsed)).put("fetchedAt", snapshot.fetchedAt).toString()).apply()
                        status = null
                        schedule.succeeded(snapshot.fetchedAt)
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    status = if (e is HttpFailure && (e.status == 401 || e.status == 403))
                        "Stats-Schlüssel abgelehnt" else null
                    schedule.failed()
                }
                emit(CloudResult(snapshot, status))
            }
        }.flowOn(Dispatchers.IO)
    }

    private val stars: Flow<StarsSnapshot?> = flow {
        var snapshot = state.value.stars
        val schedule = PollSchedule(60 * 60_000L, snapshot?.fetchedAt)
        emit(snapshot)
        while (true) {
            delay(schedule.remainingDelay())
            try {
                val count = JSONObject(get(GITHUB_URL)).getLong("stargazers_count")
                check(count >= 0)
                currentCoroutineContext().ensureActive()
                snapshot = StarsSnapshot(count, System.currentTimeMillis())
                prefs.edit().putString("stars", JSONObject().put("count", count)
                    .put("fetchedAt", snapshot.fetchedAt).toString()).apply()
                schedule.succeeded(snapshot.fetchedAt)
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                schedule.failed()
            }
            emit(snapshot)
        }
    }.flowOn(Dispatchers.IO)

    val state: StateFlow<StatsState> = combine(cloud, stars) { c, s -> StatsState(c.snapshot, s, c.status) }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), initialState)

    private class HttpFailure(val status: Int) : IOException()

    private fun get(url: String, key: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            // A redirect must never forward the Authorization header to another host.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Droidrun-Office-TV/0.1")
            if (key != null) connection.setRequestProperty("Authorization", "Bearer $key")
            if (connection.responseCode !in 200..299) throw HttpFailure(connection.responseCode)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val body = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (body.length + count > 512_000) throw IOException()
                    body.append(buffer, 0, count)
                }
                body.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val STATS_NOT_CONFIGURED = "Statistik nicht konfiguriert"
        const val GITHUB_URL = "https://api.github.com/repos/" + BuildConfig.OFFICETV_GITHUB_REPO
    }
}
