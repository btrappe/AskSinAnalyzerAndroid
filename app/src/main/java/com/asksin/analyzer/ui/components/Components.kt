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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.draw.drawBehind
import com.asksin.analyzer.model.NoiseSample
import com.asksin.analyzer.model.SequenceType
import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.model.TelegramSequence
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
        Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** AES verification badge */
@Composable
fun AesBadge(verified: Boolean?, modifier: Modifier = Modifier) {
    val color: Color
    val label: String
    when (verified) {
        true -> { color = Accent; label = "AES OK" }
        false -> { color = Danger; label = "AES FAIL" }
        null -> { color = TextMuted; label = "AES" }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Single telegram row card */
@Composable
fun TelegramRow(telegram: Telegram, onClick: () -> Unit, onAddressClick: (String) -> Unit = {}, nameResolver: (String) -> String? = { null }, aesVerified: Boolean? = null, modifier: Modifier = Modifier) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (telegram.hasAes) {
                        AesBadge(aesVerified)
                    }
                    Text(
                        telegram.msgSubtypeName?.let { "${telegram.msgTypeName} / $it" } ?: telegram.msgTypeName,
                        color = Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Address row
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddressChip(telegram.srcAddress, true, nameResolver(telegram.srcAddress)) { onAddressClick(telegram.srcAddress) }
                Spacer(Modifier.width(6.dp))
                Text("→", color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                AddressChip(telegram.dstAddress, false, nameResolver(telegram.dstAddress)) { onAddressClick(telegram.dstAddress) }
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
fun AddressChip(address: String, isSource: Boolean, deviceName: String? = null, onClick: () -> Unit = {}) {
    val bg = if (isSource) Accent.copy(alpha = 0.12f) else AccentDim.copy(alpha = 0.08f)
    val fg = if (isSource) Accent else AccentDim
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (deviceName != null) {
            Column {
                Text(address, color = fg, fontSize = 9.sp, fontFamily = MonoFont)
                Text(deviceName, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(address, color = fg, fontSize = 10.sp, fontFamily = MonoFont, fontWeight = FontWeight.Bold)
        }
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

// ── Sequence grouping composables ───────────────────────────────────────

fun sequenceColor(type: SequenceType): Color = when (type) {
    SequenceType.PAIRING -> SeqPairing
    SequenceType.CONFIG_READ -> SeqConfigRead
    SequenceType.CONFIG_WRITE -> SeqConfigWrite
    SequenceType.AES_HANDSHAKE -> SeqAes
    SequenceType.KEY_EXCHANGE -> SeqKeyExchange
    SequenceType.COMMAND_STATUS -> SeqCommandStatus
    SequenceType.GET_INFO -> SeqGetInfo
    SequenceType.EVENT_ACK -> SeqEventAck
    SequenceType.GENERIC_BIDI -> SeqGenericBidi
}

@Composable
fun SequenceTypeBadge(type: SequenceType, modifier: Modifier = Modifier) {
    val color = sequenceColor(type)
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(type.label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SequenceGroupHeader(
    sequence: TelegramSequence,
    telegrams: List<Telegram>,
    expanded: Boolean,
    nameResolver: (String) -> String? = { null },
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val color = sequenceColor(sequence.type)
    val first = telegrams.firstOrNull() ?: return

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // Colored left strip
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(10.dp)
        ) {
            // Top row: badge + count + time
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SequenceTypeBadge(sequence.type)
                    if (sequence.type == SequenceType.AES_HANDSHAKE) {
                        AesBadge(sequence.aesVerified)
                    }
                    Text(
                        "${telegrams.size} msgs",
                        color = TextMuted, fontSize = 10.sp
                    )
                }
                Text(first.formattedTime, color = TextMuted, fontSize = 10.sp, fontFamily = MonoFont)
            }
            Spacer(Modifier.height(4.dp))

            // Address row
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddressChip(first.srcAddress, true, nameResolver(first.srcAddress))
                Spacer(Modifier.width(6.dp))
                Text("→", color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                AddressChip(first.dstAddress, false, nameResolver(first.dstAddress))
                Spacer(Modifier.weight(1f))
                Text(
                    "${first.rssi} dBm",
                    color = TextMuted, fontSize = 9.sp, fontFamily = MonoFont
                )
            }
        }
        // Expand/collapse button
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp).align(Alignment.CenterVertically)) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SequenceGroupMember(
    telegram: Telegram,
    sequence: TelegramSequence,
    isLast: Boolean,
    nameResolver: (String) -> String? = { null },
    onClick: () -> Unit
) {
    val color = sequenceColor(sequence.type)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
    ) {
        // Connector line
        Box(
            Modifier
                .width(16.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    // Vertical line
                    drawLine(
                        color = color.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, if (isLast) size.height / 2 else size.height),
                        strokeWidth = 2f
                    )
                    // Horizontal tick
                    drawLine(
                        color = color.copy(alpha = 0.5f),
                        start = Offset(x, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2f
                    )
                    // Dot
                    drawCircle(
                        color = color,
                        radius = 3f,
                        center = Offset(x, size.height / 2)
                    )
                }
        )
        // Compact telegram card
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceVariant)
                .border(1.dp, Border, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(8.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(telegram.formattedTime, color = TextMuted, fontSize = 9.sp, fontFamily = MonoFont)
                    Text(telegram.msgTypeName, color = Accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddressChip(telegram.srcAddress, true, nameResolver(telegram.srcAddress))
                    Spacer(Modifier.width(4.dp))
                    Text("→", color = TextMuted, fontSize = 9.sp)
                    Spacer(Modifier.width(4.dp))
                    AddressChip(telegram.dstAddress, false, nameResolver(telegram.dstAddress))
                    Spacer(Modifier.weight(1f))
                    Text("${telegram.rssi} dBm", color = TextMuted, fontSize = 9.sp, fontFamily = MonoFont)
                }
            }
        }
    }
}
