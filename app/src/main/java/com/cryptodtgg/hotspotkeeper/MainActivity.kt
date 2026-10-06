package com.cryptodtgg.hotspotkeeper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            updateStatus()
        } else {
            Toast.makeText(this, "Permissions required for hotspot control", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        toggleButton.setOnClickListener {
            if (!hasAllPermissions()) {
                permissionLauncher.launch(requiredPermissions)
                return@setOnClickListener
            }
            val running = HotspotKeeperService.isRunning
            if (running) {
                stopService(Intent(this, HotspotKeeperService::class.java))
                toggleButton.text = getString(R.string.start_keeper)
                statusText.text = getString(R.string.status_off)
            } else {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, HotspotKeeperService::class.java)
                )
                toggleButton.text = getString(R.string.stop_keeper)
                statusText.text = getString(R.string.status_on)
            }
        }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateStatus() {
        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val running = HotspotKeeperService.isRunning
        toggleButton.text = getString(if (running) R.string.stop_keeper else R.string.start_keeper)
        statusText.text = getString(if (running) R.string.status_on else R.string.status_off)
    }
}