package com.baji.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.baji.sdk.model.DeviceInfo

class DeviceAdapter(
    private val devices: List<DeviceInfo>,
    private val onItemClick: (DeviceInfo) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener {
            onItemClick(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(device: DeviceInfo) {
            text1.text = device.name
            val rssiText = device.rssi?.let { "RSSI: ${it}dBm" } ?: ""
            val statusText = if (device.isConnected) "(已连接)" else ""
            text2.text = "${device.macAddress} $rssiText $statusText".trim()
        }
    }
}

