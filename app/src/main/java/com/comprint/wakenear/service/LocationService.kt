package com.comprint.wakenear.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.comprint.wakenear.MainActivity
import com.comprint.wakenear.Utils
import com.comprint.wakenear.WakeNearApp
import com.comprint.wakenear.alarm.AlarmController

class LocationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"
        const val EXTRA_RADIUS = "radius"
        private const val TAG = "LocationService"
        private const val NOTIFICATION_ID = 1001

        var onLocationUpdate: ((Location) -> Unit)? = null
        var onDistanceUpdate: ((Float) -> Unit)? = null
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var locationManager: LocationManager? = null
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var radius: Float = 500f
    private var alarmTriggered = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocationUpdate?.invoke(location)

            val distance = Utils.haversineDistance(
                location.latitude, location.longitude,
                destLat, destLng
            ).toFloat()

            onDistanceUpdate?.invoke(distance)
            Log.d(TAG, "Distance to destination: ${distance}m (radius: ${radius}m)")

            // Update notification
            val notification = buildNotification("${distance.toInt()}m to destination")
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, notification)

            if (distance <= radius && !alarmTriggered) {
                alarmTriggered = true
                Log.d(TAG, "DESTINATION REACHED! Triggering alarm.")
                val alarmController = AlarmController(this@LocationService)
                alarmController.triggerAlarm()
                stopSelf()
            }
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
                destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0)
                radius = intent.getFloatExtra(EXTRA_RADIUS, 500f)
                alarmTriggered = false
                startForegroundTracking()
            }
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = buildNotification("Tracking your location...")
        startForeground(NOTIFICATION_ID, notification)

        // Acquire wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WakeNear::LocationTracking"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }

        // Start location updates
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // GPS provider (primary)
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L,
                    10f,
                    locationListener
                )
            } catch (e: Exception) {
                Log.e(TAG, "GPS provider not available", e)
            }

            // Network provider (fallback)
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    10f,
                    locationListener
                )
            } catch (e: Exception) {
                Log.e(TAG, "Network provider not available", e)
            }
        }
    }

    private fun stopTracking() {
        locationManager?.removeUpdates(locationListener)
        locationManager = null
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WakeNearApp.CHANNEL_LOCATION)
            .setContentTitle("WakeNear Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}
