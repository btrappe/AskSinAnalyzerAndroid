package com.asksin.analyzer.data

import com.asksin.analyzer.model.Telegram
import kotlin.math.roundToInt

/**
 * Parses the serial output of the AskSinSniffer328P sketch.
 *
 * The sniffer outputs two types of lines:
 *
 * 1. Telegram line:
 *    A;<millis>;<rssi_raw>;<lqi_raw>;<hex_data_without_spaces>
 *    e.g.: A;12345;-74;42;0E112A10AABBCCDDEEFF010203
 *
 * 2. Noise / RSSI line:
 *    N;<millis>;<rssi_raw>
 *    e.g.: N;12346;-85
 *
 * The hex_data field is the raw BidCoS packet:
 *   [LEN][CNT][FLAGS][TYPE][SRC0][SRC1][SRC2][DST0][DST1][DST2][PAYLOAD...]
 */
    data class DecodedFrame(
        val msgLen: Int,
        val msgCounter: Int,
        val flags: Int,
        val msgType: Int,
        val srcAddress: String,
        val dstAddress: String,
        val payload: ByteArray
    )

object TelegramParser {

    private val TELEGRAM_REGEX = Regex(
        """^A;(\d+);(-?\d+);(\d+);([0-9A-Fa-f ]+)$"""
    )
    private val NOISE_REGEX = Regex(
        """^N;(\d+);(-?\d+)$"""
    )

    sealed class ParseResult {
        data class TelegramResult(val telegram: Telegram) : ParseResult()
        data class NoiseResult(val timestampMs: Long, val rssiDbm: Int) : ParseResult()
        object Invalid : ParseResult()
    }

    fun decodeBidCosFrame(rawBytes: ByteArray): DecodedFrame? {
        if (rawBytes.size < 10) return null
        return DecodedFrame(
            msgLen     = rawBytes[0].toInt() and 0xFF,
            msgCounter = rawBytes[1].toInt() and 0xFF,
            flags      = rawBytes[2].toInt() and 0xFF,
            msgType    = rawBytes[3].toInt() and 0xFF,
            srcAddress = "%02X%02X%02X".format(
                rawBytes[4].toInt() and 0xFF,
                rawBytes[5].toInt() and 0xFF,
                rawBytes[6].toInt() and 0xFF
            ),
            dstAddress = "%02X%02X%02X".format(
                rawBytes[7].toInt() and 0xFF,
                rawBytes[8].toInt() and 0xFF,
                rawBytes[9].toInt() and 0xFF
            ),
            payload = if (rawBytes.size > 10) rawBytes.copyOfRange(10, rawBytes.size) else byteArrayOf()
        )
    }

    fun parse(line: String): ParseResult {
        val trimmed = line.trim()

        TELEGRAM_REGEX.matchEntire(trimmed)?.let { match ->
            val millis = match.groupValues[1].toLongOrNull() ?: return ParseResult.Invalid
            val rssiRaw = match.groupValues[2].toIntOrNull() ?: return ParseResult.Invalid
            val lqiRaw = match.groupValues[3].toIntOrNull() ?: return ParseResult.Invalid
            val hex = match.groupValues[4].replace(" ", "")

            val bytes = hexToBytes(hex) ?: return ParseResult.Invalid
            val frame = decodeBidCosFrame(bytes) ?: return ParseResult.Invalid

            val rssiDbm = convertRssi(rssiRaw)
            val lqi = lqiRaw and 0x7F  // 7-bit value

            val telegram = Telegram(
                timestamp = System.currentTimeMillis(),
                rssi = rssiDbm,
                lqi = lqi,
                rawBytes = bytes,
                msgLen = frame.msgLen,
                msgCounter = frame.msgCounter,
                flags = frame.flags,
                msgType = frame.msgType,
                srcAddress = frame.srcAddress,
                dstAddress = frame.dstAddress,
                payload = frame.payload
            )
            return ParseResult.TelegramResult(telegram)
        }

        NOISE_REGEX.matchEntire(trimmed)?.let { match ->
            val millis = match.groupValues[1].toLongOrNull() ?: return ParseResult.Invalid
            val rssiRaw = match.groupValues[2].toIntOrNull() ?: return ParseResult.Invalid
            return ParseResult.NoiseResult(millis, convertRssi(rssiRaw))
        }

        return ParseResult.Invalid
    }

    /**
     * CC1101 RSSI conversion formula (from CC1101 datasheet):
     *   If RSSI_dec >= 128: RSSI_dBm = (RSSI_dec - 256) / 2 - RSSI_offset
     *   If RSSI_dec < 128:  RSSI_dBm = RSSI_dec / 2 - RSSI_offset
     *   RSSI_offset = 74 for 868 MHz
     *
     * The AskSinSniffer328P already emits the converted value (signed int),
     * so we just pass it through. If it looks like a raw value (0-255 range)
     * we apply the conversion ourselves.
     */
    private fun convertRssi(raw: Int): Int {
        // If the sniffer already outputs dBm (negative value), use directly
        if (raw < 0) return raw
        // Otherwise convert CC1101 raw byte
        return if (raw >= 128) ((raw - 256) / 2) - 74 else (raw / 2) - 74
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
