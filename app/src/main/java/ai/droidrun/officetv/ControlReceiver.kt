package ai.droidrun.officetv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ai.droidrun.officetv.data.isValidStatsKey
import ai.droidrun.officetv.data.isValidCalendarUrl
import ai.droidrun.officetv.data.provisionSecret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The manifest requires DUMP: shell/system can provision; ordinary third-party apps cannot. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == SHOW_TEST_REMINDER) {
            val app = context.applicationContext as OfficeApplication
            resultData = if (app.showTestReminder?.invoke() == true) "shown" else "unavailable"
            return
        }
        if (action !in setOf(SET, CLEAR, SET_CALENDAR, CLEAR_CALENDAR, CALENDAR_STATUS)) return
        val key = if (action == SET) intent.getStringExtra("key") else null
        val url = if (action == SET_CALENDAR) intent.getStringExtra("url") else null
        val pending = goAsync()
        val app = context.applicationContext as OfficeApplication
        app.scope.launch(Dispatchers.IO) {
            try {
                if (action == CALENDAR_STATUS) {
                    pending.resultData = if (app.calendarUrl.hasValue) "set" else "unset"
                    return@launch
                }
                val calendar = action == SET_CALENDAR || action == CLEAR_CALENDAR
                val result = if (calendar) {
                    val repository = app.calendar
                    synchronized(repository) {
                        provisionSecret(url, action == CLEAR_CALENDAR, ::isValidCalendarUrl,
                            repository::discardCache, app.calendarUrl::store, app.calendarUrl::clear)
                    }
                } else {
                    provisionSecret(key, action == CLEAR, ::isValidStatsKey,
                        store = app.keys::store, remove = app.keys::clear)
                }
                pending.resultData = result
                if (result != "invalid") {
                    val label = if (calendar) "calendar url" else "stats key"
                    Log.i("OfficeTV", "$label $result")
                }
            } catch (_: Exception) {
                pending.resultData = "invalid"
                // Never log exceptions from crypto/provisioning, input, headers or response bodies.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val SET = "ai.droidrun.officetv.SET_STATS_KEY"
        const val CLEAR = "ai.droidrun.officetv.CLEAR_STATS_KEY"
        const val SET_CALENDAR = "ai.droidrun.officetv.SET_CALENDAR_URL"
        const val CLEAR_CALENDAR = "ai.droidrun.officetv.CLEAR_CALENDAR_URL"
        const val CALENDAR_STATUS = "ai.droidrun.officetv.CALENDAR_STATUS"
        const val SHOW_TEST_REMINDER = "ai.droidrun.officetv.SHOW_TEST_REMINDER"
    }
}
