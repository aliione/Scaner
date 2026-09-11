package com.example.core.communication

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Thread-safe ring buffer and dispatcher for all incoming/outgoing raw hardware bytes.
 * Serves the Raw Hex Traffic Monitor tool for reverse engineering, packet verification, and logging.
 */
object TrafficMonitorHub {

    private const val MAX_LOG_CAPACITY = 250
    private val buffer = ArrayDeque<TrafficLogEntry>(MAX_LOG_CAPACITY)

    private val _trafficLogs = MutableSharedFlow<TrafficLogEntry>(replay = 50, extraBufferCapacity = 100)
    val trafficLogs = _trafficLogs.asSharedFlow()

    @Synchronized
    fun logTraffic(
        direction: TrafficDirection,
        rawBytes: ByteArray,
        isChecksumValid: Boolean,
        parsedDescription: String = ""
    ) {
        val hexStr = rawBytes.joinToString(" ") { "%02X".format(it) }
        val asciiStr = rawBytes.map { b ->
            val c = b.toInt().toChar()
            if (c in ' '..'~') c else '.'
        }.joinToString("")

        val entry = TrafficLogEntry(
            direction = direction,
            rawBytes = rawBytes.clone(),
            hexString = hexStr,
            asciiString = asciiStr,
            length = rawBytes.size,
            isChecksumValid = isChecksumValid,
            parsedDescription = parsedDescription
        )

        if (buffer.size >= MAX_LOG_CAPACITY) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
        _trafficLogs.tryEmit(entry)
    }

    @Synchronized
    fun getRecentLogs(): List<TrafficLogEntry> {
        return buffer.toList()
    }

    @Synchronized
    fun clearLogs() {
        buffer.clear()
    }
}
