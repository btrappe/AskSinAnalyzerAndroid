package com.asksin.analyzer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asksin.analyzer.data.CsvExporter
import com.asksin.analyzer.data.TelegramParser
import com.asksin.analyzer.model.DeviceStats
import com.asksin.analyzer.model.NoiseSample
import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.serial.ConnectionState
import com.asksin.analyzer.serial.UsbSerialManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_TELEGRAMS = 500
private const val MAX_NOISE_SAMPLES = 200
private const val DUTY_CYCLE_WINDOW_MS = 3_600_000L   // 1 hour

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val usbManager = UsbSerialManager(app)

    val connectionState: StateFlow<ConnectionState> = usbManager.connectionState

    private val _telegrams = MutableStateFlow<List<Telegram>>(emptyList())
    val telegrams: StateFlow<List<Telegram>> = _telegrams.asStateFlow()

    private val _noiseSamples = MutableStateFlow<List<NoiseSample>>(emptyList())
    val noiseSamples: StateFlow<List<NoiseSample>> = _noiseSamples.asStateFlow()

    private val _deviceStats = MutableStateFlow<Map<String, DeviceStats>>(emptyMap())
    val deviceStats: StateFlow<Map<String, DeviceStats>> = _deviceStats.asStateFlow()

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    val filteredTelegrams: StateFlow<List<Telegram>> = _telegrams
        .map { list ->
            val q = _filter.value.trim().uppercase()
            if (q.isEmpty()) list else list.filter {
                it.srcAddress.contains(q) ||
                it.dstAddress.contains(q) ||
                it.msgTypeName.uppercase().contains(q) ||
                it.formattedRaw.replace(" ", "").contains(q)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableDevices: StateFlow<List<UsbSerialDriver>> = MutableStateFlow(emptyList())

    init {
        // Start consuming serial lines
        viewModelScope.launch(Dispatchers.IO) {
            for (line in usbManager.lineChannel) {
                when (val result = TelegramParser.parse(line)) {
                    is TelegramParser.ParseResult.TelegramResult -> addTelegram(result.telegram)
                    is TelegramParser.ParseResult.NoiseResult ->
                        addNoiseSample(NoiseSample(result.timestampMs, result.rssiDbm))
                    else -> {}
                }
            }
        }
        refreshDevices()
    }

    fun refreshDevices() {
        val drivers = usbManager.availableDevices().map { it.second }
        (availableDevices as MutableStateFlow).value = drivers
    }

    fun connect(driver: UsbSerialDriver) = usbManager.connect(driver)
    fun disconnect() = usbManager.disconnect()
    fun setFilter(q: String) { _filter.value = q }

    fun clearTelegrams() {
        _telegrams.value = emptyList()
        _deviceStats.value = emptyMap()
        _noiseSamples.value = emptyList()
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    CsvExporter.export(_telegrams.value, out)
                }
            } catch (_: Exception) { }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val imported = CsvExporter.importCsv(input)
                    if (imported.isNotEmpty()) {
                        _telegrams.value = imported
                        _deviceStats.value = emptyMap()
                        imported.forEach { updateDeviceStats(it) }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun addTelegram(t: Telegram) {
        val current = _telegrams.value.toMutableList()
        current.add(0, t)  // prepend newest
        if (current.size > MAX_TELEGRAMS) current.removeAt(current.size - 1)
        _telegrams.value = current
        updateDeviceStats(t)
    }

    private fun addNoiseSample(s: NoiseSample) {
        val current = _noiseSamples.value.toMutableList()
        current.add(s)
        if (current.size > MAX_NOISE_SAMPLES) current.removeAt(0)
        _noiseSamples.value = current
    }

    private fun updateDeviceStats(t: Telegram) {
        val now = System.currentTimeMillis()
        val stats = _deviceStats.value.toMutableMap()

        listOf(t.srcAddress).forEach { addr ->
            val existing = stats[addr]
            val allTelegramsFromDevice = _telegrams.value.filter { it.srcAddress == addr }
            val recentTelegrams = allTelegramsFromDevice.filter { now - it.timestamp < DUTY_CYCLE_WINDOW_MS }

            // Rough duty cycle: assume ~10ms per telegram on 868 MHz
            val dutyCycle = (recentTelegrams.size * 10f) / (DUTY_CYCLE_WINDOW_MS / 1000f) / 10f  // percent

            val rssiList = allTelegramsFromDevice.map { it.rssi }
            stats[addr] = DeviceStats(
                address = addr,
                telegramCount = (existing?.telegramCount ?: 0) + 1,
                lastSeen = now,
                dutyCyclePercent = dutyCycle.coerceIn(0f, 100f),
                avgRssi = if (rssiList.isEmpty()) 0 else rssiList.average().toInt(),
                minRssi = rssiList.minOrNull() ?: 0,
                maxRssi = rssiList.maxOrNull() ?: 0
            )
        }
        _deviceStats.value = stats
    }

    override fun onCleared() {
        super.onCleared()
        usbManager.release()
    }
}
