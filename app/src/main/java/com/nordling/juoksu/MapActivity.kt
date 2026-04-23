package com.nordling.juoksu

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nordling.juoksu.databinding.ActivityMapBinding
import com.nordling.juoksu.db.AppDatabase
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private lateinit var binding: ActivityMapBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(this@MapActivity, getPreferences(MODE_PRIVATE))
            userAgentValue = packageName
        }

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Route"

        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)

        loadTrack(sessionId)
    }

    private fun loadTrack(sessionId: Long) {
        lifecycleScope.launch {
            val entries = if (sessionId > 0) db.logDao().getBySession(sessionId) else emptyList()
            binding.mapView.overlays.clear()
            if (entries.isEmpty()) {
                binding.textNoGps.visibility = View.VISIBLE
                binding.mapView.invalidate()
                return@launch
            }
            binding.textNoGps.visibility = View.GONE

            val points = entries.map { GeoPoint(it.latitude, it.longitude) }
            val speeds = entries.map { it.speedMps }
            val minSpeed = speeds.minOrNull() ?: 0f
            val maxSpeed = speeds.maxOrNull() ?: 0f

            for (i in 0 until points.size - 1) {
                binding.mapView.overlays.add(Polyline().apply {
                    setPoints(listOf(points[i], points[i + 1]))
                    outlinePaint.color = speedColor(speeds[i], minSpeed, maxSpeed)
                    outlinePaint.strokeWidth = 10f
                })
            }

            binding.mapView.overlays.add(Marker(binding.mapView).apply {
                position = points.first()
                title = "Start: ${timeFormat.format(Date(entries.first().timestamp))}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
            if (points.size > 1) {
                binding.mapView.overlays.add(Marker(binding.mapView).apply {
                    position = points.last()
                    title = "End: ${timeFormat.format(Date(entries.last().timestamp))}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }

            val bbox = BoundingBox.fromGeoPoints(points)
            binding.mapView.post { binding.mapView.zoomToBoundingBox(bbox.increaseByScale(1.3f), true) }
        }
    }

    private fun speedColor(value: Float, min: Float, max: Float): Int {
        val t = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0.5f
        val hue = (1f - t) * 240f
        return Color.HSVToColor(floatArrayOf(hue, 1f, 0.9f))
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
