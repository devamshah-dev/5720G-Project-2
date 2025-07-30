package com.devamshah.p2pwifimanagerroot
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import android.util.Log

class PhotoCaptureWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val TAG = "PhotoWorker"
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

            Log.d(TAG, "Executing photo capture command: $command")
            val (success, stdout, stderr) = RootCommandExecutor.execute(command)

            return if (success) {
                println("Photo captured successfully: ${outputFile.absolutePath}")
                // Optionally, broadcast an intent here to update AuthenticatorService
                // with a "photo_taken" status or even process the photo for face match.
                Result.success()
            } else {
                println("Failed to capture photo. Exit code: $stderr")
                Result.failure()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            return Result.failure()
        }
    }
}