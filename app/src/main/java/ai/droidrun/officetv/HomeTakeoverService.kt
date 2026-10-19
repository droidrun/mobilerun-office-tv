package ai.droidrun.officetv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import ai.droidrun.officetv.data.CalendarEvent
import ai.droidrun.officetv.data.reminderDelayMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant

/** Fire OS consumes HOME before key filtering; the Amazon window transition is the trigger. */
class HomeTakeoverService : AccessibilityService() {
    private val limiter = TakeoverLimiter(SystemClock::elapsedRealtime)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as OfficeApplication
    private val overlay by lazy { ReminderOverlay(this, serviceScope) }
    private var reminders: Job? = null
    private val testReminder: () -> Boolean = {
        val start = Instant.now().plusSeconds(60)
        app.screen.isInteractive() && overlay.show(CalendarEvent("Test Review", start, start.plusSeconds(1800))) != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        app.showTestReminder = testReminder
        reminders?.cancel()
        reminders = serviceScope.launch {
            combine(app.calendarState, app.screen.interactive, app.screen.clockChanges) { calendar, on, _ -> calendar to on }
                .collectLatest { (calendar, on) ->
                    if (!on) {
                        overlay.hide()
                        return@collectLatest
                    }
                    while (true) {
                        val event = app.reminderHistory.next(calendar, Instant.now()) ?: break
                        val wait = reminderDelayMillis(event, Instant.now()) ?: continue
                        delay(wait)
                        // Replanning or a test card must not cut short an already visible reminder.
                        overlay.lifetime?.join()
                        val now = Instant.now()
                        if (event.start <= now) continue
                        val lifetime = overlay.show(event)
                        if (lifetime == null) {
                            delay(15_000)
                            continue
                        }
                        app.reminderHistory.markShown(event, now)
                        lifetime.join()
                    }
                }
        }
    }

    override fun onDestroy() {
        if (app.showTestReminder === testReminder) app.showTestReminder = null
        serviceScope.cancel()
        overlay.hide()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName?.toString() == "com.amazon.tv.launcher" &&
            event.className?.toString() == "com.amazon.tv.launcher.ui.HomeActivity_vNext" &&
            !Bypass.isPaused() && limiter.allowLaunch()
        ) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            ))
        }
    }

    override fun onInterrupt() = Unit
}
