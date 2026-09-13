package com.wasay.weddingphotosync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

class ConnectivityReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ConnectivityReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (isNetworkAvailable(context)) {
            Log.d(TAG, "🌐 Internet connected! Checking for pending sync...")

            // NEW: Check if upload is already running
            if (UploadService.isUploading) {
                Log.d(TAG, "⏳ Upload already running, skipping")
                return
            }

            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("devices")
                .whereEqualTo("status", "pending_sync")
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        Log.d(TAG, "📱 Found ${documents.size()} devices with pending_sync")
                        val serviceIntent = Intent(context, UploadService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to check pending devices", e)
                }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}