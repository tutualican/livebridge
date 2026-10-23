package com.appsfolder.livebridge.liveupdate

internal object ChargingStatusPolicy {
    const val UNKNOWN_PERCENT = -1

    fun shouldShow(plugged: Boolean, percent: Int): Boolean {
        if (!plugged) {
            return false
        }
        if (percent == UNKNOWN_PERCENT) {
            return true
        }
        return percent in 0..99
    }

    fun isFullyCharged(percent: Int): Boolean = percent >= 100

    fun formatRemaining(remainingMs: Long, hourUnit: String, minuteUnit: String): String? {
        if (remainingMs <= 0L) {
            return null
        }
        val totalMinutes = ((remainingMs + 59_999L) / 60_000L).toInt()
        if (totalMinutes <= 0) {
            return null
        }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0 -> "$minutes $minuteUnit"
            minutes <= 0 -> "$hours $hourUnit"
            else -> "$hours $hourUnit $minutes $minuteUnit"
        }
    }
}
