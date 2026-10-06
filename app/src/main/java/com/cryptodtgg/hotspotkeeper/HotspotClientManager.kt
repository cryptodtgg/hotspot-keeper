package com.cryptodtgg.hotspotkeeper

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.lang.reflect.Method

/**
 * Reads connected hotspot clients via reflection on WifiManager.
 * Android doesn't expose a public API for this, so we call the hidden
 * getWifiApState / getClientList methods. Works on most stock Android
 * builds; Samsung and some OEMs may need different method names.
 */
class HotspotClientManager(private val context: Context) {

    companion object {
        private const val TAG = "HotspotClients"
    }

    data class Client(
        val macAddress: String,
        val ipAddress: String,
        val hostname: String,
        val connectedSinceMs: Long
    ) {
        fun displayName(): String {
            return if (hostname.isNotBlank() && hostname != "null") hostname else macAddress
        }
    }

    fun getConnectedClients(): List<Client> {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
        return try {
            val method: Method = wm.javaClass.getDeclaredMethod("getWifiApState")
            method.isAccessible = true
            val apState = method.invoke(wm) as? Int ?: return emptyList()

            // getClientList(int, int) — first arg is max clients, second is flag
            val clientMethod: Method = wm.javaClass.getDeclaredMethod(
                "getClientList", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            clientMethod.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val list = clientMethod.invoke(wm, 20, 0) as? List<Any> ?: return emptyList()

            list.mapNotNull { entry ->
                try {
                    val mac = entry.javaClass.getField("clientMac").get(entry) as? String ?: return@mapNotNull null
                    val ip = entry.javaClass.getField("clientIp").get(entry) as? String ?: ""
                    val host = entry.javaClass.getField("clientName").get(entry) as? String ?: ""
                    val since = try {
                        entry.javaClass.getField("clientTime").get(entry) as? Long ?: 0L
                    } catch (_: Exception) { 0L }
                    Client(mac, ip, host, since)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse client entry", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getClientList reflection failed", e)
            emptyList()
        }
    }

    fun kickClient(macAddress: String): Boolean {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            // disconnectWifiClient(String) — hidden method on some builds
            val method = wm.javaClass.getDeclaredMethod("disconnectWifiClient", String::class.java)
            method.isAccessible = true
            method.invoke(wm, macAddress)
            Log.d(TAG, "Kicked client $macAddress")
            true
        } catch (e: Exception) {
            Log.e(TAG, "disconnectWifiClient failed for $macAddress", e)
            false
        }
    }

    fun kickAll(): Int {
        val clients = getConnectedClients()
        var kicked = 0
        for (c in clients) {
            if (kickClient(c.macAddress)) kicked++
        }
        return kicked
    }
}