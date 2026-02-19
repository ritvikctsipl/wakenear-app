package com.comprint.wakenear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration

class WakeNearApp : Application() {

    companion object {
        const val CHANNEL_LOCATION = "location_tracking"
        const val CHANNEL_ALARM = "alarm_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize osmdroid
        val config = Configuration.getInstance()
        config.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        config.userAgentValue = packageName

        // Create notification channels
        val locationChannel = NotificationChannel(
            CHANNEL_LOCATION,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when WakeNear is tracking your location"
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wake-up alarm when you reach your destination"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(locationChannel)
        manager.createNotificationChannel(alarmChannel)
    }
}
