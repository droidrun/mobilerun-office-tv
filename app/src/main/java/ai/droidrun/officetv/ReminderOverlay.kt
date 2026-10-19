package ai.droidrun.officetv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import ai.droidrun.officetv.data.CalendarEvent
import ai.droidrun.officetv.data.calendarTime
import ai.droidrun.officetv.ui.MobilerunBlue
import ai.droidrun.officetv.ui.Muted
import ai.droidrun.officetv.ui.Rule
import ai.droidrun.officetv.ui.Secondary
import ai.droidrun.officetv.ui.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Small, silent service window. It cannot receive focus, touches, or remote keys. Main thread only. */
class ReminderOverlay(private val context: Context, private val scope: CoroutineScope) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var card: View? = null
    var lifetime: Job? = null
        private set

    fun show(event: CalendarEvent): Job? {
        // A concurrent test request cannot replace a visible calendar card.
        if (card != null) return null
        val width = windows.currentWindowMetrics.bounds.width()
        val unit = width / 100f
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            background = GradientDrawable().apply {
                setColor(Surface.toArgb())
                setStroke(context.resources.displayMetrics.density.roundToInt().coerceAtLeast(1), Rule.toArgb())
            }
            val padding = (unit * 2).roundToInt()
            setPadding(padding, padding, padding, padding)
            addView(View(context).apply { setBackgroundColor(MobilerunBlue.toArgb()) },
                LinearLayout.LayoutParams((unit * 6).roundToInt(), (unit * 0.35f).roundToInt().coerceAtLeast(2)))
            fun text(value: String, size: Float, font: Int, color: Int, lines: Int = 1) {
                addView(TextView(context).apply {
                    text = value
                    typeface = context.resources.getFont(font)
                    // Width-relative pixels keep the bounded title inside the card at any font scale.
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * size)
                    setTextColor(color)
                    maxLines = lines
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                    setPadding(0, (unit * 0.8f).roundToInt(), 0, 0)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            text(context.getString(R.string.reminder_label), 1.4f, R.font.jetbrains_mono_regular, Muted.toArgb())
            text(event.title, 4.2f, R.font.instrument_serif_italic, MobilerunBlue.toArgb(), lines = 2)
            text("${calendarTime(event.start)}–${calendarTime(event.end)}", 1.8f, R.font.geist_regular, Secondary.toArgb())
        }
        val animated = ValueAnimator.areAnimatorsEnabled()
        view.alpha = if (animated) 0f else 1f
        view.translationY = if (animated) -unit * 3 else 0f
        val params = WindowManager.LayoutParams(
            (width * 0.55f).roundToInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (unit * 3).roundToInt()
        }
        try {
            windows.addView(view, params)
        } catch (_: WindowManager.BadTokenException) {
            return null // Service is no longer connected; never open an activity as a fallback.
        } catch (_: IllegalStateException) {
            return null
        }
        card = view
        lifetime = scope.launch {
            try {
                if (animated) animate(view, 1f, 0f, 400)
                delay(10_000)
                if (ValueAnimator.areAnimatorsEnabled()) animate(view, 0f, 0f, 600)
            } finally {
                remove(view)
            }
        }
        return lifetime
    }

    private suspend fun animate(view: View, alpha: Float, y: Float, duration: Long) {
        suspendCancellableCoroutine { continuation ->
            view.animate().alpha(alpha).translationY(y).setDuration(duration).withEndAction {
                if (continuation.isActive) continuation.resume(Unit)
            }.start()
            continuation.invokeOnCancellation { view.animate().cancel() }
        }
    }

    private fun remove(view: View) {
        view.animate().cancel()
        if (card === view) {
            card = null
            windows.removeViewImmediate(view)
        }
    }

    fun hide() {
        lifetime?.cancel()
        lifetime = null
        card?.let(::remove)
    }
}
