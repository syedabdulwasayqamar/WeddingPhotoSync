package com.wasay.weddingphotosync

import android.content.Context
import android.provider.MediaStore

data class MediaItem(
    val uri: android.net.Uri,
    val displayName: String,
    val mimeType: String,
    val dateTakenMillis: Long
)

class PhotoScanner(private val context: Context) {

    fun scanMediaInWindow(startTimeMillis: Long, endTimeMillis: Long): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        results.addAll(scanImages(startTimeMillis, endTimeMillis))
        results.addAll(scanVideos(startTimeMillis, endTimeMillis))
        return results
    }

    private fun scanImages(start: Long, end: Long): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ? AND (${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?)"
        val selectionArgs = arrayOf(start.toString(), end.toString(), "%DCIM/Camera%", "%Pictures/Wedding%")

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                items.add(
                    MediaItem(
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "photo_$id.jpg",
                        mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                        dateTakenMillis = cursor.getLong(dateCol)
                    )
                )
            }
        }
        return items
    }

    private fun scanVideos(start: Long, end: Long): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Video.Media.DATE_TAKEN} >= ? AND ${MediaStore.Video.Media.DATE_TAKEN} <= ? AND (${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?)"
        val selectionArgs = arrayOf(start.toString(), end.toString(), "%DCIM/Camera%", "%Pictures/Wedding%")

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                items.add(
                    MediaItem(
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "video_$id.mp4",
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                        dateTakenMillis = cursor.getLong(dateCol)
                    )
                )
            }
        }
        return items
    }
}