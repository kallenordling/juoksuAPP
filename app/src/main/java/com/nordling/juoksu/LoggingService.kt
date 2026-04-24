package com.nordling.juoksu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.nordling.juoksu.db.AppDatabase
import com.nordling.juoksu.db.LogEntry
import com.nordling.juoksu.db.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LoggingService : Service() {

    companion object {
        var isRunning = false
            private set
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "juoksu_logging"
        private const val MIN_TIME_MS = 1000L
        private const val MIN_DISTANCE_M = 0f
    }

    inner class LocalBinder : Binder() {
        fun getService(): LoggingService = this@LoggingService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { AppDatabase.getInstance(this) }

    enum class PaceStatus { NO_GOAL, WAITING, AHEAD, BEHIND }

    val lastLocation = MutableLiveData<Location?>()
    val currentSpeedMps = MutableLiveData(0f)
    val totalDistanceMeters = MutableLiveData(0.0)
    val sessionStartTime = MutableLiveData(0L)
    val paceStatus = MutableLiveData(PaceStatus.NO_GOAL)
    val goal = MutableLiveData<Goal?>()

    private var sessionId: Long = -1
    private var previousLocation: Location? = null
    private var distance: Double = 0.0
    private var lastPaceStatus: PaceStatus = PaceStatus.NO_GOAL
    private val PACE_HYSTERESIS_MPS = 0.15f

    private val locationManager by lazy {
        getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private val locationListener = LocationListener { loc ->
        if (loc.accuracy > 50f) return@LocationListener

        previousLocation?.let { prev ->
            val d = prev.distanceTo(loc)
            if (d > 1.5f) {
                distance += d
                totalDistanceMeters.postValue(distance)
            }
        }
        previousLocation = loc

        val speed = if (loc.hasSpeed()) loc.speed else 0f
        currentSpeedMps.postValue(speed)
        lastLocation.postValue(loc)
        evaluatePace(speed)

        val sid = sessionId
        if (sid > 0) {
            scope.launch {
                db.logDao().insert(
                    LogEntry(
                        sessionId = sid,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speedMps = speed,
                        accuracy = loc.accuracy
                    )
                )
            }
        }
        updateNotification(buildNotificationText(speed))
    }

    private fun evaluatePace(speed: Float) {
        val g = goal.value ?: run {
            if (lastPaceStatus != PaceStatus.NO_GOAL) {
                lastPaceStatus = PaceStatus.NO_GOAL
                paceStatus.postValue(PaceStatus.NO_GOAL)
            }
            return
        }
        if (speed < 0.3f) {
            if (lastPaceStatus != PaceStatus.WAITING) {
                lastPaceStatus = PaceStatus.WAITING
                paceStatus.postValue(PaceStatus.WAITING)
            }
            return
        }
        val target = g.targetSpeedMps.toFloat()
        val next = when (lastPaceStatus) {
            PaceStatus.AHEAD -> if (speed < target - PACE_HYSTERESIS_MPS) PaceStatus.BEHIND else PaceStatus.AHEAD
            PaceStatus.BEHIND -> if (speed > target + PACE_HYSTERESIS_MPS) PaceStatus.AHEAD else PaceStatus.BEHIND
            else -> if (speed >= target) PaceStatus.AHEAD else PaceStatus.BEHIND
        }
        if (next != lastPaceStatus) {
            val wasTransition = lastPaceStatus == PaceStatus.AHEAD || lastPaceStatus == PaceStatus.BEHIND
            lastPaceStatus = next
            paceStatus.postValue(next)
            if (wasTransition) vibrate(next)
        }
    }

    private fun vibrate(status: PaceStatus) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = if (status == PaceStatus.AHEAD) longArrayOf(0, 120) else longArrayOf(0, 200, 120, 200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun buildNotificationText(speed: Float): String {
        val base = "%.2f km  •  %.1f km/h".format(distance / 1000.0, speed * 3.6f)
        return when (lastPaceStatus) {
            PaceStatus.AHEAD -> "$base  ▲ ahead"
            PaceStatus.BEHIND -> "$base  ▼ behind"
            else -> base
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        isRunning = true
        val loadedGoal = GoalStore.get(this)
        goal.postValue(loadedGoal)
        lastPaceStatus = if (loadedGoal == null) PaceStatus.NO_GOAL else PaceStatus.WAITING
        paceStatus.postValue(lastPaceStatus)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for GPS…"))

        val start = System.currentTimeMillis()
        sessionStartTime.postValue(start)
        scope.launch {
            val name = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(start))
            sessionId = db.sessionDao().insert(Session(name = name, startTime = start))
        }
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { locationManager.removeUpdates(locationListener) } catch (_: SecurityException) {}
        val endTime = System.currentTimeMillis()
        val finalDistance = distance
        val sid = sessionId
        if (sid > 0) {
            runBlocking {
                db.sessionDao().finish(sid, endTime, finalDistance)
            }
        }
        scope.cancel()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, locationListener
            )
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, locationListener
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JuoksuApp", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Run tracking" }
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, RunActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking run")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
