package com.wasay.weddingphotosync

import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class FirebaseStorageUploader {

    companion object {
        private const val TAG = "FirebaseStorageUploader"
    }

    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadFile(fileBytes: ByteArray, fileName: String, mimeType: String): Boolean {
        return try {
            Log.d(TAG, "🚀 Starting upload: $fileName, size=${fileBytes.size} bytes")
            val ref = storage.reference.child("wedding-photos/$fileName")
            ref.putBytes(fileBytes).await()
            Log.d(TAG, "✅ Upload succeeded: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed for $fileName", e)
            false
        }
    }
}