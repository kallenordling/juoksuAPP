package com.nordling.juoksu

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.View
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nordling.juoksu.databinding.ActivityRunBinding

class RunActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRunBinding

    private var service: LoggingService? = null
    private var bound = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val s = service
            if (s != null && LoggingService.isRunning) {
                val startMs = s.sessionStartTime.value ?: 0L
                if (startMs > 0) {
                    val elapsed = System.currentTimeMillis() - startMs
                    binding.textDuration.text = formatDuration(elapsed)
                }
            }
            timerHandler.postDelayed(this, 500)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, b: IBinder) {
            val s = (b as LoggingService.LocalBinder).getService()
            service = s
            bound = true
            s.currentSpeedMps.observe(this@RunActivity) { updateSpeed(it ?: 0f) }
            s.totalDistanceMeters.observe(this@RunActivity) { updateDistance(it ?: 0.0) }
            s.lastLocation.observe(this@RunActivity) { it?.let(::updateLocation) }
            s.goal.observe(this@RunActivity) { updateGoal(it) }
            s.paceStatus.observe(this@RunActivity) { updatePaceStatus(it) }
            updateButton()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRunBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Run"

        binding.btnToggle.setOnClickListener {
            if (LoggingService.isRunning) stopRun() else startRun()
        }
        updateButton()
    }

    override fun onStart() {
        super.onStart()
        if (!LoggingService.isRunning) {
            startRun()
        } else {
            bindService(Intent(this, LoggingService::class.java), connection, 0)
        }
        timerHandler.post(timerRunnable)
    }

    override fun onStop() {
        super.onStop()
        timerHandler.removeCallbacks(timerRunnable)
        if (bound) {
            unbindService(connection)
            bound = false
            service = null
        }
    }

    private fun startRun() {
        val intent = Intent(this, LoggingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, 0)
        updateButton()
    }

    private fun stopRun() {
        if (bound) {
            unbindService(connection)
            bound = false
            service = null
        }
        stopService(Intent(this, LoggingService::class.java))
        updateButton()
        finish()
    }

    private fun updateButton() {
        binding.btnToggle.text = if (LoggingService.isRunning) "Stop Run" else "Start Run"
    }

    private fun updateSpeed(mps: Float) {
        currentSpeed = mps
        val kmh = mps * 3.6f
        binding.textSpeed.text = "%.1f km/h".format(kmh)
        val paceSecPerKm = if (mps > 0.1f) (1000f / mps).toInt() else 0
        binding.textPace.text = if (paceSecPerKm > 0)
            "%d:%02d /km".format(paceSecPerKm / 60, paceSecPerKm % 60)
        else
            "—:— /km"
        updatePaceStatus(service?.paceStatus?.value)
    }

    private fun updateDistance(meters: Double) {
        binding.textDistance.text = "%.2f km".format(meters / 1000.0)
    }

    private var currentSpeed: Float = 0f

    private fun updateGoal(g: Goal?) {
        if (g == null) {
            binding.cardGoal.visibility = View.GONE
        } else {
            binding.cardGoal.visibility = View.VISIBLE
            binding.textTargetPace.text = "Target: ${formatPace(g.paceSecPerKm)}  •  %.1f km/h".format(g.targetSpeedMps * 3.6)
        }
    }

    private fun updatePaceStatus(status: LoggingService.PaceStatus?) {
        val g = service?.goal?.value
        when (status) {
            LoggingService.PaceStatus.AHEAD -> {
                binding.textPaceStatus.text = "▲ AHEAD"
                binding.textPaceStatus.setTextColor(Color.parseColor("#2E7D32"))
            }
            LoggingService.PaceStatus.BEHIND -> {
                binding.textPaceStatus.text = "▼ BEHIND"
                binding.textPaceStatus.setTextColor(Color.parseColor("#C62828"))
            }
            LoggingService.PaceStatus.WAITING -> {
                binding.textPaceStatus.text = "Waiting…"
                binding.textPaceStatus.setTextColor(Color.GRAY)
            }
            else -> {
                binding.textPaceStatus.text = ""
            }
        }
        if (g != null && currentSpeed > 0.3f) {
            val currentPace = 1000.0 / currentSpeed
            val delta = currentPace - g.paceSecPerKm
            val sign = if (delta >= 0) "+" else "-"
            val absDelta = kotlin.math.abs(delta).toInt()
            binding.textPaceDelta.text = "Now ${formatPace(currentPace)}  ($sign%d:%02d vs target)".format(absDelta / 60, absDelta % 60)
        } else {
            binding.textPaceDelta.text = ""
        }
    }

    private fun updateLocation(loc: Location) {
        binding.textGps.text = "GPS: %.5f, %.5f (±%.0f m)".format(
            loc.latitude, loc.longitude, loc.accuracy
        )
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
