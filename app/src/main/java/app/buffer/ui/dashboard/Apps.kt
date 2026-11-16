package app.buffer.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap)

/** Every app that has a launcher icon, except Buffer itself. */
suspend fun loadApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(query, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            AppInfo(
                pkg = it.activityInfo.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

val SOCIAL_PACKAGES = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill",
    "com.twitter.android",
    "com.facebook.katana",
    "com.reddit.frontpage",
    "com.snapchat.android",
    "com.google.android.youtube",
    "com.pinterest",
    "com.linkedin.android",
)
