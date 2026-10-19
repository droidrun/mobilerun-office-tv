package ai.droidrun.officetv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.droidrun.officetv.BuildConfig
import ai.droidrun.officetv.OfficeApplication
import ai.droidrun.officetv.data.AirPlayDiscovery
import ai.droidrun.officetv.data.clockFlow
import ai.droidrun.officetv.data.clockStateAt
import java.time.Instant
import java.time.LocalTime

@Composable
fun OfficeContent(interactive: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as OfficeApplication
    val clocks = remember { clockFlow() }
    val clock by clocks.collectAsStateWithLifecycle(clockStateAt(System.currentTimeMillis()))
    val stats by app.statsState.collectAsStateWithLifecycle()
    val calendar by app.calendarState.collectAsStateWithLifecycle()
    val now = remember(clock) { Instant.now() }
    val calendarDisplay = remember(calendar, now) { calendar.displayAt(now) }
    val hour = remember(clock) { LocalTime.now().hour }
    CompositionLocalProvider(LocalMotionEnabled provides motionEnabled()) {
        BurnInFrame(hour) {
            if (interactive && BuildConfig.OFFICETV_DEMO) {
                MonoScreen(clock, stats, null, calendarDisplay, now)
            } else if (interactive) {
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                val discovery = remember { AirPlayDiscovery(context.applicationContext) }
                val airPlayName by discovery.name.collectAsStateWithLifecycle()
                DisposableEffect(lifecycle, discovery) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> discovery.start()
                            Lifecycle.Event.ON_STOP -> discovery.stop()
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) discovery.start()
                    onDispose { lifecycle.removeObserver(observer); discovery.stop() }
                }
                MonoScreen(clock, stats, airPlayName, calendarDisplay, now)
            } else DreamScreen(clock, stats, calendarDisplay?.event, now)
        }
    }
}
