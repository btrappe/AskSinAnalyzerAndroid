package com.asksin.analyzer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asksin.analyzer.MainViewModel
import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector) {
    TELEGRAMS("Telegrams", Icons.Default.List),
    DEVICES("Devices", Icons.Default.Router)
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val telegrams by viewModel.filteredTelegrams.collectAsState()
    val allTelegrams by viewModel.telegrams.collectAsState()
    val noiseSamples by viewModel.noiseSamples.collectAsState()
    val deviceStats by viewModel.deviceStats.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()
    val filter by viewModel.filter.collectAsState()

    val deviceNames by viewModel.deviceNames.collectAsState()
    val ccuFetchState by viewModel.ccuFetchState.collectAsState()
    val nameResolver: (String) -> String? = { viewModel.resolveAddress(it) }

    var selectedTab by remember { mutableStateOf(Tab.TELEGRAMS) }
    var detailTelegram by remember { mutableStateOf<Telegram?>(null) }
    var showDeviceNames by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    // Show device names management screen
    if (showDeviceNames) {
        DeviceNamesScreen(
            deviceNames = deviceNames,
            ccuFetchState = ccuFetchState,
            initialCcuIp = viewModel.getCcuIp(),
            onFetchFromCcu = viewModel::fetchFromCcu,
            onAddDevice = viewModel::addDevice,
            onUpdateDevice = viewModel::updateDevice,
            onDeleteDevice = viewModel::deleteDevice,
            onClearAll = viewModel::clearDeviceNames,
            onBack = { showDeviceNames = false }
        )
        return
    }

    // Show detail overlay if a telegram is selected
    detailTelegram?.let { t ->
        TelegramDetailScreen(telegram = t, onBack = { detailTelegram = null }, nameResolver = nameResolver)
        return
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp
            ) {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Accent.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                Tab.TELEGRAMS -> MainScreen(
                    connectionState = connectionState,
                    telegrams = telegrams,
                    noiseSamples = noiseSamples,
                    availableDevices = availableDevices,
                    filter = filter,
                    nameResolver = nameResolver,
                    onFilterChange = viewModel::setFilter,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onClear = viewModel::clearTelegrams,
                    onRefreshDevices = viewModel::refreshDevices,
                    onTelegramClick = { detailTelegram = it },
                    onExport = {
                        val ts = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("TelegramsXS_$ts.csv")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("text/*", "text/csv", "application/octet-stream"))
                    },
                    onShowDeviceNames = { showDeviceNames = true }
                )
                Tab.DEVICES -> DeviceStatsScreen(stats = deviceStats, nameResolver = nameResolver)
            }
        }
    }
}
