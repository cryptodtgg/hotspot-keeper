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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflate
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
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
            refreshClients()
        } else {
            Toast.makeText(this, "Permissions required for hotspot control", Toast.LENGTH_LONG).show()
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatus()
            updateChart()
            refreshClients()
        }
    }

    private val clientRefreshHandler = Handler(Looper.getMainLooper())
    private val clientRefreshRunnable = object : Runnable {
        override fun run() {
            refreshClients()
            clientRefreshHandler.postDelayed(this, 10_000L)
        }
    }

    private var clientAdapter: ClientAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val dataText = findViewById<TextView>(R.id.dataText)
        val chart = findViewById<LineChart>(R.id.usageChart)
        val clientList = findViewById<ListView>(R.id.clientList)
        val kickAllButton = findViewById<Button>(R.id.kickAllButton)

        setupChart(chart)

        clientAdapter = ClientAdapter(this, R.layout.item_client, mutableListOf())
        clientList.adapter = clientAdapter
        clientList.setOnItemClickListener { _, _, position, _ ->
            val client = clientAdapter?.getItem(position) ?: return@setOnItemClickListener
            kickClient(client)
        }

        kickAllButton.setOnClickListener {
            val mgr = HotspotClientManager(this)
            val kicked = mgr.kickAll()
            Toast.makeText(this, "Kicked $kicked client(s)", Toast.LENGTH_SHORT).show()
            refreshClients()
        }

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
            refreshClients()
        }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
        updateStatus()
        updateChart()
        refreshClients()
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
        clientRefreshHandler.post(clientRefreshRunnable)
        updateStatus()
        updateChart()
        refreshClients()
    }

    override fun onPause() {
        super.onPause()
        clientRefreshHandler.removeCallbacks(clientRefreshRunnable)
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

    private fun refreshClients() {
        val mgr = HotspotClientManager(this)
        val clients = mgr.getConnectedClients()
        clientAdapter?.updateClients(clients)
        val countText = findViewById<TextView>(R.id.clientCountText)
        countText.text = getString(R.string.client_count_format, clients.size)
    }

    private fun kickClient(client: HotspotClientManager.Client) {
        val mgr = HotspotClientManager(this)
        val ok = mgr.kickClient(client.macAddress)
        Toast.makeText(
            this,
            if (ok) "Kicked ${client.displayName()}" else "Couldn't kick ${client.displayName()}",
            Toast.LENGTH_SHORT
        ).show()
        refreshClients()
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

    private class ClientAdapter(
        context: Context,
        @LayoutRes private val resource: Int,
        private val clients: MutableList<HotspotClientManager.Client>
    ) : ArrayAdapter<HotspotClientManager.Client>(context, resource, clients) {

        fun updateClients(newClients: List<HotspotClientManager.Client>) {
            clients.clear()
            clients.addAll(newClients)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflate.from(context).inflate(resource, parent, false)
            val client = getItem(position)!!
            view.findViewById<TextView>(R.id.clientName).text = client.displayName()
            view.findViewById<TextView>(R.id.clientMac).text = client.macAddress
            view.findViewById<TextView>(R.id.clientIp).text = client.ipAddress
            return view
        }
    }
}