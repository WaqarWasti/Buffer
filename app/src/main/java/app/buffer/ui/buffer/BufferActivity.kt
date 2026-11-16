package app.buffer.ui.buffer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import app.buffer.data.Attempts
import app.buffer.data.Store
import app.buffer.service.AppWatcherService
import app.buffer.ui.theme.BufferTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BufferActivity : ComponentActivity() {

    private data class Target(val pkg: String, val attemptId: Long)

    private var target by mutableStateOf<Target?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (!readTarget(intent)) {
            finish()
            return
        }

        // Back is "no": the buffer exists to get you out, not to trap you.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = decline()
        })

        setContent {
            BufferTheme {
                val current = target
                if (current != null) {
                    key(current) {
                        val label = appLabel(current.pkg)
                        val seconds = Store.config.value.secondsFor(current.pkg)
                        val minutes = Store.config.value.minutesPerOpen
                        val stats by produceState<BufferStats?>(null, current) {
                            value = withContext(Dispatchers.IO) {
                                val now = System.currentTimeMillis()
                                BufferStats(
                                    attempts24h = Attempts.count(current.pkg, now - Attempts.DAY_MS),
                                    savedMinutes = Attempts.declined(current.pkg, 0, current.attemptId) * minutes,
                                    lastOpenedMs = Attempts.lastContinued(current.pkg),
                                )
                            }
                        }
                        BufferScreen(
                            appLabel = label,
                            seconds = seconds,
                            stats = stats,
                            onDecline = ::decline,
                            onContinue = { continueToApp(current) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTarget(intent)
    }

    private fun readTarget(intent: Intent): Boolean {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return false
        target = Target(pkg, intent.getLongExtra(EXTRA_ATTEMPT_ID, -1))
        AppWatcherService.bufferShowing = pkg
        return true
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun decline() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun continueToApp(t: Target) {
        Attempts.markContinued(t.attemptId)
        AppWatcherService.foreground = t.pkg
        packageManager.getLaunchIntentForPackage(t.pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Leaving by any other route (home, recents, screen off) ends the buffer. Counts as declined.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AppWatcherService.bufferShowing == target?.pkg) AppWatcherService.bufferShowing = null
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_ATTEMPT_ID = "attempt_id"
    }
}

data class BufferStats(
    val attempts24h: Int,
    val savedMinutes: Int,
    val lastOpenedMs: Long?,
)
