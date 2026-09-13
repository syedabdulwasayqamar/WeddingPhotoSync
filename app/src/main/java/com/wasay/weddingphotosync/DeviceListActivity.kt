package com.wasay.weddingphotosync

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class DeviceListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: DeviceAdapter
    private val deviceList = mutableListOf<DeviceInfo>()
    private val db = FirebaseFirestore.getInstance()
    private var snapshotListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        recyclerView = findViewById(R.id.deviceRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        adapter = DeviceAdapter(deviceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefreshLayout.setOnRefreshListener {
            loadDevices()
        }

        startListening()
    }

    private fun startListening() {
        snapshotListener?.remove()
        
        snapshotListener = db.collection("devices")
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("DeviceList", "❌ Listen failed", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    deviceList.clear()
                    for (doc in snapshots) {
                        val device = DeviceInfo(
                            guestName = doc.getString("guestName") ?: "Unknown",
                            fatherName = doc.getString("fatherName") ?: "",
                            status = doc.getString("status") ?: "unknown",
                            uploadedPhotos = doc.getLong("uploadedPhotos")?.toInt() ?: 0,
                            totalPhotos = doc.getLong("totalPhotos")?.toInt() ?: 0,
                            lastUpdated = doc.getLong("lastUpdated") ?: 0
                        )
                        deviceList.add(device)
                    }

                    updateUI()
                }
            }
    }

    private fun loadDevices() {
        swipeRefreshLayout.isRefreshing = true
        db.collection("devices")
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                deviceList.clear()
                for (doc in documents) {
                    val device = DeviceInfo(
                        guestName = doc.getString("guestName") ?: "Unknown",
                        fatherName = doc.getString("fatherName") ?: "",
                        status = doc.getString("status") ?: "unknown",
                        uploadedPhotos = doc.getLong("uploadedPhotos")?.toInt() ?: 0,
                        totalPhotos = doc.getLong("totalPhotos")?.toInt() ?: 0,
                        lastUpdated = doc.getLong("lastUpdated") ?: 0
                    )
                    deviceList.add(device)
                }
                updateUI()
                swipeRefreshLayout.isRefreshing = false
            }
            .addOnFailureListener { e ->
                Log.e("DeviceList", "❌ Failed to load devices", e)
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Failed to refresh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI() {
        if (deviceList.isEmpty()) {
            emptyText.text = "No registered devices yet\n\nGuests need to open the app and register first."
            emptyText.visibility = View.VISIBLE
        } else {
            emptyText.visibility = View.GONE
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }

    data class DeviceInfo(
        val guestName: String,
        val fatherName: String,
        val status: String,
        val uploadedPhotos: Int,
        val totalPhotos: Int,
        val lastUpdated: Long
    )

    inner class DeviceAdapter(private val devices: List<DeviceInfo>) :
        RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return DeviceViewHolder(view)
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val device = devices[position]

            val statusText = when (device.status) {
                "registered" -> "⏳ Waiting for sync"
                "pending_sync" -> "⏳ Sync triggered"
                "uploading" -> "📤 Uploading (${device.uploadedPhotos}/${device.totalPhotos})"
                "completed" -> "✅ Uploaded ${device.uploadedPhotos} photos"
                "failed" -> "❌ Upload failed"
                "error" -> "⚠️ Error occurred"
                else -> "Unknown"
            }

            holder.text1.text = "${device.guestName} ${device.fatherName}"
            holder.text2.text = statusText

            // Color based on status
            val color = when (device.status) {
                "completed" -> android.graphics.Color.parseColor("#4CAF50") // Green
                "uploading", "pending_sync" -> android.graphics.Color.parseColor("#2196F3") // Blue
                "failed", "error" -> android.graphics.Color.parseColor("#F44336") // Red
                else -> android.graphics.Color.GRAY
            }
            holder.text1.setTextColor(color)
        }

        override fun getItemCount(): Int = devices.size

        inner class DeviceViewHolder(itemView: View) :
            RecyclerView.ViewHolder(itemView) {
            val text1: TextView = itemView.findViewById(android.R.id.text1)
            val text2: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}