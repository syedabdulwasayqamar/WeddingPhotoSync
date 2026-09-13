package com.wasay.weddingphotosync

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.*
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PhotoSync"
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var statusText: TextView
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var fatherNameInput: TextInputEditText
    private lateinit var continueButton: Button
    private lateinit var cameraButton: Button
    private lateinit var cameraButtonContainer: LinearLayout
    private lateinit var formLayout: LinearLayout
    private lateinit var welcomeBackText: TextView
    private lateinit var changeNameButton: Button
    private var photoUri: Uri? = null
    private var isSyncing = false
    private var isRegistering = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Permission result: allGranted=$allGranted")
        if (allGranted) {
            startSync()
            scheduleBackgroundSync()
            subscribeToWeddingTopic()
            val firstName = getSharedPreferences("photosync_prefs", MODE_PRIVATE).getString("first_name", "Guest") ?: "Guest"
            showSuccessState(firstName)
        } else {
            statusText.text = "📷 Photo access is needed to share your wedding photos."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        FirebaseFirestore.setLoggingEnabled(true)
        testFirestoreConnection()

        statusText = findViewById(R.id.statusText)
        firstNameInput = findViewById(R.id.firstNameInput)
        fatherNameInput = findViewById(R.id.fatherNameInput)
        continueButton = findViewById(R.id.continueButton)
        cameraButton = findViewById(R.id.openCameraButton)
        cameraButtonContainer = findViewById(R.id.cameraButtonContainer)
        welcomeBackText = findViewById(R.id.welcomeBackText)
        changeNameButton = findViewById(R.id.changeNameButton)
        formLayout = findViewById(R.id.formLayout)

        // Animate card slide up
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.cardContainer)
        card.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setDuration(800)
            .start()

        changeNameButton.setOnClickListener {
            showRegistrationForm()
        }

        // Check if already registered
        val prefs = getSharedPreferences("photosync_prefs", MODE_PRIVATE)
        val savedFirstName = prefs.getString("first_name", null)
        if (savedFirstName != null) {
            val savedFatherName = prefs.getString("father_name", "")
            firstNameInput.setText(savedFirstName)
            fatherNameInput.setText(savedFatherName)
            
            // Show personalized welcome and camera button immediately
            showSuccessState(savedFirstName)
            
            // Auto-start sync if permissions are already granted
            checkAndRequestPermissions()
        }

        continueButton.setOnClickListener {
            handleContinue()
        }

        cameraButton.setOnClickListener {
            openCamera()
        }
    }

    private fun openCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "WEDDING_${timeStamp}.jpg"

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Wedding")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Wedding/$fileName")
            file.parentFile?.mkdirs()
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        }

        photoUri = uri
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivity(cameraIntent)
    }

    private fun testFirestoreConnection() {
        Log.d("FirestoreTest", "Starting Firestore connection test...")

        val testData = hashMapOf(
            "test" to "connection",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("test_connection")
            .add(testData)
            .addOnSuccessListener { documentReference ->
                Log.d("FirestoreTest", "✅ SUCCESS! Wrote to Firestore! Document ID: ${documentReference.id}")
                documentReference.delete()
                    .addOnSuccessListener {
                        Log.d("FirestoreTest", "✅ Test document cleaned up")
                    }
                    .addOnFailureListener { e ->
                        Log.w("FirestoreTest", "Couldn't delete test document", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreTest", "❌ FAILED! Firestore connection error", e)
                showErrorMessage(e)
            }
    }

    private fun showErrorMessage(e: Exception) {
        val message = when (e) {
            is FirebaseFirestoreException -> {
                when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission denied. Check Firestore rules."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Service unavailable - Check network"
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        "Connection timed out"
                    else -> "Firestore error: ${e.code}"
                }
            }
            is IOException -> "Network error: ${e.message}"
            else -> "Error: ${e.message}"
        }

        Log.e("FirestoreTest", message)
        runOnUiThread {
            statusText.text = "⚠️ $message"
        }
    }

    private fun handleContinue() {
        if (isRegistering) return

        Log.d(TAG, "Continue button tapped")
        val firstName = firstNameInput.text.toString().trim()
        val fatherName = fatherNameInput.text.toString().trim()

        if (firstName.isEmpty() || fatherName.isEmpty()) {
            statusText.text = "Please enter both names."
            statusText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    statusText.text = "Please enter both names."
                    statusText.animate().alpha(1f).setDuration(200).start()
                }
                .start()
            return
        }

        // Secret admin trigger
        if (firstName.equals("admin", ignoreCase = true) &&
            fatherName.equals("admin", ignoreCase = true)) {
            Log.d(TAG, "Admin login detected, launching AdminActivity")
            startActivity(Intent(this, AdminActivity::class.java))
            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            return
        }

        isRegistering = true
        continueButton.isEnabled = false
        statusText.text = "⏳ Registering..."

        // Animate button press
        continueButton.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .start()

        Log.d(TAG, "Saving guest name: $firstName $fatherName")

        // Get unique device ID
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Save name and device ID locally
        val prefs = getSharedPreferences("photosync_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("first_name", firstName)
            .putString("father_name", fatherName)
            .putString("device_id", deviceId)
            .apply()

        // Register device in Firestore
        registerDevice(firstName, fatherName, deviceId)
    }

    private fun registerDevice(firstName: String, fatherName: String, deviceId: String) {
        Log.d(TAG, "📱 Registering device: $deviceId")

        val deviceData = hashMapOf(
            "guestName" to firstName,
            "fatherName" to fatherName,
            "deviceId" to deviceId,
            "status" to "registered",
            "registeredAt" to System.currentTimeMillis(),
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("devices").document(deviceId)
            .set(deviceData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Device registered successfully")
                showSuccessState(firstName)
                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestPermissions()
                }, 500)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to register device", e)
                statusText.text = "⚠️ Registration failed: ${e.message}"
                continueButton.isEnabled = true
                isRegistering = false
                continueButton.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions")

        if (isSyncing) {
            Log.d(TAG, "Sync already in progress")
            return
        }

        val permissionsNeeded = getRequiredPermissions()
        val notGranted = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            Log.d(TAG, "✅ Permissions already granted, starting sync")
            startSync()
            scheduleBackgroundSync()
            subscribeToWeddingTopic()
        } else {
            Log.d(TAG, "Requesting permissions: $notGranted")
            statusText.text = "📷 Requesting photo access..."
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun showSuccessState(firstName: String) {
        // Update welcome text
        welcomeBackText.text = "Welcome $firstName!\nWe hope you are enjoying Khulud\'s event!"
        
        // Hide registration form
        formLayout.visibility = android.view.View.GONE
        
        // Show success UI
        statusText.visibility = android.view.View.GONE
        welcomeBackText.visibility = android.view.View.VISIBLE
        changeNameButton.visibility = android.view.View.VISIBLE
        cameraButtonContainer.visibility = android.view.View.VISIBLE
        cameraButtonContainer.alpha = 1f
        cameraButtonContainer.translationY = 0f
    }

    private fun showRegistrationForm() {
        // Reset state
        isRegistering = false
        continueButton.isEnabled = true
        continueButton.scaleX = 1.0f
        continueButton.scaleY = 1.0f

        // Show form
        formLayout.visibility = android.view.View.VISIBLE
        
        // Hide success UI
        welcomeBackText.visibility = android.view.View.GONE
        changeNameButton.visibility = android.view.View.GONE
        cameraButtonContainer.visibility = android.view.View.GONE
        statusText.visibility = android.view.View.VISIBLE
        statusText.text = ""
    }

    private fun subscribeToWeddingTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("wedding_guests")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Subscribed to wedding_guests topic")
                } else {
                    Log.e(TAG, "❌ Failed to subscribe to topic", task.exception)
                }
            }
    }

    private fun startSync() {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping")
            return
        }

        Log.d(TAG, "📤 Starting foreground upload service...")
        statusText.text = "📤 Uploading your wedding photos..."

        // Start the foreground service
        val serviceIntent = Intent(this, UploadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun scheduleBackgroundSync() {
        Log.d(TAG, "📅 Scheduling background sync (every 1 hour)")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "wedding_photo_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permissions.toTypedArray()
    }
}