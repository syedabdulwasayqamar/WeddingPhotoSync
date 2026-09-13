package com.wasay.weddingphotosync

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class UploadService : Service() {

    companion object {
        private const val TAG = "UploadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "photo_upload_channel"
        private const val CHANNEL_NAME = "Photo Upload"

        @JvmStatic
        var isUploading = false
    }

    private lateinit var db: FirebaseFirestore
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var uploadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        db = FirebaseFirestore.getInstance()
        createNotificationChannel()
        Log.d(TAG, "✅ UploadService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_UPLOAD") {
            stopUpload()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Checking for photos...", "⏳"))
        startUpload()

        return START_STICKY
    }

    private fun startUpload() {
        if (isRunning) {
            Log.d(TAG, "⚠️ Upload already running")
            return
        }

        isRunning = true
        isUploading = true
        Log.d(TAG, "🔄 Starting upload process...")

        uploadJob = serviceScope.launch {
            try {
                // Get guest name and device ID
                val prefs = getSharedPreferences("photosync_prefs", MODE_PRIVATE)
                val firstName = prefs.getString("first_name", null)
                val deviceId = prefs.getString("device_id", null)

                if (firstName == null || deviceId == null) {
                    Log.d(TAG, "👤 No guest registered yet")
                    updateNotification("Please open app and register first", "⚠️")
                    delay(3000)
                    stopUpload()
                    return@launch
                }

                Log.d(TAG, "👤 Guest: $firstName, Device: $deviceId")

                // Get event window from Firestore
                updateNotification("Checking event time...", "⏳")

                val doc = db.collection("config").document("event_window")
                    .get()
                    .await()

                val start = doc.getLong("startTime")
                val end = doc.getLong("endTime")

                if (start == null || end == null) {
                    Log.d(TAG, "⏳ No event window set yet")
                    updateNotification("Registration successful! Waiting for event to start...", "✅")
                    updateDeviceStatus(deviceId, firstName, "registered")
                    delay(10000)
                    stopUpload()
                    return@launch
                }

                Log.d(TAG, "📅 Time window: $start - $end")

                updateDeviceStatus(deviceId, firstName, "uploading")
                updateNotification("Scanning for wedding photos...", "🔍")

                val scanner = PhotoScanner(applicationContext)
                val items = scanner.scanMediaInWindow(start, end)

                if (items.isEmpty()) {
                    Log.d(TAG, "📸 No photos found in time window")
                    updateNotification("No wedding photos found yet", "📸")
                    updateDeviceStatus(deviceId, firstName, "registered")
                    scheduleNextCheck()
                    delay(5000)
                    stopUpload()
                    return@launch
                }

                Log.d(TAG, "📸 Found ${items.size} photos from the event")
                updateNotification("Found ${items.size} photos! Uploading...", "📤")

                val uploader = FirebaseStorageUploader()
                var uploaded = 0
                var failed = 0
                val total = items.size

                for ((index, item) in items.withIndex()) {
                    if (!isRunning) {
                        Log.d(TAG, "⏹️ Upload stopped by user")
                        break
                    }

                    try {
                        val progress = ((index.toFloat() / total) * 100).toInt()
                        updateNotificationProgress(
                            "Uploading ${index + 1}/$total: ${item.displayName}",
                            progress
                        )

                        val bytes = contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val name = "${firstName}_${item.displayName}"
                            val success = uploader.uploadFile(bytes, name, item.mimeType)
                            if (success) {
                                uploaded++
                                Log.d(TAG, "✅ Uploaded $uploaded/$total")
                            } else {
                                failed++
                                Log.e(TAG, "❌ Upload failed for ${item.displayName}")
                            }
                        } else {
                            failed++
                            Log.e(TAG, "❌ Could not read file: ${item.displayName}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Upload exception for ${item.displayName}", e)
                        failed++
                    }
                }

                val message = if (uploaded > 0) {
                    "✅ Uploaded $uploaded of $total photos! 🎉"
                } else {
                    "❌ Upload failed. Please try again."
                }

                val status = if (uploaded > 0) "completed" else "failed"
                updateDeviceStatus(deviceId, firstName, status, uploaded, failed, total)

                val statusData = hashMapOf(
                    "guestName" to firstName,
                    "uploaded" to uploaded,
                    "failed" to failed,
                    "total" to total,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                db.collection("uploads").document(deviceId)
                    .set(statusData)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Upload status saved to Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to save upload status", e)
                    }

                updateNotification(message, if (uploaded > 0) "✅" else "❌")
                Log.d(TAG, "$message ($failed failed)")

                delay(15000)
                stopUpload()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Upload service error", e)
                updateNotification("Error: ${e.message}", "❌")

                val prefs = getSharedPreferences("photosync_prefs", MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", null)
                val firstName = prefs.getString("first_name", "guest")
                if (deviceId != null) {
                    updateDeviceStatus(deviceId, firstName ?: "guest", "error")
                }

                delay(5000)
                stopUpload()
            }
        }
    }

    private fun scheduleNextCheck() {
        Log.d(TAG, "📅 Scheduling next check...")
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(30, java.util.concurrent.TimeUnit.MINUTES)
            .build()

        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("next_sync_check", androidx.work.ExistingWorkPolicy.REPLACE, workRequest)
    }

    private fun updateDeviceStatus(
        deviceId: String,
        guestName: String,
        status: String,
        total: Int = 0,
        uploaded: Int = 0,
        failed: Int = 0
    ) {
        val data = hashMapOf(
            "guestName" to guestName,
            "status" to status,
            "lastUpdated" to System.currentTimeMillis(),
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        if (status == "uploading") {
            data["totalPhotos"] = total
        } else if (status == "completed" || status == "failed") {
            data["uploadedPhotos"] = uploaded
            data["failedPhotos"] = failed
            data["totalPhotos"] = total
        }

        db.collection("devices").document(deviceId)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Device status updated: $status")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update device status", e)
            }
    }

    private fun stopUpload() {
        Log.d(TAG, "⏹️ Stopping upload service")
        isRunning = false
        isUploading = false
        uploadJob?.cancel()

        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification(text: String, icon: String) {
        val notification = createNotification(text, icon)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationProgress(text: String, progress: Int) {
        val notification = createNotification(text, "📤", progress)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String, icon: String, progress: Int = -1): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wedding Photo Sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(progress == -1)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (progress >= 0 && progress < 100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val stopIntent = Intent(this, UploadService::class.java).apply {
            action = "STOP_UPLOAD"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            pendingIntent
        )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows photo upload progress"
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "UploadService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}