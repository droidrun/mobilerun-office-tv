package ai.droidrun.officetv.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** One shared screen signal for UI polling and the accessibility service. */
class ScreenState(context: Context, scope: CoroutineScope) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val clockRevision = MutableStateFlow(0L)
    val clockChanges = clockRevision.asStateFlow()
    fun isInteractive(): Boolean = power.isInteractive

    // Screen and clock actions are protected system broadcasts; API <33 has no export flag.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    val interactive = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    clockRevision.value++
                }
                trySend(isInteractive())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        // Read after registration so a concurrent screen transition cannot be missed.
        trySend(isInteractive())
        awaitClose { context.unregisterReceiver(receiver) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), isInteractive())
}
