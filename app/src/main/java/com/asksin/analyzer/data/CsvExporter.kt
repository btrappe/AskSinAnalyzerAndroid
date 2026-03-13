package com.asksin.analyzer.data

import com.asksin.analyzer.model.Telegram
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export/import telegrams in the same semicolon-separated CSV format
 * used by AskSinAnalyzerXS (TelegramsXS_*.csv).
 *
 * Header:
 *   tstamp;date;rssi;len;cnt;dc;flags;type;fromAddr;toAddr;fromName;toName;fromSerial;toSerial;toIsIp;fromIsIp;payload;raw
 */
object CsvExporter {

    private const val HEADER =
        "tstamp;date;rssi;len;cnt;dc;flags;type;fromAddr;toAddr;fromName;toName;fromSerial;toSerial;toIsIp;fromIsIp;payload;raw"

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // ── Export ───────────────────────────────────────────────────────────────

    fun export(telegrams: List<Telegram>, out: OutputStream) {
        out.bufferedWriter().use { w ->
            w.write(HEADER)
            w.newLine()
            for (t in telegrams) {
                w.write(toCsvLine(t))
                w.newLine()
            }
        }
    }

    private fun toCsvLine(t: Telegram): String {
        val date = DATE_FORMAT.format(Date(t.timestamp))
        val flags = flagsString(t.flags)
        val type = t.msgTypeName
        val fromAddr = hexAddressToDecimal(t.srcAddress)
        val toAddr = hexAddressToDecimal(t.dstAddress)
        val payload = t.payload.joinToString("") { "%02X".format(it) }
        val raw = ":" + t.rawBytes.joinToString("") { "%02X".format(it) }

        return "${t.timestamp};$date;${t.rssi};${t.msgLen};${t.msgCounter};${t.dutyCycle};" +
                "$flags;$type;$fromAddr;$toAddr;;;;;;;$payload;$raw;"
    }

    private fun flagsString(flags: Int): String {
        val parts = mutableListOf<String>()
        if (flags and 0x04 != 0) parts.add("BCAST")
        if (flags and 0x20 != 0) parts.add("BIDI")
        if (flags and 0x80 != 0) parts.add("RPTEN")
        if (flags and 0x40 != 0) parts.add("RPTED")
        if (flags and 0x10 != 0) parts.add("BURST")
        if (flags and 0x02 != 0) parts.add("WKMEUP")
        if (flags and 0x01 != 0) parts.add("WKUP")
        return parts.joinToString(",")
    }

    private fun hexAddressToDecimal(hex: String): Long {
        return hex.toLongOrNull(16) ?: 0L
    }

    // ── Import ───────────────────────────────────────────────────────────────

    fun importCsv(input: InputStream): List<Telegram> {
        val telegrams = mutableListOf<Telegram>()
        BufferedReader(InputStreamReader(input)).use { reader ->
            val header = reader.readLine() ?: return emptyList()
            // Validate it looks like the right format
            if (!header.contains("tstamp") || !header.contains("raw")) return emptyList()

            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                parseCsvLine(line)?.let { telegrams.add(it) }
            }
        }
        return telegrams
    }

    private fun parseCsvLine(line: String): Telegram? {
        val parts = line.split(";")
        if (parts.size < 18) return null

        val timestamp = parts[0].toLongOrNull() ?: return null
        val rssi = parts[2].toIntOrNull() ?: return null
        val dutyCycle = parts[5].toFloatOrNull() ?: 0f
        val rawField = parts[17]

        // Parse raw bytes — strip leading ':' and skip first byte (RSSI prefix)
        val rawHex = rawField.trimStart(':')
        val allBytes = hexToBytes(rawHex) ?: return null
        val rawBytes = if (allBytes.size > 1) allBytes.copyOfRange(1, allBytes.size) else return null

        // Decode all BidCoS fields from raw bytes (same logic as live USB path)
        val frame = TelegramParser.decodeBidCosFrame(rawBytes) ?: return null

        return Telegram(
            id = timestamp,
            timestamp = timestamp,
            rssi = rssi,
            lqi = 0,
            rawBytes = rawBytes,
            msgLen = frame.msgLen,
            msgCounter = frame.msgCounter,
            flags = frame.flags,
            msgType = frame.msgType,
            srcAddress = frame.srcAddress,
            dstAddress = frame.dstAddress,
            payload = frame.payload,
            dutyCycle = dutyCycle
        )
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.isEmpty()) return byteArrayOf()
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
