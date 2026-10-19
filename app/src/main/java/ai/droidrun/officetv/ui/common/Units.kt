package ai.droidrun.officetv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The mockups are specified in CSS container-width units (1cqw = 1% of the screen width).
 * The root provides the real value (9.6 dp on the 1920x1080 / 320 dpi TV), so every design scales
 * from the same numbers as the HTML reference.
 */
val LocalCq = compositionLocalOf { 9.6.dp }

@Composable
@ReadOnlyComposable
fun cq(v: Float): Dp = LocalCq.current * v

@Composable
@ReadOnlyComposable
fun cqSp(v: Float): TextUnit = (LocalCq.current.value * v).sp
