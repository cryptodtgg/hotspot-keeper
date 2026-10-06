package com.cryptodtgg.hotspotkeeper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Lightweight receiver that samples data usage every 15 minutes without a
 * foreground service. No notification, no wake lock — just a quick read of
 * TrafficStats and a write to SharedPreferences.
 */
class DataSamplingReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DataSampling"
        const val ACTION_SAMPLE = "com.cryptodtgg.hotspotkeeper.ACTION_SAMPLE"
        private const val REQUEST_CODE = 4242
        private const val INTERVAL_MS = 15 * 60 * 1000L

        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DataSamplingReceiver::class.java).apply {
                action = ACTION_SAMPLE
            }
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, INTERVAL_MS, pi)
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DataSamplingReceiver::class.java).apply {
                action = ACTION_SAMPLE
            }
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.cancel(pi)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SAMPLE) return
        try {
            val tracker = DataUsageTracker(context)
            tracker.recordSample()
            Log.d(TAG, "Sample recorded: ${tracker.formatBytes(tracker.getSessionUsageBytes())}")
        } catch (e: Exception) {
            Log.e(TAG, "Sampling failed", e)
        }
        // Re-arm for the next interval
        schedule(context)
    }
}