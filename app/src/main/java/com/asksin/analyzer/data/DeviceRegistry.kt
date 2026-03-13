package com.asksin.analyzer.data

import android.content.Context
import com.asksin.analyzer.model.DeviceInfo
import org.json.JSONObject

class DeviceRegistry(context: Context) {

    private val prefs = context.getSharedPreferences("device_registry", Context.MODE_PRIVATE)

    fun loadAll(): Map<String, DeviceInfo> {
        val json = prefs.getString("devices", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, DeviceInfo>()
            for (key in obj.keys()) {
                val d = obj.getJSONObject(key)
                map[key] = DeviceInfo(
                    address = key,
                    name = d.optString("name", ""),
                    serial = d.optString("serial", ""),
                    type = d.optString("type", ""),
                    manuallyAdded = d.optBoolean("manual", false)
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun save(devices: Map<String, DeviceInfo>) {
        val obj = JSONObject()
        for ((addr, info) in devices) {
            val d = JSONObject()
            d.put("name", info.name)
            d.put("serial", info.serial)
            d.put("type", info.type)
            d.put("manual", info.manuallyAdded)
            obj.put(addr, d)
        }
        prefs.edit().putString("devices", obj.toString()).apply()
    }

    fun upsert(device: DeviceInfo) {
        val map = loadAll().toMutableMap()
        map[device.address] = device
        save(map)
    }

    fun delete(address: String) {
        val map = loadAll().toMutableMap()
        map.remove(address)
        save(map)
    }

    fun clear() {
        prefs.edit().remove("devices").apply()
    }

    fun getCcuIp(): String = prefs.getString("ccu_ip", "") ?: ""

    fun setCcuIp(ip: String) {
        prefs.edit().putString("ccu_ip", ip).apply()
    }
}
