package ai.droidrun.officetv.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ai.droidrun.officetv.data.SeriesPoint
import ai.droidrun.officetv.ui.common.cq
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import java.time.Instant

@Composable
internal fun Sparkline(points: List<SeriesPoint>, utcHour: Long) {
    // Keep real hour positions and zero-filled gaps, matching the existing home chart.
    val hours = remember(points, utcHour) {
        val counts = points.mapNotNull { point ->
            runCatching { Instant.parse(point.time).epochSecond / 3600 to point.count }.getOrNull()
        }.toMap()
        (utcHour - 23..utcHour).map { counts[it] ?: 0.0 }
    }
    val max = remember(hours) { hours.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0 }
    val motion = LocalMotionEnabled.current
    val reveal = remember { Animatable(1f) }
    val latestPoints by rememberUpdatedState(points)
    LaunchedEffect(motion) {
        reveal.snapTo(1f)
        // Ignore the initial/cached value. Hour-window shifts never enter this flow.
        if (motion) snapshotFlow { latestPoints }.drop(1).collectLatest {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(1_200, easing = LinearOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxWidth().height(cq(3.6f))) {
        // Cache geometry and paint separately from the halo's per-frame drawing/layer.
        Box(Modifier.matchParentSize().graphicsLayer().drawWithCache {
            val inset = 5.dp.toPx().coerceAtMost(size.height / 4)
            fun point(index: Int) = Offset(inset + index * (size.width - 2 * inset) / 23,
                size.height - inset - (hours[index] / max * (size.height - 2 * inset)).toFloat())
            val line = Path().apply {
                hours.indices.forEach { index ->
                    val p = point(index)
                    if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            val end = point(23)
            val area = Path().apply {
                addPath(line)
                lineTo(end.x, size.height)
                lineTo(point(0).x, size.height)
                close()
            }
            val brush = Brush.verticalGradient(listOf(MobilerunBlue.copy(alpha = 0.35f), MobilerunBlue.copy(alpha = 0f)))
            val stroke = Stroke(1.5.dp.toPx())
            onDrawBehind {
                drawLine(Rule, Offset(0f, size.height), Offset(size.width, size.height))
                clipRect(right = size.width * reveal.value) {
                    drawPath(area, brush)
                    drawPath(line, MobilerunBlue, style = stroke)
                }
                if (reveal.value >= 1f) drawCircle(MobilerunBlue, radius = 2.dp.toPx(), center = end)
            }
        })
        if (motion) {
            val pulse = rememberInfiniteTransition(label = "spark halo").animateFloat(0f, 1f,
                infiniteRepeatable(tween(2_000, easing = LinearEasing)), label = "halo phase")
            Canvas(Modifier.matchParentSize().graphicsLayer()) {
                if (reveal.value >= 1f) {
                    val inset = 5.dp.toPx().coerceAtMost(size.height / 4)
                    val end = Offset(size.width - inset,
                        size.height - inset - (hours.last() / max * (size.height - 2 * inset)).toFloat())
                    val phase = pulse.value
                    drawCircle(MobilerunBlue.copy(alpha = 0.25f * (1f - phase)),
                        radius = (2f + 3f * phase).dp.toPx(), center = end)
                }
            }
        }
    }
}
