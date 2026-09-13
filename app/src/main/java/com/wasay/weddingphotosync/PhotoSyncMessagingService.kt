package com.wasay.weddingphotosync

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore

class PhotoSyncMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PhotoSyncMessaging"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push received: ${message.data}")

        if (message.data["type"] == "sync_now") {
            CoroutineScope(Dispatchers.IO).launch {
                runSync()
            }
        }
    }

    private suspend fun runSync() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("config").document("event_window").get().await()

            val start = doc.getLong("startTime")
            val end = doc.getLong("endTime")
            if (start == null || end == null) return

            val prefs = applicationContext.getSharedPreferences("photosync_prefs", MODE_PRIVATE)
            val firstName = prefs.getString("first_name", null) ?: return

            val scanner = PhotoScanner(applicationContext)
            val items = scanner.scanMediaInWindow(start, end)

            val uploader = FirebaseStorageUploader()
            for (item in items) {
                try {
                    val bytes = applicationContext.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val labeledName = "${firstName}_${item.displayName}"
                        uploader.uploadFile(bytes, labeledName, item.mimeType)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Upload failed for ${item.displayName}", e)
                }
            }
            Log.d(TAG, "Push-triggered sync complete: ${items.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Push-triggered sync failed", e)
        }
    }
}