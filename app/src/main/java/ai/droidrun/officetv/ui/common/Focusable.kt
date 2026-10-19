package ai.droidrun.officetv.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/** TV focus primitive. The screen handles held OK before tile activation. */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    Box(base.onKeyEvent { event ->
        if (event.key !in SELECT_KEYS) return@onKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> { down = true; true }
            KeyEventType.KeyUp -> {
                if (down && !event.nativeKeyEvent.isCanceled) onClick()
                down = false
                true
            }
            else -> false
        }
    }.onFocusChanged { focused = it.isFocused; if (!focused) down = false }.focusable()) { content(focused) }
}
