package ai.droidrun.officetv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import ai.droidrun.officetv.data.CalendarEvent
import ai.droidrun.officetv.data.calendarDreamTime
import ai.droidrun.officetv.data.calendarCountdown
import java.time.Instant
import ai.droidrun.officetv.data.ClockState
import ai.droidrun.officetv.data.StatsState
import ai.droidrun.officetv.data.formatNumber
import ai.droidrun.officetv.ui.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Carousel dwell is wall-clock time, independent of the system's nonzero animation speed.
private val CarouselDurationScale = object : MotionDurationScale {
    override val scaleFactor = 1f
}

@Composable
internal fun DreamScreen(clock: ClockState, stats: StatsState, event: CalendarEvent?, now: Instant) {
    var selected by remember { mutableStateOf(StatLabels.CLOCK) }
    val available = remember(stats, event) { carouselStats(stats, event) }
    val latestStats by rememberUpdatedState(available)
    val motion = LocalMotionEnabled.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        while (true) {
            if (motion) {
                progress.snapTo(0f)
                withContext(CarouselDurationScale) {
                    progress.animateTo(1f, tween(20_000, easing = LinearEasing))
                }
            } else {
                progress.snapTo(1f)
                delay(20_000)
            }
            selected = nextCarouselLabel(selected, latestStats)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Brand(Modifier.align(Alignment.TopStart).padding(cq(4f)))
        AnimatedContent(selected, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = cq(8f)),
            transitionSpec = {
                val duration = if (motion) 700 else 0
                (fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 12 }) togetherWith
                    (fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { -it / 12 })
            }, label = "stat carousel") { label ->
            val stat = available.firstOrNull { it.label == label } ?: available.first()
            Column(verticalArrangement = Arrangement.spacedBy(cq(0.8f))) {
                val number = stat.value
                val meeting = stat.event
                if (meeting != null) {
                    Txt(calendarDreamTime(meeting, now), 16f, Ink, Geist,
                        weight = FontWeight.ExtraLight, tabularNums = true)
                } else if (number == null) {
                    Txt(clock.time, 16f, Ink, Geist,
                        weight = FontWeight.ExtraLight, tabularNums = true)
                } else RollingNumber(number, 12f, weight = FontWeight.ExtraLight, demo = stats.demo)
                Txt(if (stat.label == StatLabels.CLOCK) clock.date else stat.label, 1.8f, Muted, JetBrainsMono,
                    accentWord = StatLabels.accent(stat.label))
                if (meeting != null) {
                    Txt(meeting.title, 3f, Ink, Geist, modifier = Modifier.fillMaxWidth(), weight = FontWeight.Light)
                    Txt(calendarCountdown(meeting, now), 2f, MobilerunBlue, Geist, weight = FontWeight.Light)
                }
                if (stat.label == StatLabels.USERS) stats.cloud?.stats?.usersNew7d?.let {
                    Txt("+${formatNumber(it, demo = stats.demo)} in 7 Tagen", 2f, MobilerunBlue, Geist,
                        weight = FontWeight.Light)
                }
            }
        }
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(horizontal = cq(4f), vertical = cq(3f)).height(cq(0.08f)).graphicsLayer()) {
            drawLine(Rule, Offset.Zero, Offset(size.width, 0f), strokeWidth = size.height)
            drawLine(MobilerunBlue, Offset.Zero, Offset(size.width * progress.value, 0f), strokeWidth = size.height)
        }
    }
}
