package com.asksin.analyzer.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val ACTION_USB_PERMISSION = "com.asksin.analyzer.USB_PERMISSION"
private const val BAUD_RATE = 57600   // AskSinSniffer328P default baud rate

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String, val driverName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Manages the USB serial connection to the CC1101 / ATMega328P sniffer.
 * Uses the usb-serial-for-android library for driver support (FTDI, CH340, CP210x, PL2303).
 */
class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Channel that emits complete lines from the serial port
    val lineChannel = Channel<String>(capacity = Channel.UNLIMITED)

    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var lineBuffer = StringBuilder()

    // ── USB permission broadcast receiver ────────────────────────────────────
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (granted && device != null) openDevice(device)
                else _connectionState.value = ConnectionState.Error("USB permission denied")
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    /** Returns list of connected USB serial devices with supported drivers */
    fun availableDevices(): List<Pair<UsbDevice, UsbSerialDriver>> {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return drivers.map { driver -> driver.device to driver }
    }

    /** Request permission (if needed) and connect to a device */
    fun connect(driver: UsbSerialDriver) {
        val device = driver.device
        _connectionState.value = ConnectionState.Connecting(device.deviceName)

        if (!usbManager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(context, 0,
                Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, intent)
        } else {
            openDevice(device)
        }
    }

    private fun openDevice(device: UsbDevice) {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.firstOrNull { it.device == device }
            ?: run {
                _connectionState.value = ConnectionState.Error("No driver found for device")
                return
            }

        val connection = usbManager.openDevice(device)
            ?: run {
                _connectionState.value = ConnectionState.Error("Cannot open USB device")
                return
            }

        try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            serialPort = port
            _connectionState.value = ConnectionState.Connected(
                deviceName = device.productName ?: device.deviceName,
                driverName = driver.javaClass.simpleName.removeSuffix("SerialDriver")
            )

            // Start async IO
            ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    processData(data)
                }
                override fun onRunError(e: Exception) {
                    _connectionState.value = ConnectionState.Error(e.message ?: "IO error")
                    disconnect()
                }
            })
            ioManager?.start()

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Failed to open port: ${e.message}")
        }
    }

    private fun processData(data: ByteArray) {
        val text = String(data, Charsets.US_ASCII)
        lineBuffer.append(text)
        var newline = lineBuffer.indexOf('\n')
        while (newline >= 0) {
            val line = lineBuffer.substring(0, newline).trim()
            if (line.isNotEmpty()) lineChannel.trySend(line)
            lineBuffer.delete(0, newline + 1)
            newline = lineBuffer.indexOf('\n')
        }
    }

    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try { serialPort?.close() } catch (_: Exception) {}
        serialPort = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun release() {
        disconnect()
        try { context.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
        lineChannel.close()
    }
}
