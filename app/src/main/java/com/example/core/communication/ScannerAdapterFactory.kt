package com.example.core.communication

import android.content.Context

/**
 * Factory for instantiating the appropriate ScannerAdapter based on transport type and profile.
 */
object ScannerAdapterFactory {

    fun createAdapter(
        context: Context,
        transportType: ScannerTransportType
    ): ScannerAdapter {
        return when (transportType) {
            ScannerTransportType.BLUETOOTH_LE -> BleScannerAdapter(context)
            ScannerTransportType.BLUETOOTH_CLASSIC_SPP -> ClassicBluetoothAdapter(context)
            ScannerTransportType.USB_SERIAL -> {
                // Return classic SPP or Ble as fallback when USB not attached, or USB adapter
                ClassicBluetoothAdapter(context)
            }
            ScannerTransportType.SIMULATOR -> {
                BleScannerAdapter(context)
            }
        }
    }
}
