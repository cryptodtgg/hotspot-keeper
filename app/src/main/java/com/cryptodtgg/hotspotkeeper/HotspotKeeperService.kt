package com.cryptodtgg.hotspotkeeper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class HotspotKeeperService : Service() {

    companion object {
        private const val TAG = "HotspotKeeper"
        private const val CHANNEL_ID = "hotspot_keeper_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 30_000L
        const val ACTION_UPDATE = "com.cryptodtgg.hotspotkeeper.ACTION_UPDATE"
        @Volatile
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wifiManager: WifiManager? = null
    private var dataTracker: DataUsageTracker? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                ensureHotspotOn()
                dataTracker?.recordSample()
                sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName))
            } catch (e: Exception) {
                Log.e(TAG, "Error checking hotspot", e)
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        dataTracker = DataUsageTracker(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        dataTracker?.startSession()
        startForegroundNotification()
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        dataTracker?.stopSession()
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureHotspotOn() {
        val wm = wifiManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val method = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
                method.isAccessible = true
                val enabled = method.invoke(wm) as? Boolean ?: false
                if (!enabled) {
                    val startMethod = wm.javaClass.getDeclaredMethod("startSoftAp")
                    startMethod.isAccessible = true
                    startMethod.invoke(wm)
                    Log.d(TAG, "Hotspot was off — turned it back on")
                } else {
                    Log.d(TAG, "Hotspot is on")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reflection failed, trying fallback", e)
                fallbackToggle(wm)
            }
        } else {
            fallbackToggle(wm)
        }
    }

    private fun fallbackToggle(wm: WifiManager) {
        try {
            val isEnabled = wm.isWifiApEnabled
            if (!isEnabled) {
                val config = wm.wifiApConfiguration
                val method = wm.javaClass.getMethod(
                    "setWifiApEnabled",
                    android.net.wifi.WifiConfiguration::class.java,
                    Boolean::class.javaPrimitiveType
                )
                method.invoke(wm, config, true)
                Log.d(TAG, "Fallback: hotspot turned on")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback failed", e)
        }
    }

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val sessionMb = dataTracker?.formatBytes(dataTracker?.getSessionUsageBytes() ?: 0L) ?: "0 B"
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text) + " — $sessionMb this session")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}