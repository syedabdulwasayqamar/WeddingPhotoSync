package com.wasay.weddingphotosync

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🔍 Worker checking for photos...")

            if (!isNetworkAvailable()) {
                Log.d(TAG, "⚠️ No internet, retrying later")
                return Result.retry()
            }

            // NEW: Check if upload is already running
            if (UploadService.isUploading) {
                Log.d(TAG, "⏳ Upload already running, skipping")
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences("photosync_prefs", Context.MODE_PRIVATE)
            val firstName = prefs.getString("first_name", null)
            val deviceId = prefs.getString("device_id", null)

            if (firstName == null || deviceId == null) {
                Log.d(TAG, "👤 Guest not registered yet")
                return Result.success()
            }

            val firestore = FirebaseFirestore.getInstance()

            val deviceDoc = firestore.collection("devices").document(deviceId).get().await()
            val status = deviceDoc.getString("status")

            if (status == "pending_sync") {
                Log.d(TAG, "📱 Device has pending_sync, starting upload service...")
                val serviceIntent = Intent(applicationContext, UploadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(serviceIntent)
                } else {
                    applicationContext.startService(serviceIntent)
                }
            } else {
                val doc = firestore.collection("config").document("event_window").get().await()
                val start = doc.getLong("startTime")
                val end = doc.getLong("endTime")

                if (start != null && end != null) {
                    Log.d(TAG, "📅 Time window found, checking for photos...")
                    val serviceIntent = Intent(applicationContext, UploadService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(serviceIntent)
                    } else {
                        applicationContext.startService(serviceIntent)
                    }
                } else {
                    Log.d(TAG, "⏳ No time window set yet")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Worker error", e)
            Result.retry()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}