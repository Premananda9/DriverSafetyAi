package com.driversafety.ai.ui

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.driversafety.ai.databinding.ItemBluetoothDeviceBinding

class BluetoothDeviceAdapter(
    private var devices: List<BluetoothDevice>,
    private val onConnect: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemBluetoothDeviceBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        with(holder.binding) {
            try {
                tvDeviceName.text = device.name ?: "Unknown Device"
                tvDeviceAddress.text = device.address
            } catch (e: SecurityException) {
                tvDeviceName.text = "Device ${position + 1}"
                tvDeviceAddress.text = device.address
            }
            btnConnect.setOnClickListener { onConnect(device) }
        }
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
