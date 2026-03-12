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
        val msgLen = parts[3].toIntOrNull() ?: return null
        val msgCounter = parts[4].toIntOrNull() ?: return null
        val dutyCycle = parts[5].toFloatOrNull() ?: 0f
        val flagsStr = parts[6]
        val fromAddr = parts[8].toLongOrNull() ?: return null
        val toAddr = parts[9].toLongOrNull() ?: return null
        val payloadHex = parts[16]
        val rawField = parts[17]

        val flags = parseFlags(flagsStr)
        val srcAddress = "%06X".format(fromAddr)
        val dstAddress = "%06X".format(toAddr)

        // Parse raw bytes — strip leading ':'
        val rawHex = rawField.trimStart(':')
        val rawBytes = hexToBytes(rawHex) ?: return null

        // Determine msgType from raw bytes if available (byte index 3),
        // or fall back to the type name column
        val msgType = if (rawBytes.size > 3) rawBytes[3].toInt() and 0xFF else 0

        val payload = hexToBytes(payloadHex) ?: byteArrayOf()

        return Telegram(
            id = timestamp,
            timestamp = timestamp,
            rssi = rssi,
            lqi = 0,
            rawBytes = rawBytes,
            msgLen = msgLen,
            msgCounter = msgCounter,
            flags = flags,
            msgType = msgType,
            srcAddress = srcAddress,
            dstAddress = dstAddress,
            payload = payload,
            dutyCycle = dutyCycle
        )
    }

    private fun parseFlags(flagsStr: String): Int {
        if (flagsStr.isBlank()) return 0
        var flags = 0
        for (part in flagsStr.split(",")) {
            when (part.trim().uppercase()) {
                "BCAST" -> flags = flags or 0x04
                "BIDI" -> flags = flags or 0x20
                "RPTEN" -> flags = flags or 0x80
                "RPTED" -> flags = flags or 0x40
                "BURST" -> flags = flags or 0x10
                "WKMEUP", "WAKEMEUP" -> flags = flags or 0x02
                "WKUP", "WAKEUP" -> flags = flags or 0x01
            }
        }
        return flags
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
