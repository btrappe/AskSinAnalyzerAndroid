package com.asksin.analyzer.model

data class DeviceInfo(
    val address: String,              // 6-char hex RF address "1A2B3C"
    val name: String,                 // human-readable name
    val serial: String = "",          // HomeMatic serial "MEQ0123456"
    val type: String = "",            // device type "HM-Sec-SCo"
    val manuallyAdded: Boolean = false
)
