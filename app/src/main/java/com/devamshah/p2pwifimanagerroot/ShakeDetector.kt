package com.devamshah.p2pwifimanagerroot
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs

class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var lastTime: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    companion object {
        // Threshold= shake
        private const val SHAKE_THRESHOLD = 800
        private const val SHAKE_TIMEOUT = 500 // 500ms between shakes
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastTime) > SHAKE_TIMEOUT) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val speed = abs(x + y + z - lastX - lastY - lastZ) / (currentTime - lastTime) * 10000

                if (speed > SHAKE_THRESHOLD) {
                    lastTime = currentTime
                    // A significant motion event was detected!
                    onShake() // Trigger the callback function
                }

                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}