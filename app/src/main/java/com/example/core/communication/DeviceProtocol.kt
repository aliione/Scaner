package com.example.core.communication

import com.example.core.model.GenericSensorPacket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generic configurable ground-scanner communication protocol parser and serializer.
 * Works with DIY hardware, Arduino, ESP32, STM32, or commercial sensor controllers.
 * Supports binary packets (32 bytes) and human-readable CSV telemetry packets.
 */
object DeviceProtocol {

    const val START_BYTE: Byte = 0xAA.toByte()
    const val END_BYTE: Byte = 0x55.toByte()
    const val PACKET_SIZE: Int = 32

    /**
     * Parse binary 32-byte packet:
     * [0] Header (0xAA)
     * [1..4] Timestamp (UInt32)
     * [5..6] Sequence (UInt16)
     * [7..8] Grid X (Int16)
     * [9..10] Grid Y (Int16)
     * [11..14] Sensor Channel 1 (Float32)
     * [15..18] Sensor Channel 2 (Float32)
     * [19..22] Sensor Channel 3 (Float32)
     * [23] Battery % (UInt8)
     * [24..25] Orientation Pitch (Int16, scaled * 100)
     * [26..27] Orientation Roll (Int16, scaled * 100)
     * [28..29] Orientation Yaw (Int16, scaled * 100)
     * [30] Checksum (XOR)
     * [31] End Byte (0x55)
     */
    fun parseBinaryPacket(bytes: ByteArray, deviceId: String = "DIY_SENSOR"): GenericSensorPacket? {
        if (bytes.size < PACKET_SIZE) return null
        if (bytes[0] != START_BYTE || bytes[31] != END_BYTE) return null

        // Validate XOR checksum
        var calculatedXor: Byte = 0
        for (i in 1..29) {
            calculatedXor = (calculatedXor.toInt() xor bytes[i].toInt()).toByte()
        }
        val isChecksumValid = (calculatedXor == bytes[30])

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val timestamp = buffer.getInt(1).toLong() and 0xFFFFFFFFL
        val seq = buffer.getShort(5).toInt() and 0xFFFF
        val gridX = buffer.getShort(7).toInt()
        val gridY = buffer.getShort(9).toInt()
        val s1 = buffer.getFloat(11)
        val s2 = buffer.getFloat(15)
        val s3 = buffer.getFloat(19)
        val batt = buffer.get(23).toInt() and 0xFF
        val pitch = buffer.getShort(24).toFloat() / 100f
        val roll = buffer.getShort(26).toFloat() / 100f
        val yaw = buffer.getShort(28).toFloat() / 100f

        return GenericSensorPacket(
            header = START_BYTE,
            deviceId = deviceId,
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
            sequenceNumber = seq,
            xIndex = gridX,
            yIndex = gridY,
            sensorChannel1 = s1,
            sensorChannel2 = s2,
            sensorChannel3 = s3,
            batteryPercent = batt.coerceIn(0, 100),
            orientationPitch = pitch,
            orientationRoll = roll,
            orientationYaw = yaw,
            checksum = bytes[30].toInt(),
            isValid = isChecksumValid
        )
    }

    /**
     * Parse text line protocol: "$GS,SEQ,X,Y,S1,S2,S3,BATT,PITCH,ROLL,YAW*CHECKSUM"
     */
    fun parseTextPacket(line: String, deviceId: String = "DIY_SENSOR"): GenericSensorPacket? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("\$GS") && !trimmed.startsWith("GS")) return null

        val tokens = trimmed.removePrefix("\$").split(",", "*")
        if (tokens.size < 6) return null

        return try {
            val seq = tokens.getOrNull(1)?.toIntOrNull() ?: 0
            val x = tokens.getOrNull(2)?.toIntOrNull() ?: 0
            val y = tokens.getOrNull(3)?.toIntOrNull() ?: 0
            val s1 = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f
            val s2 = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f
            val s3 = tokens.getOrNull(6)?.toFloatOrNull() ?: 0f
            val batt = tokens.getOrNull(7)?.toIntOrNull() ?: 90
            val pitch = tokens.getOrNull(8)?.toFloatOrNull() ?: 0f
            val roll = tokens.getOrNull(9)?.toFloatOrNull() ?: 0f
            val yaw = tokens.getOrNull(10)?.toFloatOrNull() ?: 0f

            GenericSensorPacket(
                header = START_BYTE,
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                sequenceNumber = seq,
                xIndex = x,
                yIndex = y,
                sensorChannel1 = s1,
                sensorChannel2 = s2,
                sensorChannel3 = s3,
                batteryPercent = batt.coerceIn(0, 100),
                orientationPitch = pitch,
                orientationRoll = roll,
                orientationYaw = yaw,
                checksum = 0,
                isValid = true
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serializes a packet to 32-byte binary format for testing or transmission.
     */
    fun buildBinaryPacket(packet: GenericSensorPacket): ByteArray {
        val bytes = ByteArray(PACKET_SIZE)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        bytes[0] = START_BYTE
        buffer.putInt(1, (packet.timestamp and 0xFFFFFFFFL).toInt())
        buffer.putShort(5, packet.sequenceNumber.toShort())
        buffer.putShort(7, packet.xIndex.toShort())
        buffer.putShort(9, packet.yIndex.toShort())
        buffer.putFloat(11, packet.sensorChannel1)
        buffer.putFloat(15, packet.sensorChannel2)
        buffer.putFloat(19, packet.sensorChannel3)
        bytes[23] = packet.batteryPercent.toByte()
        buffer.putShort(24, (packet.orientationPitch * 100).toInt().toShort())
        buffer.putShort(26, (packet.orientationRoll * 100).toInt().toShort())
        buffer.putShort(28, (packet.orientationYaw * 100).toInt().toShort())

        var calculatedXor: Byte = 0
        for (i in 1..29) {
            calculatedXor = (calculatedXor.toInt() xor bytes[i].toInt()).toByte()
        }
        bytes[30] = calculatedXor
        bytes[31] = END_BYTE

        return bytes
    }
}
