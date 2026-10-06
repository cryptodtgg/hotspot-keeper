package com.cryptodtgg.hotspotkeeper

import android.app.Application

class HotspotKeeperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start the lightweight 15-minute sampling alarm so data keeps logging
        // even when the hotspot keeper service is not running.
        DataSamplingReceiver.schedule(this)
    }
}