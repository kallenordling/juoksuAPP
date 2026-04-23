package com.nordling.juoksu

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nordling.juoksu.databinding.ActivitySessionDetailBinding
import com.nordling.juoksu.db.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class SessionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private lateinit var binding: ActivitySessionDetailBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private var sessionId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Run"

        binding.btnMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java).apply {
                putExtra(MapActivity.EXTRA_SESSION_ID, sessionId)
            })
        }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val s = db.sessionDao().getById(sessionId) ?: run { finish(); return@launch }
            val entries = db.logDao().getBySession(sessionId)

            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val endMs = s.endTime ?: s.startTime
            val durationSec = (endMs - s.startTime) / 1000
            val km = s.distanceMeters / 1000.0
            val avgKmh = if (durationSec > 0) km / (durationSec / 3600.0) else 0.0

            val maxKmh = entries.maxOfOrNull { it.speedMps }?.let { it * 3.6f } ?: 0f
            val paceMinPerKm = if (km > 0) (durationSec / 60.0) / km else 0.0
            val paceMin = paceMinPerKm.toInt()
            val paceSec = ((paceMinPerKm - paceMin) * 60).toInt()

            binding.textName.text = s.name
            binding.textStart.text = "Start: ${df.format(Date(s.startTime))}"
            binding.textEnd.text = "End: ${if (s.endTime != null) df.format(Date(s.endTime)) else "—"}"
            binding.textDistance.text = "Distance: %.3f km".format(km)
            binding.textDuration.text = "Duration: %d:%02d:%02d".format(
                durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60
            )
            binding.textAvgSpeed.text = "Avg speed: %.1f km/h".format(avgKmh)
            binding.textMaxSpeed.text = "Max speed: %.1f km/h".format(maxKmh)
            binding.textPace.text = "Pace: %d:%02d /km".format(paceMin, paceSec)
            binding.textSamples.text = "GPS samples: ${entries.size}"
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete run?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.logDao().deleteBySession(sessionId)
                    db.sessionDao().deleteById(sessionId)
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
