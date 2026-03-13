package com.asksin.analyzer.data

import com.asksin.analyzer.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

class CcuClient {

    data class FetchResult(
        val devices: Map<String, DeviceInfo> = emptyMap(),
        val error: String? = null
    )

    private data class RfDevice(
        val serial: String,
        val rfAddress: String,  // 6-char hex
        val type: String
    )

    suspend fun fetchDevices(ccuIp: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val rfDevices = fetchRfAddresses(ccuIp)
            val names = fetchDeviceNames(ccuIp)

            if (rfDevices.isEmpty() && names.isEmpty()) {
                return@withContext FetchResult(error = "XML-RPC (port 2001) returned 0 devices and name resolution found 0 names. Check that BidCos-RF is running on the CCU.")
            }
            if (rfDevices.isEmpty()) {
                return@withContext FetchResult(error = "XML-RPC on port 2001 returned 0 RF devices (name resolution found ${names.size} names). Check BidCos-RF interface.")
            }
            if (names.isEmpty()) {
                return@withContext FetchResult(error = "Found ${rfDevices.size} RF devices but name resolution returned 0 names. Check XML-API addon or ReGaHSS (port 8181).")
            }

            val devices = mutableMapOf<String, DeviceInfo>()
            for ((serial, rf) in rfDevices) {
                val name = names[serial] ?: serial
                if (rf.rfAddress.isNotEmpty() && rf.rfAddress != "000000") {
                    devices[rf.rfAddress] = DeviceInfo(
                        address = rf.rfAddress,
                        name = name,
                        serial = serial,
                        type = rf.type
                    )
                }
            }
            FetchResult(devices = devices)
        } catch (e: Exception) {
            FetchResult(error = e.message ?: "Unknown error")
        }
    }

    private fun fetchRfAddresses(ccuIp: String): Map<String, RfDevice> {
        val body = """<?xml version="1.0"?><methodCall><methodName>listDevices</methodName><params></params></methodCall>"""
        val response = httpPost("http://$ccuIp:2001/", body, "text/xml")
        return parseXmlRpcDevices(response)
    }

    /**
     * Fetch device names, trying XML-API addon first (port 80), then ReGaHSS (port 8181).
     */
    private fun fetchDeviceNames(ccuIp: String): Map<String, String> {
        // Try XML-API addon first — more reliable, uses standard HTTP port 80
        try {
            val names = fetchNamesViaXmlApi(ccuIp)
            if (names.isNotEmpty()) return names
        } catch (_: Exception) {}

        // Fall back to ReGaHSS (port 8181)
        return fetchNamesViaRegaHss(ccuIp)
    }

    /**
     * Fetch device names via XML-API addon: GET /addons/xmlapi/devicelist.cgi
     * Returns XML with <device name="..." address="..." interface="BidCos-RF" ...> elements.
     */
    private fun fetchNamesViaXmlApi(ccuIp: String): Map<String, String> {
        val response = httpGet("http://$ccuIp/addons/xmlapi/devicelist.cgi")

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(response))

        val names = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "device") {
                val iface = parser.getAttributeValue(null, "interface") ?: ""
                if (iface == "BidCos-RF") {
                    val address = parser.getAttributeValue(null, "address") ?: ""
                    val name = parser.getAttributeValue(null, "name") ?: ""
                    if (address.isNotEmpty() && name.isNotEmpty()) {
                        names[address] = name
                    }
                }
            }
            event = parser.next()
        }
        return names
    }

    /**
     * Fetch device names via ReGaHSS script on port 8181.
     */
    private fun fetchNamesViaRegaHss(ccuIp: String): Map<String, String> {
        val script = buildString {
            append("string devId;")
            append("foreach(devId,dom.GetObject(ID_DEVICES).EnumUsedIDs()){")
            append("var dev=dom.GetObject(devId);")
            append("var iface=dom.GetObject(dev.Interface());")
            append("if(iface){if(iface.Name()==\"BidCos-RF\"){")
            append("WriteLine(dev.Address()#\"=\"#dev.Name());")
            append("}}")
            append("}")
        }
        val response = httpPost("http://$ccuIp:8181/tclrega.exe", script, "text/plain")

        // ReGaHSS wraps output in <xml><exec>...</exec></xml>
        val content = Regex("<exec>(.*?)</exec>", RegexOption.DOT_MATCHES_ALL)
            .find(response)?.groupValues?.get(1) ?: ""

        val names = mutableMapOf<String, String>()
        for (line in content.lines()) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                val serial = line.substring(0, idx).trim()
                val name = line.substring(idx + 1).trim()
                if (serial.isNotEmpty() && name.isNotEmpty()) {
                    names[serial] = name
                }
            }
        }
        return names
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode !in 200..299) {
                throw Exception("HTTP ${conn.responseCode} from $url")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPost(url: String, body: String, contentType: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", contentType)
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode !in 200..299) {
                throw Exception("HTTP ${conn.responseCode} from $url")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Parse XML-RPC listDevices response.
     * Each device is a <struct> with <member> elements for ADDRESS, RF_ADDRESS, TYPE, etc.
     * We only keep parent devices (ADDRESS without ':' channel suffix).
     */
    private fun parseXmlRpcDevices(xml: String): Map<String, RfDevice> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val devices = mutableMapOf<String, RfDevice>()

        // Track state while parsing nested struct/member elements
        var inStruct = false
        var currentMemberName: String? = null
        var address: String? = null
        var rfAddress: Int? = null
        var type: String? = null
        var structDepth = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "struct" -> {
                            structDepth++
                            if (structDepth == 1) {
                                // Device struct — directly inside <array><data><value>
                                inStruct = true
                                address = null
                                rfAddress = null
                                type = null
                            }
                        }
                        "name" -> {
                            if (inStruct) {
                                currentMemberName = null // will be set on TEXT
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (inStruct && text.isNotEmpty()) {
                        if (currentMemberName == null) {
                            // This is the <name> text
                            currentMemberName = text
                        } else {
                            // This is the value text
                            when (currentMemberName) {
                                "ADDRESS" -> address = text
                                "RF_ADDRESS" -> rfAddress = text.toIntOrNull()
                                "TYPE" -> type = text
                            }
                            currentMemberName = null
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "struct" -> {
                            if (structDepth == 1 && inStruct) {
                                // End of a device struct — save if it's a parent device
                                val addr = address
                                val rf = rfAddress
                                if (addr != null && rf != null && !addr.contains(":")) {
                                    val rfHex = "%06X".format(rf)
                                    devices[addr] = RfDevice(
                                        serial = addr,
                                        rfAddress = rfHex,
                                        type = type ?: ""
                                    )
                                }
                                inStruct = false
                            }
                            structDepth--
                        }
                        "member" -> {
                            currentMemberName = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return devices
    }
}
