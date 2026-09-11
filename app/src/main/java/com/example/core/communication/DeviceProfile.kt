package com.example.core.communication

import java.nio.ByteOrder

/**
 * Universal Device Profile System.
 * Enables adding support for arbitrary ground scanner protocols, DIY microcontrollers (ESP32/STM32/Arduino),
 * and commercial sensors without modifying the core app engine.
 */
data class DeviceProfile(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val transport: ScannerTransportType,
    val bleServiceUuid: String? = null,
    val bleCharacteristicUuid: String? = null,
    val baudRate: Int = 115200,
    val packetFormat: PacketFormatType = PacketFormatType.BINARY_FIXED_32,
    val packetSize: Int = 32,
    val headerByte: Byte = 0xAA.toByte(),
    val footerByte: Byte = 0x55.toByte(),
    val endianness: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    val sensorChannels: Int = 1,
    val nativeUnit: String = "µT",
    val scalingFactor: Float = 1.0f,
    val supportedSamplingRatesHz: List<Int> = listOf(1, 10, 20, 50),
    val supportsHardwareCalibration: Boolean = true,
    val commandStart: ByteArray = byteArrayOf(0xAA.toByte(), 0x01, 0x55.toByte()),
    val commandStop: ByteArray = byteArrayOf(0xAA.toByte(), 0x02, 0x55.toByte()),
    val commandCalibrate: ByteArray = byteArrayOf(0xAA.toByte(), 0x03, 0x55.toByte()),
    val isBuiltIn: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DeviceProfile
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class PacketFormatType(val displayName: String) {
    BINARY_FIXED_32("Binary 32-Byte Little-Endian Frame"),
    GENERIC_ASCII_CSV("Text ASCII CSV ($GS,SEQ,X,Y,VAL*CS)"),
    CUSTOM_RAW_STREAM("Custom Raw Byte Stream"),
    UNKNOWN_PROTOCOL("Unknown Proprietary Protocol")
}

object DeviceProfileRegistry {

    val PROFILES = listOf(
        DeviceProfile(
            id = "diy_geoscan_ble_v1",
            name = "GeoScan BLE Pro (ESP32/NRF52)",
            manufacturer = "OpenGeophysics",
            model = "GS-BLE-32",
            transport = ScannerTransportType.BLUETOOTH_LE,
            bleServiceUuid = "0000FFE0-0000-1000-8000-00805F9B34FB",
            bleCharacteristicUuid = "0000FFE1-0000-1000-8000-00805F9B34FB",
            packetFormat = PacketFormatType.BINARY_FIXED_32,
            packetSize = 32,
            supportedSamplingRatesHz = listOf(1, 10, 20, 50, 100),
            supportsHardwareCalibration = true
        ),
        DeviceProfile(
            id = "diy_geoscan_spp_v1",
            name = "GeoScan SPP Bluetooth (HC-05/HC-06)",
            manufacturer = "Standard Serial",
            model = "GS-SPP-SERIAL",
            transport = ScannerTransportType.BLUETOOTH_CLASSIC_SPP,
            baudRate = 115200,
            packetFormat = PacketFormatType.BINARY_FIXED_32,
            packetSize = 32,
            supportedSamplingRatesHz = listOf(1, 10, 20, 50)
        ),
        DeviceProfile(
            id = "generic_ascii_gradiometer",
            name = "ASCII Text Gradiometer (NMEA Style)",
            manufacturer = "Generic Scientific",
            model = "MAG-ASCII-STREAM",
            transport = ScannerTransportType.BLUETOOTH_CLASSIC_SPP,
            baudRate = 9600,
            packetFormat = PacketFormatType.GENERIC_ASCII_CSV,
            packetSize = 64,
            supportedSamplingRatesHz = listOf(1, 10, 20)
        ),
        DeviceProfile(
            id = "usb_serial_ftdi",
            name = "USB OTG Wired Gradiometer (FTDI/CH340)",
            manufacturer = "Industrial USB Sensor",
            model = "USB-MAG-CDC",
            transport = ScannerTransportType.USB_SERIAL,
            baudRate = 115200,
            packetFormat = PacketFormatType.BINARY_FIXED_32,
            packetSize = 32,
            supportedSamplingRatesHz = listOf(1, 10, 20, 50, 100)
        ),
        DeviceProfile(
            id = "unknown_scanner_probe",
            name = "Unknown Scanner Probe (Auto-Detect Mode)",
            manufacturer = "Unknown / Third-Party",
            model = "Generic Probe",
            transport = ScannerTransportType.BLUETOOTH_LE,
            packetFormat = PacketFormatType.UNKNOWN_PROTOCOL,
            packetSize = 0,
            supportedSamplingRatesHz = listOf(10),
            supportsHardwareCalibration = false
        )
    )

    fun getProfileById(id: String): DeviceProfile {
        return PROFILES.find { it.id == id } ?: PROFILES.first()
    }
}
