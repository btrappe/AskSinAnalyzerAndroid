# AskSin Analyzer – Native Android App

A native Android application for analysing HomeMatic BidCoS radio telegrams
captured by the **AskSinSniffer328P** sketch running on an ATMega328P + CC1101
module, connected via USB OTG.

---

## Features

| Feature | Details |
|---|---|
| USB Serial | Auto-detects FTDI, CH340, CP210x, PL2303 adapters |
| Telegram list | Live scrolling list, newest-first, colour-coded |
| BidCoS decoder | Parses length, counter, flags, type, SRC/DST addresses, payload |
| Telegram detail | Full hex dump with byte-map overlay |
| RSSI bar | Colour-coded signal strength per telegram |
| LQI indicator | Link quality from CC1101 status bytes |
| RSSI Noise chart | Real-time sparkline of ambient noise floor |
| Device stats | Per-device telegram count, avg/min/max RSSI, duty-cycle estimate |
| Duty cycle alert | Warning when device approaches 1%/h limit on 868 MHz |
| Filter | Search by address, message type, or hex content |
| Auto-launch | App opens automatically when sniffer is plugged in |

---

## Hardware Required

- Android phone/tablet with **USB OTG Host** support (Android 8+)
- USB OTG adapter (USB-A female to your phone's USB-C/Micro-USB)
- ATMega328P (Arduino Nano / Pro Mini) + CC1101 module
  - Flashed with the **AskSinSniffer328P** sketch
  - Connected via USB-UART (FTDI FT232, CH340G, CP2102, or PL2303)

---

## Building

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK with API 26–35

### Steps

```bash
# Clone / place this folder, then open in Android Studio
# or build from command line:

cd AskSinAnalyzerAndroid
./gradlew assembleDebug

# Install on connected device:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Dependencies (auto-downloaded by Gradle)
- `com.github.mik3y:usb-serial-for-android:3.7.0` — USB serial drivers
- Jetpack Compose BOM 2024.06.00
- AndroidX Navigation Compose
- Kotlin Coroutines

---

## Serial Protocol

The app expects the AskSinSniffer328P serial output format at **57600 baud, 8N1**:

```
# Telegram line:
A;<millis>;<rssi_dBm>;<lqi>;<hex_bytes>

# Example:
A;123456;-74;42;0E112A10AABBCCDDEEFF010203

# Noise floor line:
N;<millis>;<rssi_dBm>
N;123460;-92
```

### BidCoS Frame Layout
```
Byte  0:    LEN     – total frame length
Byte  1:    CNT     – message counter
Byte  2:    FLAGS   – WAKEUP, BCAST, BURST, BIDI, RPTED, RPTEN
Byte  3:    TYPE    – message type (DeviceInfo, Set, Get, AckStatus, …)
Bytes 4-6:  SRC     – source device address
Bytes 7-9:  DST     – destination address (000000 = broadcast)
Bytes 10+:  PAYLOAD – type-dependent payload
```

---

## Project Structure

```
app/src/main/java/com/asksin/analyzer/
├── MainActivity.kt              Entry point
├── MainViewModel.kt             State + business logic
├── model/
│   └── Telegram.kt              Data model (Telegram, DeviceStats, NoiseSample)
├── data/
│   └── TelegramParser.kt        Parses sniffer serial lines
├── serial/
│   └── UsbSerialManager.kt      USB OTG connection management
└── ui/
    ├── theme/Theme.kt            Dark industrial colour theme
    ├── components/Components.kt  Reusable UI widgets
    └── screens/
        ├── AppNavigation.kt      Bottom-tab navigation
        ├── MainScreen.kt         Telegram list + connection bar
        ├── TelegramDetailScreen.kt  Full telegram details
        └── DeviceStatsScreen.kt  Per-device duty cycle & RSSI
```

---

## Permissions

The app requests:
- `android.hardware.usb.host` — USB Host / OTG mode
- `USB_PERMISSION` — granted per-device on first connect

No network, storage, or location permissions are required.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| No devices shown | Check OTG adapter; try refreshing the device list |
| Permission denied | Unplug and re-plug; tap "OK" on the permission dialog |
| Garbled data | Verify baud rate is 57600; check the sniffer sketch config |
| CH340 not detected | Some CH340 boards need VID:1A86 PID:7523 — verify `device_filter.xml` |
| App doesn't auto-open | Enable "Open app" in Android USB settings |

---

## License

MIT — based on the open-source AskSinAnalyzerXS project by psi-4ward.
