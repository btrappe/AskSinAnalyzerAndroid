package com.asksin.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asksin.analyzer.MainViewModel
import com.asksin.analyzer.model.DeviceInfo
import com.asksin.analyzer.ui.components.AddressChip
import com.asksin.analyzer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceNamesScreen(
    deviceNames: Map<String, DeviceInfo>,
    ccuFetchState: MainViewModel.CcuFetchState,
    initialCcuIp: String,
    onFetchFromCcu: (String) -> Unit,
    onAddDevice: (DeviceInfo) -> Unit,
    onUpdateDevice: (DeviceInfo) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onClearAll: () -> Unit,
    onAddUnknown: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    var ccuIp by remember { mutableStateOf(initialCcuIp) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val sortedDevices = remember(deviceNames) {
        deviceNames.values.sortedBy { it.name.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Names", color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add device", tint = Accent)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = TextSecondary)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Export JSON", color = TextPrimary, fontSize = 13.sp) },
                                onClick = { showMenu = false; onExport() },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import JSON", color = TextPrimary, fontSize = 13.sp) },
                                onClick = { showMenu = false; onImport() },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add unknown from data", color = TextPrimary, fontSize = 13.sp) },
                                onClick = { showMenu = false; onAddUnknown() },
                                leadingIcon = { Icon(Icons.Default.Add, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                            )
                            HorizontalDivider(color = Border)
                            DropdownMenuItem(
                                text = { Text("Clear all", color = TextPrimary, fontSize = 13.sp) },
                                onClick = { showMenu = false; onClearAll() }
                            )
                        }
                    }
                }
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // CCU fetch section
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ccuIp,
                    onValueChange = { ccuIp = it },
                    label = { Text("CCU IP address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextMuted
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { if (ccuIp.isNotBlank()) onFetchFromCcu(ccuIp.trim()) },
                    enabled = ccuIp.isNotBlank() && ccuFetchState !is MainViewModel.CcuFetchState.Loading
                ) {
                    Icon(Icons.Default.Refresh, "Fetch from CCU", tint = Accent)
                }
            }

            // Fetch status
            when (ccuFetchState) {
                is MainViewModel.CcuFetchState.Loading ->
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = Accent
                    )
                is MainViewModel.CcuFetchState.Success ->
                    Text(
                        "Fetched ${ccuFetchState.count} devices",
                        color = RssiGood, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                is MainViewModel.CcuFetchState.Error ->
                    Text(
                        "Error: ${ccuFetchState.message}",
                        color = Danger, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                else -> Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "${sortedDevices.size} devices",
                color = TextMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))

            // Device list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedDevices, key = { it.address }) { device ->
                    DeviceNameRow(
                        device = device,
                        onEdit = { editingDevice = device },
                        onDelete = { onDeleteDevice(device.address) }
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        DeviceEditDialog(
            title = "Add Device",
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { info ->
                onAddDevice(info)
                showAddDialog = false
            }
        )
    }

    // Edit dialog
    editingDevice?.let { device ->
        DeviceEditDialog(
            title = "Edit Device",
            initial = device,
            onDismiss = { editingDevice = null },
            onConfirm = { info ->
                onUpdateDevice(info)
                editingDevice = null
            }
        )
    }
}

@Composable
private fun DeviceNameRow(
    device: DeviceInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AddressChip(device.address, isSource = true)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    color = TextPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (device.serial.isNotEmpty() || device.type.isNotEmpty()) {
                    Text(
                        listOfNotNull(
                            device.serial.ifEmpty { null },
                            device.type.ifEmpty { null }
                        ).joinToString(" / "),
                        color = TextMuted, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (device.manuallyAdded) {
                    Text("manual", color = AccentDim, fontSize = 9.sp)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = Danger.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceEditDialog(
    title: String,
    initial: DeviceInfo?,
    onDismiss: () -> Unit,
    onConfirm: (DeviceInfo) -> Unit
) {
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var serial by remember { mutableStateOf(initial?.serial ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    val isEdit = initial != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(6) },
                    label = { Text("RF Address (hex)") },
                    singleLine = true,
                    enabled = !isEdit,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextMuted,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextMuted,
                        disabledBorderColor = Border,
                        disabledLabelColor = TextMuted
                    )
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextMuted
                    )
                )
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Serial (optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextMuted
                    )
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type (optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextMuted
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        DeviceInfo(
                            address = address,
                            name = name,
                            serial = serial,
                            type = type,
                            manuallyAdded = initial?.manuallyAdded ?: true
                        )
                    )
                },
                enabled = address.length == 6 && name.isNotBlank()
            ) {
                Text("Save", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
