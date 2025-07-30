package com.devamshah.p2pwifimanagerroot
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException

class PhotoCaptureWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        try {
            // Define where to save the picture. Use the app's internal storage.
            val timestamp = System.currentTimeMillis()
            val outputDir = File(applicationContext.filesDir, "captures")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, "capture_$timestamp.jpg")

            // The root command to take a photo using Termux's tool
            // It specifies the camera to use (0 is usually the back camera) and the output file path.
            val command = "su -c termux-camera-photo -c 0 ${outputFile.absolutePath}"

            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()

            return if (exitCode == 0) {
                println("Photo captured successfully: ${outputFile.absolutePath}")
                Result.success()
            } else {
                println("Failed to capture photo. Exit code: $exitCode")
                Result.failure()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            return Result.failure()
        }
    }
}