package com.cryptodtgg.hotspotkeeper

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats

class DataUsageTracker(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "hotspot_data_usage"
        private const val KEY_BASELINE_RX = "baseline_rx"
        private const val KEY_BASELINE_TX = "baseline_tx"
        private const val KEY_SESSION_START_RX = "session_start_rx"
        private const val KEY_SESSION_START_TX = "session_start_tx"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_POINTS = 60
    }

    fun startSession() {
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        prefs.edit()
            .putLong(KEY_BASELINE_RX, rx)
            .putLong(KEY_BASELINE_TX, tx)
            .putLong(KEY_SESSION_START_RX, rx)
            .putLong(KEY_SESSION_START_TX, tx)
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .apply()
    }

    fun stopSession() {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, false).apply()
    }

    fun isSessionActive(): Boolean = prefs.getBoolean(KEY_SESSION_ACTIVE, false)

    fun getSessionUsageBytes(): Long {
        if (!isSessionActive()) return 0L
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L) - prefs.getLong(KEY_SESSION_START_RX, 0L)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L) - prefs.getLong(KEY_SESSION_START_TX, 0L)
        return (rx + tx).coerceAtLeast(0L)
    }

    fun getTotalUsageBytes(): Long {
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L) - prefs.getLong(KEY_BASELINE_RX, 0L)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L) - prefs.getLong(KEY_BASELINE_TX, 0L)
        return (rx + tx).coerceAtLeast(0L)
    }

    fun recordSample() {
        val usage = getSessionUsageBytes()
        val history = getHistory().toMutableList()
        history.add(usage)
        while (history.size > MAX_HISTORY_POINTS) history.removeAt(0)
        prefs.edit().putString(KEY_HISTORY, history.joinToString(",")).apply()
    }

    fun getHistory(): List<Long> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}