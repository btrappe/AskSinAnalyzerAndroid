package com.asksin.analyzer.ui.screens

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

    var selectedTab by remember { mutableStateOf(Tab.TELEGRAMS) }
    var detailTelegram by remember { mutableStateOf<Telegram?>(null) }

    // Show detail overlay if a telegram is selected
    detailTelegram?.let { t ->
        TelegramDetailScreen(telegram = t, onBack = { detailTelegram = null })
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
                    onFilterChange = viewModel::setFilter,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onClear = viewModel::clearTelegrams,
                    onRefreshDevices = viewModel::refreshDevices,
                    onTelegramClick = { detailTelegram = it }
                )
                Tab.DEVICES -> DeviceStatsScreen(stats = deviceStats)
            }
        }
    }
}
