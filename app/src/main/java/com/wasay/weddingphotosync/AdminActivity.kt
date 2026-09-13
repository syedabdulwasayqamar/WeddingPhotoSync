package com.wasay.weddingphotosync

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.Calendar

class AdminActivity : AppCompatActivity() {

    private lateinit var adminStatusText: TextView
    private lateinit var windowText: TextView
    private val firestore = FirebaseFirestore.getInstance()

    private var startTimeMillis: Long? = null
    private var endTimeMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        adminStatusText = findViewById(R.id.adminStatusText)
        windowText = findViewById(R.id.windowText)

        findViewById<Button>(R.id.setStartTimeButton).setOnClickListener {
            pickDateTime { millis ->
                startTimeMillis = millis
                updateWindowText()
            }
        }

        findViewById<Button>(R.id.setEndTimeButton).setOnClickListener {
            pickDateTime { millis ->
                endTimeMillis = millis
                updateWindowText()
            }
        }

        findViewById<Button>(R.id.saveWindowButton).setOnClickListener {
            saveWindowToFirestore()
        }

        findViewById<Button>(R.id.viewDevicesButton).setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        findViewById<Button>(R.id.backToWelcomeButton).setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }

        // NEW: Force Stop Button
        findViewById<Button>(R.id.forceStopButton).setOnClickListener {
            forceStopAllSyncs()
        }
    }

    private fun pickDateTime(onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(year, month, day, hour, minute, 0)
                onPicked(calendar.timeInMillis)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateWindowText() {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        val startStr = startTimeMillis?.let { sdf.format(java.util.Date(it)) } ?: "not set"
        val endStr = endTimeMillis?.let { sdf.format(java.util.Date(it)) } ?: "not set"
        windowText.text = "Start: $startStr\nEnd: $endStr"
    }

    private fun saveWindowToFirestore() {
        val start = startTimeMillis
        val end = endTimeMillis
        if (start == null || end == null) {
            adminStatusText.text = "Please set both start and end times first."
            return
        }

        val window = hashMapOf(
            "startTime" to start,
            "endTime" to end
        )

        firestore.collection("config").document("event_window")
            .set(window)
            .addOnSuccessListener {
                adminStatusText.text = "✅ Event window saved!"
                triggerSyncForAllDevices()
            }
            .addOnFailureListener { e ->
                val message = when (e) {
                    is com.google.firebase.firestore.FirebaseFirestoreException -> {
                        if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            "Permission denied. Check Firestore rules."
                        } else {
                            "Firestore error: ${e.code}"
                        }
                    }
                    else -> "Failed to save: ${e.message}"
                }
                adminStatusText.text = message
            }
    }

    private fun triggerSyncForAllDevices() {
        firestore.collection("devices")
            .get()
            .addOnSuccessListener { documents ->
                var count = 0
                for (doc in documents) {
                    val data = hashMapOf(
                        "status" to "pending_sync",
                        "syncTriggered" to System.currentTimeMillis()
                    )
                    firestore.collection("devices").document(doc.id)
                        .update(data as Map<String, Any>)
                        .addOnSuccessListener {
                            count++
                            Log.d("Admin", "✅ Sync triggered for: ${doc.getString("guestName")}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Admin", "❌ Failed to trigger sync for ${doc.id}", e)
                        }
                }
                adminStatusText.text = "✅ Event window saved! Sync triggered for $count devices."

                // Start the service
                val serviceIntent = Intent(this, UploadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Admin", "❌ Failed to get devices", e)
                adminStatusText.text = "✅ Event window saved! But failed to trigger sync: ${e.message}"
            }
    }

    // NEW: Force Stop All Syncs
    private fun forceStopAllSyncs() {
        adminStatusText.text = "🛑 Stopping all syncs..."

        // 1. Stop the UploadService
        val serviceIntent = Intent(this, UploadService::class.java).apply {
            action = "STOP_UPLOAD"
        }
        stopService(serviceIntent)
        Log.d("Admin", "✅ UploadService stopped")

        // 2. Cancel all pending WorkManager tasks
        androidx.work.WorkManager.getInstance(applicationContext)
            .cancelAllWork()
        Log.d("Admin", "✅ All WorkManager tasks cancelled")

        // 3. Reset all device statuses to "registered"
        firestore.collection("devices")
            .get()
            .addOnSuccessListener { documents ->
                var count = 0
                for (doc in documents) {
                    val data = hashMapOf(
                        "status" to "registered",
                        "syncTriggered" to null,
                        "forceStopped" to System.currentTimeMillis()
                    )
                    firestore.collection("devices").document(doc.id)
                        .update(data as Map<String, Any>)
                        .addOnSuccessListener {
                            count++
                            Log.d("Admin", "✅ Reset status for: ${doc.getString("guestName")}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Admin", "❌ Failed to reset status for ${doc.id}", e)
                        }
                }
                adminStatusText.text = "🛑 Stopped all syncs! Reset $count devices to registered status."
                Toast.makeText(this, "Stopped all syncs and reset $count devices", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e("Admin", "❌ Failed to reset devices", e)
                adminStatusText.text = "❌ Failed to reset devices: ${e.message}"
            }

        // 4. Clear any pending sync flags in UploadService
        UploadService.isUploading = false
        Log.d("Admin", "✅ UploadService flags reset")
    }
}