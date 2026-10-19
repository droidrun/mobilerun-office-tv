package ai.droidrun.officetv.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import ai.droidrun.officetv.data.formatNumber
import ai.droidrun.officetv.R
import ai.droidrun.officetv.ui.common.Geist
import ai.droidrun.officetv.ui.common.JetBrainsMono
import ai.droidrun.officetv.ui.common.Txt
import ai.droidrun.officetv.ui.common.LocalCq
import ai.droidrun.officetv.ui.common.cqSp
import ai.droidrun.officetv.ui.common.cq
import kotlinx.coroutines.delay

internal val Background = Color(0xFFFAFAF8)
internal val Surface = Color.White
internal val Ink = Color(0xFF0A0A0A)
internal val Secondary = Color(0xFF4A4A4A)
internal val Muted = Color(0xFF6B6B6B)
internal val Rule = Color(0xFFE8E6E1)
internal val MobilerunBlue = Color(0xFF1D4ED8)
internal val LocalMotionEnabled = compositionLocalOf { true }

@Composable
internal fun motionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    var enabled by remember(resolver) { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = enabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/** Shared design units, bounded translation, and a dimming scrim without an offscreen alpha buffer. */
@Composable
internal fun BurnInFrame(hour: Int, content: @Composable BoxScope.() -> Unit) {
    val motion = LocalMotionEnabled.current
    var offset by remember { mutableStateOf(BurnInProtection.offsetAt(SystemClock.elapsedRealtime())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(BurnInProtection.INTERVAL_MS - SystemClock.elapsedRealtime() % BurnInProtection.INTERVAL_MS)
            offset = BurnInProtection.offsetAt(SystemClock.elapsedRealtime())
        }
    }
    val duration = if (motion) 2_000 else 0
    val x by animateFloatAsState(offset.first.toFloat(), tween(duration), label = "drift x")
    val y by animateFloatAsState(offset.second.toFloat(), tween(duration), label = "drift y")
    val dim by animateFloatAsState(if (BurnInProtection.isNight(hour)) 0.55f else 1f,
        tween(duration), label = "night dim")
    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        // Preserve the 16:9 design at any viewport size or reported density.
        val unit = minOf(maxWidth / 100, maxHeight / 56.25f)
        CompositionLocalProvider(LocalCq provides unit) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = x * density
                translationY = y * density
            }.drawWithContent {
                drawContent()
                if (dim < 1f) drawRect(Color.Black.copy(alpha = 1f - dim))
            }, content = content)
        }
    }
}

@Composable
internal fun Brand(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Image(painterResource(R.drawable.mobilerun_wordmark), contentDescription = "Mobilerun",
            modifier = Modifier.height(cq(2.106f)).aspectRatio(786f / 122f))
    }
}

/** Each right-aligned slot retains its own transition; separators and unchanged digits stay still. */
@Composable
internal fun RollingNumber(value: Number, sizeCq: Float, modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Light, demo: Boolean = false) {
    if (demo) {
        Txt(formatNumber(value, demo = true), sizeCq, Ink, Geist, modifier = modifier,
            weight = weight, tabularNums = true)
        return
    }
    val text = formatNumber(value)
    var shown by remember { mutableStateOf(text) }
    // On growth, first compose new leading slots empty so they can roll in too.
    LaunchedEffect(text) { shown = text }
    val motion = LocalMotionEnabled.current
    val reversed = shown.reversed()
    val density = LocalDensity.current
    val fontSize = cqSp(sizeCq)
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = fontSize, fontFamily = Geist,
        fontWeight = weight, fontFeatureSettings = "tnum")
    val digitWidth = remember(measurer, style, density) {
        with(density) {
            ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }.toDp()
        }
    }
    // Convert sp through the current font scale, just like the glyphs. The separator is a thin space.
    val separatorWidth = with(density) { (fontSize.toPx() * 0.28f).toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            val place = text.lastIndex - index
            key(place) {
                AnimatedContent(targetState = reversed.getOrNull(place) ?: ' ',
                    modifier = Modifier.width(if (char == ' ') separatorWidth else digitWidth).clipToBounds(),
                    transitionSpec = {
                        val duration = if (motion && initialState != targetState) 600 else 0
                        val pause = if (duration > 0) place * 40 else 0
                        slideInVertically(tween(duration, pause, FastOutSlowInEasing)) { it } togetherWith
                            slideOutVertically(tween(duration, pause, FastOutSlowInEasing)) { -it }
                    }, label = "digit $place") { digit ->
                    Txt(if (digit == ' ') "" else digit.toString(), sizeCq, Ink, Geist,
                        weight = weight, tabularNums = true)
                }
            }
        }
    }
}

@Composable
internal fun UsersNumber(value: Long, demo: Boolean = false) {
    if (demo) {
        RollingNumber(value, 5.75f, weight = FontWeight.ExtraLight, demo = true)
        return
    }
    var previous by remember { mutableStateOf(value) }
    var increase by remember { mutableStateOf<Long?>(null) }
    val opacity = remember { Animatable(0f) }
    val motion = LocalMotionEnabled.current
    LaunchedEffect(value, motion) {
        val delta = value - previous
        previous = value
        increase = null
        opacity.snapTo(0f)
        if (delta > 0 && motion) {
            increase = delta
            opacity.animateTo(1f, tween(200))
            delay(4_000)
            opacity.animateTo(0f, tween(400))
            increase = null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.7f))) {
        RollingNumber(value, 5.75f, weight = FontWeight.ExtraLight)
        increase?.let {
            Txt("+${formatNumber(it)}", 1.8f, MobilerunBlue, JetBrainsMono,
                modifier = Modifier.graphicsLayer { alpha = opacity.value })
        }
    }
}
