package app.buffer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BufferConfig(
    val enabled: Set<String> = emptySet(),
    val defaultSeconds: Int = 15,
    val perApp: Map<String, Int> = emptyMap(),
    val minutesPerOpen: Int = 10,
) {
    fun secondsFor(pkg: String): Int = perApp[pkg] ?: defaultSeconds
}

/** All user settings. Lives in plain SharedPreferences, local to the device. */
object Store {
    private lateinit var prefs: SharedPreferences
    private val _config = MutableStateFlow(BufferConfig())
    val config: StateFlow<BufferConfig> = _config

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("buffer", Context.MODE_PRIVATE)
        _config.value = load()
    }

    private fun load(): BufferConfig {
        val per = mutableMapOf<String, Int>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(APP_PREFIX) && value is Int) per[key.removePrefix(APP_PREFIX)] = value
        }
        return BufferConfig(
            enabled = prefs.getStringSet(KEY_ENABLED, emptySet())?.toSet() ?: emptySet(),
            defaultSeconds = prefs.getInt(KEY_DEFAULT_SECONDS, 15),
            perApp = per,
            minutesPerOpen = prefs.getInt(KEY_MINUTES, 10),
        )
    }

    fun setEnabled(packages: Collection<String>, on: Boolean) {
        val set = _config.value.enabled.toMutableSet()
        if (on) set.addAll(packages) else set.removeAll(packages.toSet())
        prefs.edit().putStringSet(KEY_ENABLED, set).apply()
        _config.value = _config.value.copy(enabled = set)
    }

    fun setDefaultSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_DEFAULT_SECONDS, seconds).apply()
        _config.value = _config.value.copy(defaultSeconds = seconds)
    }

    /** Pass null to remove the override and use the default again. */
    fun setAppSeconds(pkg: String, seconds: Int?) {
        val edit = prefs.edit()
        val map = _config.value.perApp.toMutableMap()
        if (seconds == null) {
            edit.remove(APP_PREFIX + pkg)
            map.remove(pkg)
        } else {
            edit.putInt(APP_PREFIX + pkg, seconds)
            map[pkg] = seconds
        }
        edit.apply()
        _config.value = _config.value.copy(perApp = map)
    }

    fun setMinutesPerOpen(minutes: Int) {
        prefs.edit().putInt(KEY_MINUTES, minutes).apply()
        _config.value = _config.value.copy(minutesPerOpen = minutes)
    }

    private const val KEY_ENABLED = "enabled"
    private const val KEY_DEFAULT_SECONDS = "default_seconds"
    private const val KEY_MINUTES = "minutes_per_open"
    private const val APP_PREFIX = "sec_"
}
