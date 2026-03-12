package com.asksin.analyzer.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a decoded HomeMatic BidCoS telegram sniffed by the CC1101/ATMega328P.
 *
 * The AskSinSniffer328P outputs lines in the format:
 *   A;<millis>;<RSSI_raw>;<LQI_raw>;<len> <byte0> <byte1> ... <byteN>
 *
 * HomeMatic BidCoS telegram structure (from AskSinPP library):
 *   Byte 0:  Length (number of following bytes)
 *   Byte 1:  Message counter
 *   Byte 2:  Control / Flags
 *   Byte 3:  Message type
 *   Byte 4-6:  Source address (3 bytes)
 *   Byte 7-9:  Destination address (3 bytes)
 *   Byte 10+: Payload
 */
data class Telegram(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val rssi: Int,           // dBm (converted from raw CC1101 value)
    val lqi: Int,            // 0-127 (lower = better)
    val rawBytes: ByteArray,

    // Decoded BidCoS fields
    val msgLen: Int,
    val msgCounter: Int,
    val flags: Int,
    val msgType: Int,
    val srcAddress: String,   // "AABBCC"
    val dstAddress: String,   // "DDEEFF"
    val payload: ByteArray,

    // Computed
    val dutyCycle: Float = 0f
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    val formattedRaw: String
        get() = rawBytes.joinToString(" ") { "%02X".format(it) }

    val formattedPayload: String
        get() = payload.joinToString(" ") { "%02X".format(it) }

    val msgTypeName: String
        get() = MessageTypes.name(msgType)

    val isBroadcast: Boolean
        get() = dstAddress == "000000"

    val rssiBar: Float
        get() = ((rssi + 100).coerceIn(0, 70) / 70f)  // normalise -100…-30 → 0…1

    val lqiGood: Boolean
        get() = lqi < 50

    override fun equals(other: Any?) = other is Telegram && id == other.id
    override fun hashCode() = id.hashCode()
}

object MessageTypes {
    private val types = mapOf(
        0x00 to "DEVINFO",
        0x01 to "CONFIG",
        0x02 to "RESPONSE",
        0x03 to "RESPONSE_AES",
        0x04 to "KEY_EXCHANGE",
        0x10 to "INFO",
        0x11 to "ACTION",
        0x12 to "GET",
        0x3E to "TIMESTAMP",
        0x3F to "TIMESTAMP",
        0x40 to "REMOTE_EVENT",
        0x41 to "SENSOR_DATA",
        0x53 to "SENSOR_EVENT",
        0x58 to "CLIMATE_EVENT",
        0x70 to "WEATHER_EVENT",
        0xCA to "SET_TEAM"
    )
    fun name(type: Int): String {
        val hex = "0x%02X".format(type)
        return types[type]?.let { "$it ($hex)" } ?: hex
    }
}

/**
 * Per-device duty cycle tracker.
 * HomeMatic devices are limited to 1% duty cycle per hour on 868 MHz.
 */
data class DeviceStats(
    val address: String,
    val telegramCount: Int = 0,
    val lastSeen: Long = 0L,
    val dutyCyclePercent: Float = 0f,
    val avgRssi: Int = 0,
    val minRssi: Int = 0,
    val maxRssi: Int = 0
)

/** RSSI noise sample for the noise floor chart */
data class NoiseSample(val timestamp: Long, val rssiDbm: Int)
