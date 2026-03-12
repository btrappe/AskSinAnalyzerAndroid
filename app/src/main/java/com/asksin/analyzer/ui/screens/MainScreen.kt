package com.asksin.analyzer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asksin.analyzer.model.NoiseSample
import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.serial.ConnectionState
import com.asksin.analyzer.ui.components.*
import com.asksin.analyzer.ui.theme.*
import com.hoho.android.usbserial.driver.UsbSerialDriver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    telegrams: List<Telegram>,
    noiseSamples: List<NoiseSample>,
    availableDevices: List<UsbSerialDriver>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onConnect: (UsbSerialDriver) -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit,
    onRefreshDevices: () -> Unit,
    onTelegramClick: (Telegram) -> Unit,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Background)) {

        // ── Top bar ──────────────────────────────────────────────────────────
        TopBar(
            connectionState = connectionState,
            onConnectClick = {
                onRefreshDevices()
                showDeviceDialog = true
            },
            onDisconnect = onDisconnect,
            onClear = onClear,
            onExport = onExport,
            onImport = onImport
        )

        // ── Noise chart ──────────────────────────────────────────────────────
        AnimatedVisibility(noiseSamples.isNotEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text("RSSI Noise Floor", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                NoiseChart(noiseSamples, Modifier.fillMaxSize())
            }
        }

        // ── Search bar ───────────────────────────────────────────────────────
        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            placeholder = { Text("Filter by address, type, hex…", color = TextMuted, fontSize = 12.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
            trailingIcon = if (filter.isNotEmpty()) {
                { IconButton(onClick = { onFilterChange("") }) {
                    Icon(Icons.Default.Clear, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }}
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent
            ),
            shape = RoundedCornerShape(8.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = MonoFont)
        )

        // ── Telegram count ───────────────────────────────────────────────────
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${telegrams.size} telegrams", color = TextMuted, fontSize = 10.sp)
        }

        // ── Telegram list ────────────────────────────────────────────────────
        if (telegrams.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radio, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (connectionState) {
                            is ConnectionState.Connected -> "Listening for telegrams…"
                            is ConnectionState.Connecting -> "Connecting…"
                            else -> "Connect your CC1101 sniffer via USB"
                        },
                        color = TextMuted, fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(telegrams, key = { it.id }) { t ->
                    TelegramRow(telegram = t, onClick = { onTelegramClick(t) }, onAddressClick = onFilterChange)
                }
            }
        }
    }

    // ── Device chooser dialog ────────────────────────────────────────────────
    if (showDeviceDialog) {
        DeviceChooserDialog(
            devices = availableDevices,
            onSelect = { driver ->
                showDeviceDialog = false
                onConnect(driver)
            },
            onDismiss = { showDeviceDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    connectionState: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected
    val label = when (connectionState) {
        is ConnectionState.Connected -> "${connectionState.deviceName} (${connectionState.driverName})"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Error -> connectionState.message
        else -> "Disconnected"
    }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface)
            .border(0.dp, Border)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App title
        Text(
            "AskSin",
            color = Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
        Text(
            " Analyzer",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(Modifier.width(12.dp))
        ConnectionIndicator(isConnected, label, Modifier.weight(1f))

        // Actions
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, "Menu", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Export CSV", color = TextPrimary, fontSize = 13.sp) },
                    onClick = { showMenu = false; onExport() },
                    leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Import CSV", color = TextPrimary, fontSize = 13.sp) },
                    onClick = { showMenu = false; onImport() },
                    leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                )
                HorizontalDivider(color = Border)
                DropdownMenuItem(
                    text = { Text("Clear", color = TextPrimary, fontSize = 13.sp) },
                    onClick = { showMenu = false; onClear() },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                )
            }
        }
        if (isConnected) {
            IconButton(onClick = onDisconnect, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.UsbOff, "Disconnect", tint = Danger, modifier = Modifier.size(18.dp))
            }
        } else {
            IconButton(onClick = onConnectClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Usb, "Connect", tint = Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DeviceChooserDialog(
    devices: List<UsbSerialDriver>,
    onSelect: (UsbSerialDriver) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Select USB Device", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            if (devices.isEmpty()) {
                Text("No supported USB serial devices found.\n\nCheck that your CC1101 sniffer is connected via OTG adapter.", color = TextSecondary, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { driver ->
                        val dev = driver.device
                        val driverName = driver.javaClass.simpleName.removeSuffix("SerialDriver")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariant)
                                .border(1.dp, Border, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dev.productName ?: "Unknown device", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("VID:${"%04X".format(dev.vendorId)} PID:${"%04X".format(dev.productId)} · $driverName",
                                    color = TextMuted, fontSize = 10.sp, fontFamily = MonoFont)
                            }
                            TextButton(onClick = { onSelect(driver) }) {
                                Text("Connect", color = Accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
