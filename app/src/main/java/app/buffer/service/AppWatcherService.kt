package app.buffer.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

    private var foreground: String? = null
    private var imePackages: Set<String> = emptySet()
    private var imeCheckedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) {
            foreground = pkg
            return
        }
        if (isTransient(pkg)) return

        val previous = foreground
        foreground = pkg
        if (previous == pkg) return // moving around inside the same app

        if (pkg !in Store.config.value.enabled) return
        if (consumeAllow(pkg)) return
        if (bufferShowing == pkg) return

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
        /** Package currently showing the buffer, so we never stack a second one. */
        @Volatile
        var bufferShowing: String? = null

        @Volatile
        private var allowPkg: String? = null

        @Volatile
        private var allowUntil = 0L

        /** One-shot pass for the app the user just chose to continue to. Not a timer or pause. */
        fun allowOnce(pkg: String) {
            allowPkg = pkg
            allowUntil = System.currentTimeMillis() + 5_000
        }

        private fun consumeAllow(pkg: String): Boolean {
            val ok = allowPkg == pkg && System.currentTimeMillis() < allowUntil
            if (allowPkg == pkg) allowPkg = null
            return ok
        }

        private val TRANSIENT_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.intentresolver",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}
