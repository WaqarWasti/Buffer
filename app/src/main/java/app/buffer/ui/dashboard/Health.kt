package app.buffer.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.buffer.service.AppWatcherService

data class Health(val accessibility: Boolean, val overlay: Boolean, val battery: Boolean) {
    val ok get() = accessibility && overlay && battery
}

/** Re-checked every time the user comes back to the app (e.g. from system Settings). */
@Composable
fun rememberHealth(): Health {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return remember(tick) { checkHealth(context) }
}

private fun checkHealth(context: Context): Health {
    val component = ComponentName(context, AppWatcherService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val accessibility = enabledServices.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return Health(
        accessibility = accessibility,
        overlay = Settings.canDrawOverlays(context),
        battery = power.isIgnoringBatteryOptimizations(context.packageName),
    )
}

fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

fun overlayIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

fun batteryIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
