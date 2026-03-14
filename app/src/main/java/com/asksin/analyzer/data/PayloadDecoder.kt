package com.asksin.analyzer.data

data class DecodedField(
    val name: String,
    val value: String,
    val rawHex: String = ""
)

object PayloadDecoder {

    fun decode(msgType: Int, payload: ByteArray): List<DecodedField> {
        if (payload.isEmpty()) return emptyList()
        return when (msgType) {
            0x00 -> decodeDevInfo(payload)
            0x01 -> decodeConfig(payload)
            0x02 -> decodeResponse(payload)
            0x03 -> listOf(DecodedField("Type", "AES Response", hex(payload)))
            0x04 -> listOf(DecodedField("Type", "Key Exchange", hex(payload)))
            0x10 -> decodeInfo(payload)
            0x11 -> decodeAction(payload)
            0x40 -> decodeRemoteEvent(payload)
            0x41 -> decodeSensorEvent(payload)
            0x53 -> decodeSensorData(payload)
            0x58 -> decodeClimateEvent(payload)
            0x5A -> decodeClimateCtrlEvent(payload)
            0x5E, 0x5F -> decodePowerEvent(payload)
            0x70 -> decodeWeatherEvent(payload)
            else -> emptyList()
        }
    }

    fun subtypeName(msgType: Int, payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        return when (msgType) {
            0x01 -> if (payload.size >= 2) configSubtypeName(payload[1].toInt() and 0xFF) else null
            0x02 -> responseSubtypeName(payload[0].toInt() and 0xFF)
            0x10 -> infoSubtypeName(payload[0].toInt() and 0xFF)
            0x11 -> actionSubtypeName(payload[0].toInt() and 0xFF)
            else -> null
        }
    }

    // ── CONFIG (0x01) ───────────────────────────────────────────────────────

    private fun configSubtypeName(sub: Int) = when (sub) {
        0x01 -> "PeerAdd"
        0x02 -> "PeerRemove"
        0x03 -> "PeerListReq"
        0x04 -> "ParamReq"
        0x05 -> "Start"
        0x06 -> "End"
        0x07 -> "ParamWrite"
        0x08 -> "WriteIndex"
        0x09 -> "SerialReq"
        0x0A -> "PairSerial"
        0x0E -> "StatusRequest"
        else -> null
    }

    private fun decodeConfig(p: ByteArray): List<DecodedField> {
        // CONFIG: payload[0]=channel, payload[1]=subcommand, payload[2+]=data
        val fields = mutableListOf<DecodedField>()
        fields.add(DecodedField("Channel", "${b(p, 0)}", h(b(p, 0))))
        if (p.size < 2) return fields

        val sub = b(p, 1)
        fields.add(DecodedField("Subcommand", "${configSubtypeName(sub) ?: "Unknown"} (${h(sub)})", h(sub)))

        when (sub) {
            0x01, 0x02 -> { // PeerAdd / PeerRemove
                if (p.size >= 7) {
                    fields.add(DecodedField("Peer Address", addr(p, 3), hex(p, 3, 3)))
                    fields.add(DecodedField("Peer Channel", "${b(p, 6)}", h(b(p, 6))))
                }
            }
            0x04, 0x05 -> { // ParamReq / Start
                if (p.size >= 8) {
                    fields.add(DecodedField("Peer Address", addr(p, 3), hex(p, 3, 3)))
                    fields.add(DecodedField("Peer Channel", "${b(p, 6)}", h(b(p, 6))))
                    fields.add(DecodedField("Param List", "${b(p, 7)}", h(b(p, 7))))
                }
            }
            0x07 -> { // ParamWrite
                if (p.size >= 4) {
                    fields.add(DecodedField("Param List", "${b(p, 2)}", h(b(p, 2))))
                    fields.add(DecodedField("Data", hex(p, 3, p.size - 3)))
                }
            }
            0x08 -> { // WriteIndex
                val pairs = mutableListOf<String>()
                var i = 2
                while (i + 1 < p.size) {
                    pairs.add("${h(b(p, i))}=${h(b(p, i + 1))}")
                    i += 2
                }
                if (pairs.isNotEmpty()) fields.add(DecodedField("Registers", pairs.joinToString(", ")))
            }
            0x0A -> { // PairSerial
                if (p.size >= 12) {
                    val serial = String(p, 2, 10, Charsets.US_ASCII)
                    fields.add(DecodedField("Serial", serial))
                }
            }
        }
        return fields
    }

    // ── RESPONSE (0x02) ─────────────────────────────────────────────────────

    private fun responseSubtypeName(sub: Int) = when (sub) {
        0x00 -> "ACK"
        0x01 -> "ACK_STATUS"
        0x02 -> "ACK2"
        0x04 -> "AES_CHALLENGE"
        0x80 -> "NACK"
        0x84 -> "NACK_TARGET_INVALID"
        else -> null
    }

    private fun decodeResponse(p: ByteArray): List<DecodedField> {
        val sub = b(p, 0)
        val fields = mutableListOf<DecodedField>()
        fields.add(DecodedField("Subtype", "${responseSubtypeName(sub) ?: "Unknown"} (${h(sub)})", h(sub)))

        when (sub) {
            0x01 -> { // ACK_STATUS
                if (p.size >= 4) {
                    fields.add(DecodedField("Channel", "${b(p, 1)}", h(b(p, 1))))
                    val status = b(p, 2)
                    fields.add(DecodedField("Status", statusString(status), h(status)))
                    fields.add(DecodedField("Flags", flagsString(b(p, 3)), h(b(p, 3))))
                }
            }
            0x04 -> { // AES_CHALLENGE
                if (p.size >= 7) {
                    fields.add(DecodedField("Challenge", hex(p, 1, 6)))
                }
            }
        }
        return fields
    }

    // ── INFO (0x10) ─────────────────────────────────────────────────────────

    private fun infoSubtypeName(sub: Int) = when (sub) {
        0x00 -> "Serial"
        0x01 -> "PeerList"
        0x02 -> "ParamResponsePairs"
        0x03 -> "ParamResponseSeq"
        0x04 -> "ParameterChange"
        0x06 -> "ActuatorStatus"
        0x0A -> "RTStatus"
        else -> null
    }

    private fun decodeInfo(p: ByteArray): List<DecodedField> {
        val sub = b(p, 0)
        val fields = mutableListOf<DecodedField>()
        fields.add(DecodedField("Subtype", "${infoSubtypeName(sub) ?: "Unknown"} (${h(sub)})", h(sub)))

        when (sub) {
            0x00 -> { // Serial
                if (p.size >= 11) {
                    fields.add(DecodedField("Serial", String(p, 1, 10, Charsets.US_ASCII)))
                }
            }
            0x01 -> { // PeerList
                val peers = mutableListOf<String>()
                var i = 1
                while (i + 3 < p.size) {
                    val pa = addr(p, i)
                    val ch = b(p, i + 3)
                    if (pa != "000000") peers.add("$pa:$ch")
                    i += 4
                }
                if (peers.isNotEmpty()) fields.add(DecodedField("Peers", peers.joinToString(", ")))
            }
            0x02 -> { // ParamResponsePairs
                val pairs = mutableListOf<String>()
                var i = 1
                while (i + 1 < p.size) {
                    pairs.add("${h(b(p, i))}=${h(b(p, i + 1))}")
                    i += 2
                }
                if (pairs.isNotEmpty()) fields.add(DecodedField("Params", pairs.joinToString(", ")))
            }
            0x03 -> { // ParamResponseSeq
                if (p.size >= 2) {
                    fields.add(DecodedField("Offset", "${h(b(p, 1))}"))
                    if (p.size > 2) fields.add(DecodedField("Data", hex(p, 2, p.size - 2)))
                }
            }
            0x06 -> { // ActuatorStatus
                if (p.size >= 4) {
                    fields.add(DecodedField("Channel", "${b(p, 1)}", h(b(p, 1))))
                    val status = b(p, 2)
                    fields.add(DecodedField("Status", statusString(status), h(status)))
                    fields.add(DecodedField("Flags", flagsString(b(p, 3)), h(b(p, 3))))
                }
            }
        }
        return fields
    }

    // ── ACTION (0x11) ───────────────────────────────────────────────────────

    private fun actionSubtypeName(sub: Int) = when (sub) {
        0x00 -> "InhibitOff"
        0x01 -> "InhibitOn"
        0x02 -> "Set"
        0x03 -> "StopChange"
        0x04 -> "Reset"
        0x80 -> "Command"
        0x81 -> "Level"
        0x82 -> "SleepMode"
        0xCA -> "EnterBootloader"
        else -> null
    }

    private fun decodeAction(p: ByteArray): List<DecodedField> {
        val sub = b(p, 0)
        val fields = mutableListOf<DecodedField>()
        fields.add(DecodedField("Subtype", "${actionSubtypeName(sub) ?: "Unknown"} (${h(sub)})", h(sub)))

        when (sub) {
            0x02 -> { // Set
                if (p.size >= 2) {
                    val value = b(p, 1)
                    fields.add(DecodedField("Value", statusString(value), h(value)))
                    if (p.size >= 4) fields.add(DecodedField("Ramp Time", "${word(p, 2) * 0.5}s", hex(p, 2, 2)))
                    if (p.size >= 6) fields.add(DecodedField("Duration", "${word(p, 4) * 0.5}s", hex(p, 4, 2)))
                }
            }
            0x80 -> { // Command (display text)
                if (p.size > 1) fields.add(DecodedField("Data", hex(p, 1, p.size - 1)))
            }
        }
        return fields
    }

    // ── REMOTE_EVENT (0x40) ─────────────────────────────────────────────────

    private fun decodeRemoteEvent(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.isNotEmpty()) {
            val cmd = b(p, 0)
            fields.add(DecodedField("Channel", "${cmd and 0x3F}"))
            fields.add(DecodedField("Press", if (cmd and 0x40 != 0) "LONG" else "SHORT"))
        }
        if (p.size >= 2) fields.add(DecodedField("Counter", "${b(p, 1)}"))
        return fields
    }

    // ── SENSOR_EVENT (0x41) ─────────────────────────────────────────────────

    private fun decodeSensorEvent(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.isNotEmpty()) {
            val cmd = b(p, 0)
            fields.add(DecodedField("Channel", "${cmd and 0x3F}"))
            fields.add(DecodedField("Press", if (cmd and 0x40 != 0) "LONG" else "SHORT"))
        }
        if (p.size >= 2) fields.add(DecodedField("Counter", "${b(p, 1)}"))
        if (p.size >= 3) fields.add(DecodedField("Value", "${b(p, 2)}", h(b(p, 2))))
        return fields
    }

    // ── SENSOR_DATA (0x53) ──────────────────────────────────────────────────

    private fun decodeSensorData(p: ByteArray): List<DecodedField> {
        // Generic: show raw data with field hints
        val fields = mutableListOf<DecodedField>()
        fields.add(DecodedField("Data", hex(p)))
        return fields
    }

    // ── CLIMATE_EVENT (0x58) ────────────────────────────────────────────────

    private fun decodeClimateEvent(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.size >= 2) {
            val temp = ((b(p, 0) and 0x3F) shl 8 or b(p, 1)) / 10.0
            fields.add(DecodedField("Temperature", "%.1f C".format(temp)))
        }
        if (p.size >= 3) {
            fields.add(DecodedField("Humidity", "${b(p, 2)}%"))
        }
        return fields
    }

    // ── CLIMATECTRL_EVENT (0x5A) ────────────────────────────────────────────

    private fun decodeClimateCtrlEvent(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.size >= 2) {
            val setpoint = b(p, 1) / 2.0
            fields.add(DecodedField("Setpoint", "%.1f C".format(setpoint)))
        }
        if (p.size >= 3) {
            val mode = when (b(p, 2) and 0x03) {
                0 -> "AUTO"
                1 -> "MANUAL"
                2 -> "PARTY"
                3 -> "BOOST"
                else -> "?"
            }
            fields.add(DecodedField("Mode", mode))
        }
        return fields
    }

    // ── POWER_EVENT (0x5E/0x5F) ─────────────────────────────────────────────

    private fun decodePowerEvent(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.size >= 4) {
            // Energy counter (3 bytes, big-endian) * 0.1 Wh
            val energy = ((b(p, 0) shl 16) or (b(p, 1) shl 8) or b(p, 2)) * 0.1
            fields.add(DecodedField("Energy", "%.1f Wh".format(energy)))
        }
        if (p.size >= 7) {
            val power = ((b(p, 4) shl 16) or (b(p, 5) shl 8) or b(p, 6)) / 100.0
            fields.add(DecodedField("Power", "%.2f W".format(power)))
        }
        if (p.size >= 9) {
            val current = ((b(p, 7) shl 8) or b(p, 8)) / 1.0
            fields.add(DecodedField("Current", "%.0f mA".format(current)))
        }
        if (p.size >= 11) {
            val voltage = ((b(p, 9) shl 8) or b(p, 10)) / 10.0
            fields.add(DecodedField("Voltage", "%.1f V".format(voltage)))
        }
        return fields
    }

    // ── WEATHER_EVENT (0x70) ────────────────────────────────────────────────

    private fun decodeWeatherEvent(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.size >= 3) {
            // Temperature: payload[0] bit 0-3 + payload[1], signed
            val tempRaw = ((b(p, 0) and 0x7F) shl 8) or b(p, 1)
            val temp = if (b(p, 0) and 0x80 != 0) -(tempRaw and 0x3FFF) / 10.0 else tempRaw / 10.0
            fields.add(DecodedField("Temperature", "%.1f C".format(temp)))
            fields.add(DecodedField("Humidity", "${b(p, 2)}%"))
        }
        return fields
    }

    // ── DEVINFO (0x00) ──────────────────────────────────────────────────────

    private fun decodeDevInfo(p: ByteArray): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        if (p.size >= 1) fields.add(DecodedField("Firmware", "0x${h(b(p, 0))}"))
        if (p.size >= 3) fields.add(DecodedField("Device Type", "0x${hex(p, 1, 2)}"))
        if (p.size >= 13) fields.add(DecodedField("Serial", String(p, 3, 10, Charsets.US_ASCII)))
        if (p.size >= 14) fields.add(DecodedField("Device Class", h(b(p, 13))))
        if (p.size >= 17) fields.add(DecodedField("Peer Address", addr(p, 14)))
        if (p.size >= 18) fields.add(DecodedField("Peer Channel", "${b(p, 17)}"))
        return fields
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun b(p: ByteArray, i: Int): Int = p[i].toInt() and 0xFF

    private fun h(v: Int): String = "%02X".format(v)

    private fun word(p: ByteArray, i: Int): Int = (b(p, i) shl 8) or b(p, i + 1)

    private fun addr(p: ByteArray, offset: Int): String =
        "%02X%02X%02X".format(b(p, offset), b(p, offset + 1), b(p, offset + 2))

    private fun hex(p: ByteArray): String =
        p.joinToString("") { "%02X".format(it) }

    private fun hex(p: ByteArray, offset: Int, length: Int): String =
        p.drop(offset).take(length).joinToString("") { "%02X".format(it) }

    private fun statusString(value: Int): String = when (value) {
        0x00 -> "OFF (0x00)"
        0xC8 -> "ON (0xC8)"
        else -> "${(value * 100) / 200}% (${h(value)})"
    }

    private fun flagsString(f: Int): String {
        val parts = mutableListOf<String>()
        if (f and 0x01 != 0) parts.add("LOWBAT")
        if (f and 0x02 != 0) parts.add("SIGN")
        if (f and 0x04 != 0) parts.add("CFGCHG")
        return if (parts.isEmpty()) h(f) else parts.joinToString(",") + " (${h(f)})"
    }
}
