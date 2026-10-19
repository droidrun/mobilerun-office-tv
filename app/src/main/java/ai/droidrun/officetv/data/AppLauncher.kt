package ai.droidrun.officetv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import ai.droidrun.officetv.Bypass

/** TV launch pattern with an explicit bypass for both escape routes. */
object AppLauncher {
    fun settings(context: Context) {
        Bypass.pause()
        start(context, Intent(Settings.ACTION_SETTINGS), onMissing = { amazonHome(context) })
    }

    fun amazonHome(context: Context) {
        Bypass.pause()
        start(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setPackage("com.amazon.tv.launcher"))
    }

    private fun start(context: Context, intent: Intent, onMissing: (() -> Unit)? = null) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            if (onMissing != null) onMissing()
            else Toast.makeText(context, "App nicht verfügbar", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "App kann nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }
}
