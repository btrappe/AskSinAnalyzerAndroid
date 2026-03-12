package com.asksin.analyzer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asksin.analyzer.model.NoiseSample
import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.ui.theme.*

/** Pulsing dot that shows connection status */
@Composable
fun ConnectionIndicator(connected: Boolean, label: String, modifier: Modifier = Modifier) {
    val dotColor by animateColorAsState(
        if (connected) Accent else Danger,
        animationSpec = tween(300), label = "dot"
    )
    Row(modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

/** RSSI bar — horizontal fill, colour-coded */
@Composable
fun RssiBar(rssiDbm: Int, modifier: Modifier = Modifier) {
    val fill = ((rssiDbm + 100).coerceIn(0, 70) / 70f)
    val color = when {
        rssiDbm > -60 -> RssiGood
        rssiDbm > -80 -> RssiMed
        else          -> RssiBad
    }
    val animFill by animateFloatAsState(fill, label = "rssi")
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RSSI", color = TextMuted, fontSize = 9.sp, modifier = Modifier.width(30.dp))
            Text("${rssiDbm} dBm", color = color, fontSize = 10.sp, fontFamily = MonoFont)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animFill)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/** Duty cycle pill */
@Composable
fun DutyCycleBadge(percent: Float, modifier: Modifier = Modifier) {
    val color = when {
        percent > 0.8f -> Danger
        percent > 0.5f -> Warning
        else           -> AccentDim
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "DC ${"%.2f".format(percent)}%",
            color = color,
            fontSize = 10.sp,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Single telegram row card */
@Composable
fun TelegramRow(telegram: Telegram, onClick: () -> Unit, onAddressClick: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column {
            // Top row: time + type + addresses
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    telegram.formattedTime,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = MonoFont
                )
                Text(
                    telegram.msgTypeName,
                    color = Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))

            // Address row
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddressChip(telegram.srcAddress, true) { onAddressClick(telegram.srcAddress) }
                Spacer(Modifier.width(6.dp))
                Text("→", color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                AddressChip(telegram.dstAddress, false) { onAddressClick(telegram.dstAddress) }
                Spacer(Modifier.weight(1f))
                // LQI dot
                val lqiColor = if (telegram.lqiGood) RssiGood else Warning
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(lqiColor)
                )
                Spacer(Modifier.width(4.dp))
                Text("LQI ${telegram.lqi}", color = TextMuted, fontSize = 9.sp)
            }
            Spacer(Modifier.height(6.dp))

            // RSSI
            RssiBar(telegram.rssi, Modifier.fillMaxWidth())

            Spacer(Modifier.height(4.dp))

            // Hex data (truncated)
            Text(
                telegram.formattedRaw,
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = MonoFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AddressChip(address: String, isSource: Boolean, onClick: () -> Unit = {}) {
    val bg = if (isSource) Accent.copy(alpha = 0.12f) else AccentDim.copy(alpha = 0.08f)
    val fg = if (isSource) Accent else AccentDim
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(address, color = fg, fontSize = 10.sp, fontFamily = MonoFont, fontWeight = FontWeight.Bold)
    }
}

/** RSSI noise floor sparkline */
@Composable
fun NoiseChart(samples: List<NoiseSample>, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minV = -120f
        val maxV = -30f

        fun yFor(v: Int) = h - ((v - minV) / (maxV - minV)) * h

        val path = Path()
        samples.forEachIndexed { i, s ->
            val x = (i.toFloat() / (samples.size - 1).coerceAtLeast(1)) * w
            val y = yFor(s.rssiDbm).coerceIn(0f, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path,
            color = Accent.copy(alpha = 0.7f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // -80 dBm threshold line
        val thresholdY = yFor(-80).coerceIn(0f, h)
        drawLine(
            color = Warning.copy(alpha = 0.4f),
            start = Offset(0f, thresholdY),
            end = Offset(w, thresholdY),
            strokeWidth = 1f
        )
    }
}
