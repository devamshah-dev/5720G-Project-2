package com.devamshah.p2pwifimanagerroot
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MotionSentryService: Service() {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Initialize our shake detector and define what happens when a shake is detected
        shakeDetector = ShakeDetector {
            // This block is the "listener" that gets called on a shake event.
            // We schedule a PhotoCaptureWorker to do the heavy lifting.
            println("Shake detected! Scheduling photo capture job.")
            val workRequest = OneTimeWorkRequestBuilder<PhotoCaptureWorker>().build()
            WorkManager.getInstance(applicationContext).enqueue(workRequest)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as a foreground service to prevent Android from killing it
        val notification = NotificationCompat.Builder(this, "YOUR_CHANNEL_ID")
            .setContentTitle("Anti-Theft Active")
            .setContentText("Motion sensor monitoring is active.")
            // .setSmallIcon(R.drawable.ic_notification_icon) // Add your icon
            .build()
        startForeground(1, notification)

        // Register the sensor listener
        accelerometer?.also {
            sensorManager.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        return START_STICKY // If the service is killed, restart it
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the listener to save battery when the service is stopped
        sensorManager.unregisterListener(shakeDetector)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}