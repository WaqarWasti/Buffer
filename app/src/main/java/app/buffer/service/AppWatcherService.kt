package app.buffer.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import app.buffer.data.Attempts
import app.buffer.data.Store
import app.buffer.ui.buffer.BufferActivity

/**
 * Watches which app comes to the foreground. It reads only the package name of window changes,
 * never screen content (canRetrieveWindowContent=false), and shows the buffer when a blocked
 * app is opened from somewhere else.
 */
class AppWatcherService : AccessibilityService() {

    private var imePackages: Set<String> = emptySet()
    private var imeCheckedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "service connected; enabled=${Store.config.value.enabled}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) {
            Log.d(TAG, "own window; fg=$foreground -> $pkg")
            foreground = pkg
            return
        }
        // While the buffer is up, the app underneath may still emit events. They say nothing
        // about where the user is, so they must not change what we think is in front.
        if (pkg == bufferShowing) {
            Log.d(TAG, "$pkg: ignored, buffer is showing for it")
            return
        }
        if (isTransient(pkg)) {
            Log.d(TAG, "$pkg: ignored, transient")
            return
        }

        val previous = foreground
        foreground = pkg
        if (previous == pkg) return // moving around inside the same app

        val monitored = pkg in Store.config.value.enabled
        Log.d(TAG, "$pkg: fg $previous -> $pkg, monitored=$monitored")
        if (!monitored) return
        if (bufferShowing == pkg) return

        Log.i(TAG, "$pkg: TRIGGER buffer")
        val attemptId = Attempts.record(pkg)
        val intent = Intent(this, BufferActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(BufferActivity.EXTRA_PACKAGE, pkg)
            .putExtra(BufferActivity.EXTRA_ATTEMPT_ID, attemptId)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    /** System UI, keyboards and dialogs that appear on top of an app without leaving it. */
    private fun isTransient(pkg: String): Boolean {
        if (pkg in TRANSIENT_PACKAGES) return true
        val now = System.currentTimeMillis()
        if (now - imeCheckedAt > 60_000) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
            imeCheckedAt = now
        }
        return pkg in imePackages
    }

    companion object {
        const val TAG = "BufferSvc"

        /** Package currently showing the buffer, so we never stack a second one. */
        @Volatile
        var bufferShowing: String? = null

        /** Package we believe is in front. Buffer sets this when the user continues, so the app they chose doesn't re-trigger. */
        @Volatile
        var foreground: String? = null

        private val TRANSIENT_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.intentresolver",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}
