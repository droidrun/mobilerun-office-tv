package ai.droidrun.officetv.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.droidrun.officetv.data.*
import ai.droidrun.officetv.Bypass
import ai.droidrun.officetv.ui.common.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val Sans = Geist
private val Mono = JetBrainsMono

/** Monochrome text-only design and width units, arranged to the office TV mockup. */
@Composable
fun MonoScreen(
    clock: ClockState,
    stats: StatsState,
    airPlayName: String?,
    calendar: CalendarDisplay?,
    now: Instant,
) {
    val context = LocalContext.current
    var heldSelect by remember { mutableStateOf(false) }
    val input = Modifier.onPreviewKeyEvent { event ->
        if (event.key !in SELECT_KEYS) {
            false
        } else if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0) {
            if (!heldSelect) {
                heldSelect = true
                Bypass.pause()
                Toast.makeText(context, "Umleitung 30 s pausiert", Toast.LENGTH_SHORT).show()
            }
            true
        } else if (event.type == KeyEventType.KeyUp && heldSelect) {
            heldSelect = false
            true // A held OK must not also activate the focused tile.
        } else {
            if (event.type == KeyEventType.KeyDown) heldSelect = false
            false
        }
    }
    Column(Modifier.fillMaxSize().then(input).padding(horizontal = cq(4f), vertical = cq(2.4f))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cq(2.4f))) {
            Brand(Modifier.weight(1f))
            if (airPlayName != null) {
                Row(Modifier.border(1.dp, Rule, RoundedCornerShape(50)).padding(horizontal = cq(0.9f), vertical = cq(0.4f)),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.6f))) {
                    Canvas(Modifier.size(cq(0.5f))) { drawCircle(MobilerunBlue) }
                    Txt("AirPlay bereit", 1.3f, Muted, Mono)
                }
            }
        }
        Spacer(Modifier.height(cq(1.4f)))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1.05f).fillMaxHeight().padding(end = cq(3f))) {
                Txt(clock.time, 9.5f, Ink, Sans, weight = FontWeight.ExtraLight,
                    spacingEm = -0.045f, lineHeightEm = 0.98f, tabularNums = true)
                Txt(clock.date, 1.885f, Secondary, Sans, modifier = Modifier.padding(top = cq(0.6f)), weight = FontWeight.Light)
                if (calendar != null) {
                    CalendarHero(calendar, now, Modifier.weight(1f).fillMaxWidth().padding(top = cq(2.6f)))
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Rule))
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = cq(3f)),
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(cq(0.2f))) {
                    Label("Mobilerun")
                    stats.cloud?.stats?.let { cloud ->
                        cloud.usersTotal?.let {
                            UsersNumber(it, demo = stats.demo)
                            Label(StatLabels.USERS, accentWord = StatLabels.accent(StatLabels.USERS))
                        }
                        cloud.usersNew7d?.let { Txt("+${formatNumber(it, demo = stats.demo)} in 7 Tagen", 1.56f, Muted, Sans, weight = FontWeight.Light) }
                        if (cloud.usersPerDay.isNotEmpty()) {
                            val days = remember(cloud.usersPerDay, clock.utcDate) {
                                dailySlots(cloud.usersPerDay, clock.utcDate)
                            }
                            Bars(days, clock.utcDate)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Txt("vor 30 Tagen", 1.235f, Muted, Mono)
                                Txt("heute", 1.235f, Muted, Mono)
                            }
                        }
                        if (cloud.tasksPerHour.isNotEmpty()) {
                            Spacer(Modifier.height(cq(0.2f)))
                            Label("Tasks pro Stunde · 24 h")
                            Sparkline(cloud.tasksPerHour, clock.utcHour)
                        }
                    }
                    stats.statsStatus?.let { Txt(it, 1.365f, Muted, Sans) }
                }
                Column {
                    val cloud = stats.cloud?.stats
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(StatLabels.RUNNER_STEPS, cloud?.runnerSteps7d,
                            cloud?.averageSteps7d?.let { "Ø ${formatNumber(it, demo = stats.demo)} je Task" }, Modifier.weight(1f), demo = stats.demo)
                        StatCell(StatLabels.AGENT_TURNS, cloud?.agentTurns7d, null, Modifier.weight(1f), demo = stats.demo)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(StatLabels.MACHINES, cloud?.machines7d, null, Modifier.weight(1f), demo = stats.demo)
                        StatCell(StatLabels.STARS, stats.stars?.count, null, Modifier.weight(1f), demo = stats.demo)
                    }
                    // Cloud stats and stars retain their own successful-fetch timestamps.
                    val fetchedAt = stats.cloud?.fetchedAt ?: stats.stars?.fetchedAt
                    fetchedAt?.let {
                        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                        Txt("Stand $time", 1.3f, Muted, Mono, modifier = Modifier.padding(top = cq(0.5f)))
                    }
                }
            }
        }
        Spacer(Modifier.height(cq(1.1f)))
        AppBar()
    }
}

@Composable
private fun CalendarHero(calendar: CalendarDisplay, now: Instant, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(cq(0.7f))) {
        Label(StatLabels.CALENDAR, accentWord = StatLabels.accent(StatLabels.CALENDAR))
        val event = calendar.event
        if (event == null) {
            Txt(calendar.status, 2.6f, Muted, Sans, modifier = Modifier.fillMaxWidth(),
                weight = FontWeight.Light, maxLines = 2)
        } else {
            // Reserve the time/status/then rows first; a long title stays inside the left column.
            Txt(event.title, 5.4f, MobilerunBlue, InstrumentSerif,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                lineHeightEm = 1.05f, maxLines = 2)
            Txt("${calendarTime(event.start)}–${calendarTime(event.end)}", 2.8f, Ink, Geist,
                weight = FontWeight.Light, tabularNums = true)
            // Minute-driven and deliberately unanimated: no timer, per-frame redraw, or extra layer.
            val progress = calendarProgress(event, now)
            Canvas(Modifier.fillMaxWidth().padding(top = cq(0.3f)).height(cq(0.14f))) {
                drawRect(Rule)
                drawRect(MobilerunBlue, size = Size(size.width * progress, size.height))
            }
            Txt(calendar.status, 1.6f, MobilerunBlue, Geist, modifier = Modifier.fillMaxWidth())
            calendar.then?.let { then ->
                Row(Modifier.fillMaxWidth().padding(top = cq(0.6f)),
                    horizontalArrangement = Arrangement.spacedBy(cq(0.6f)), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Danach: ${calendarTime(then.start)}", 1.4f, Muted, Mono, tabularNums = true)
                    Txt(then.title, 1.9f, Muted, InstrumentSerif, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, accentWord: String? = null) =
    Txt(text, 1.235f, Muted, Mono, spacingEm = 0.04f, accentWord = accentWord)

@Composable
private fun StatCell(label: String, value: Number?, sub: String?, modifier: Modifier, demo: Boolean) {
    // Leave blank space for aggregates that have never loaded, without inventing zeroes.
    Column(modifier.heightIn(min = cq(6.2f)).background(Surface).border(width = 0.5.dp, color = Rule)
        .padding(horizontal = cq(0.6f), vertical = cq(0.5f)),
        verticalArrangement = Arrangement.spacedBy(cq(0.25f))) {
        if (value != null) {
            Label(label)
            // Keep the average beside the number to leave room for larger TV text.
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(cq(0.6f))) {
                RollingNumber(value, 2.415f, modifier = Modifier.weight(1f, fill = false), demo = demo)
                if (sub != null) Txt(sub, 1.235f, Muted, Sans)
            }
        }
    }
}

@Composable
private fun Bars(points: List<SeriesPoint>, today: LocalDate) {
    Canvas(Modifier.fillMaxWidth().height(cq(3.2f))) {
        val max = points.maxOf { it.count }.coerceAtLeast(1.0)
        val step = size.width / points.size
        points.forEachIndexed { index, point ->
            val height = (point.count / max * size.height).toFloat()
            drawRect(if (point.time.take(10) == today.toString()) MobilerunBlue else Muted,
                Offset(index * step, size.height - height), Size(step * 0.7f, height))
        }
    }
}

@Composable
private fun AppBar() {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    // Only the two escape routes; Einstellungen always starts focused.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cq(1.2f))) {
        AppTile("Einstellungen", Modifier.weight(1f), first, gear = true) { AppLauncher.settings(context) }
        AppTile("Fire TV Startseite", Modifier.weight(1f)) { AppLauncher.amazonHome(context) }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

@Composable
private fun AppTile(label: String, modifier: Modifier, focus: FocusRequester? = null, gear: Boolean = false, click: () -> Unit) {
    Focusable(modifier.height(cq(4.4f)), focus, click) { focused ->
        val ink = if (focused) Color.White else Secondary
        Row(Modifier.fillMaxSize().background(if (focused) MobilerunBlue else Surface, RoundedCornerShape(cq(0.6f)))
            .border(1.dp, Rule, RoundedCornerShape(cq(0.6f)))
            .padding(horizontal = cq(0.5f)), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cq(0.6f), Alignment.CenterHorizontally)) {
            if (gear) Gear(ink)
            Txt(label, 1.365f, ink, Sans, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Gear(color: Color) {
    Canvas(Modifier.size(cq(1.35f))) {
        val path = Path()
        repeat(32) { index ->
            val angle = index * Math.PI * 2 / 32
            val radius = size.minDimension * if (index % 4 in 1..2) 0.48f else 0.36f
            val x = center.x + cos(angle).toFloat() * radius
            val y = center.y + sin(angle).toFloat() * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color, style = Stroke(size.minDimension * 0.07f))
        drawCircle(color, radius = size.minDimension * 0.14f, style = Stroke(size.minDimension * 0.07f))
    }
}
