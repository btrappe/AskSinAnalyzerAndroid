package com.asksin.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asksin.analyzer.model.DeviceStats
import com.asksin.analyzer.ui.components.AddressChip
import com.asksin.analyzer.ui.components.DutyCycleBadge
import com.asksin.analyzer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceStatsScreen(stats: Map<String, DeviceStats>) {
    val sorted = stats.values.sortedByDescending { it.lastSeen }

    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No devices seen yet", color = TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(sorted, key = { it.address }) { device ->
            DeviceCard(device)
        }
    }
}

@Composable
private fun DeviceCard(stats: DeviceStats) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AddressChip(stats.address, true)
            DutyCycleBadge(stats.dutyCyclePercent)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatChip("Telegrams", "${stats.telegramCount}")
            StatChip("Avg RSSI", "${stats.avgRssi} dBm")
            StatChip("Min", "${stats.minRssi} dBm")
            StatChip("Max", "${stats.maxRssi} dBm")
        }

        val lastSeen = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(stats.lastSeen))
        Text("Last seen: $lastSeen", color = TextMuted, fontSize = 10.sp)

        if (stats.dutyCyclePercent > 0.8f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Danger.copy(alpha = 0.12f))
                    .border(1.dp, Danger.copy(.3f), RoundedCornerShape(6.dp))
                    .padding(8.dp, 4.dp)
            ) {
                Text("⚠ Duty cycle near limit (1% / hour)", color = Danger, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 9.sp)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = MonoFont, fontWeight = FontWeight.Medium)
    }
}
