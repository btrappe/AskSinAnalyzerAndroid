package com.asksin.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.ui.components.AddressChip
import com.asksin.analyzer.ui.components.RssiBar
import com.asksin.analyzer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramDetailScreen(telegram: Telegram, onBack: () -> Unit, nameResolver: (String) -> String? = { null }) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Telegram Detail", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header card
            InfoCard("Overview") {
                InfoRow("Time", telegram.formattedTime)
                InfoRow("Type", telegram.msgTypeName, valueColor = Accent)
                InfoRow("Counter", "0x%02X (%d)".format(telegram.msgCounter, telegram.msgCounter))
                InfoRow("Flags", "0x%02X".format(telegram.flags))
                InfoRow("Length", "${telegram.msgLen} bytes")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SRC", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                    AddressChip(telegram.srcAddress, true, nameResolver(telegram.srcAddress))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DST", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                    AddressChip(telegram.dstAddress, false, nameResolver(telegram.dstAddress))
                    if (telegram.isBroadcast) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Warning.copy(.2f)).padding(4.dp, 2.dp)) {
                            Text("BROADCAST", color = Warning, fontSize = 9.sp)
                        }
                    }
                }
            }

            // Signal quality
            InfoCard("Signal Quality") {
                RssiBar(telegram.rssi, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                InfoRow("LQI", "${telegram.lqi} (${if (telegram.lqiGood) "Good" else "Poor"})",
                    valueColor = if (telegram.lqiGood) RssiGood else Warning)
            }

            // Payload
            if (telegram.payload.isNotEmpty()) {
                InfoCard("Payload (${telegram.payload.size} bytes)") {
                    HexDump(telegram.payload)
                }
            }

            // Full raw bytes
            InfoCard("Raw Frame") {
                HexDump(telegram.rawBytes)
            }

            // BidCoS byte map
            InfoCard("BidCoS Frame Map") {
                ByteMapRow("Byte 0", "LEN", "0x%02X".format(telegram.msgLen))
                ByteMapRow("Byte 1", "CNT", "0x%02X".format(telegram.msgCounter))
                ByteMapRow("Byte 2", "FLAGS", "0x%02X".format(telegram.flags), flagsDescription(telegram.flags))
                ByteMapRow("Byte 3", "TYPE", "0x%02X".format(telegram.msgType), telegram.msgTypeName)
                ByteMapRow("Byte 4-6", "SRC", telegram.srcAddress)
                ByteMapRow("Byte 7-9", "DST", telegram.dstAddress)
                if (telegram.payload.isNotEmpty()) {
                    ByteMapRow("Byte 10+", "PAYLOAD", "${telegram.payload.size} bytes")
                }
            }
        }
    }
}

private fun flagsDescription(flags: Int): String {
    val parts = mutableListOf<String>()
    if (flags and 0x01 != 0) parts.add("WAKEUP")
    if (flags and 0x02 != 0) parts.add("WAKEMEUP")
    if (flags and 0x04 != 0) parts.add("BCAST")
    if (flags and 0x10 != 0) parts.add("BURST")
    if (flags and 0x20 != 0) parts.add("BIDI")
    if (flags and 0x40 != 0) parts.add("RPTED")
    if (flags and 0x80 != 0) parts.add("RPTEN")
    return if (parts.isEmpty()) "–" else parts.joinToString(" | ")
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(title, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = MonoFont, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ByteMapRow(position: String, field: String, value: String, note: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(position, color = TextMuted, fontSize = 9.sp, modifier = Modifier.width(56.dp))
        Text(field, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(64.dp))
        Text(value, color = Accent, fontSize = 11.sp, fontFamily = MonoFont, modifier = Modifier.weight(1f))
        if (note.isNotEmpty()) Text(note, color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun HexDump(bytes: ByteArray) {
    val lines = bytes.toList().chunked(16)
    Column {
        lines.forEachIndexed { lineIdx, chunk ->
            Row {
                Text(
                    "%04X".format(lineIdx * 16),
                    color = TextMuted, fontSize = 9.sp, fontFamily = MonoFont,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    chunk.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
                    color = TextPrimary, fontSize = 9.sp, fontFamily = MonoFont,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    chunk.map { b -> val c = (b.toInt() and 0xFF).toChar(); if (c.isLetterOrDigit()) c else '.' }.joinToString(""),
                    color = TextSecondary, fontSize = 9.sp, fontFamily = MonoFont
                )
            }
        }
    }
}
