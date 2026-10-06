package com.cryptodtgg.hotspotkeeper

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

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

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatus()
            updateChart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val dataText = findViewById<TextView>(R.id.dataText)
        val chart = findViewById<LineChart>(R.id.usageChart)

        setupChart(chart)

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
            updateStatus()
            updateChart()
        }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
        updateStatus()
        updateChart()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(HotspotKeeperService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }
        updateStatus()
        updateChart()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateStatus() {
        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val dataText = findViewById<TextView>(R.id.dataText)
        val running = HotspotKeeperService.isRunning
        toggleButton.text = getString(if (running) R.string.stop_keeper else R.string.start_keeper)
        statusText.text = getString(if (running) R.string.status_on else R.string.status_off)

        val tracker = DataUsageTracker(this)
        val session = tracker.formatBytes(tracker.getSessionUsageBytes())
        val total = tracker.formatBytes(tracker.getTotalUsageBytes())
        dataText.text = getString(R.string.data_usage_format, session, total)
    }

    private fun updateChart() {
        val chart = findViewById<LineChart>(R.id.usageChart)
        val tracker = DataUsageTracker(this)
        val history = tracker.getHistory()
        if (history.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }
        val entries = history.mapIndexed { index, value ->
            Entry(index.toFloat(), value / (1024f * 1024f))
        }
        val dataSet = LineDataSet(entries, "Data used (MB)").apply {
            color = Color.rgb(33, 150, 243)
            setCircleColor(Color.rgb(33, 150, 243))
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    private fun setupChart(chart: LineChart) {
        chart.description = Description().apply { text = "" }
        chart.legend.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(true)
        chart.setTouchEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.animateX(500)
    }
}