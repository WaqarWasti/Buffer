package app.buffer.data

import java.util.Calendar

fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun formatAgo(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val minutes = (nowMs - thenMs) / 60_000
    return when {
        minutes < 1 -> "less than a minute ago"
        minutes < 60 -> "$minutes min ago"
        minutes < 1440 -> "${minutes / 60} h ago"
        else -> "${minutes / 1440} d ago"
    }
}

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
