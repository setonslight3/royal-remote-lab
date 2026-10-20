package com.example.royalremotelab.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.royalremotelab.model.DiscoveredUpnpDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

object SsdpDiscoveryManager {

    private const val SSDP_MULTICAST_IP = "239.255.255.250"
    private const val SSDP_PORT = 1900

    /**
     * Executes bounded SSDP/UPnP M-SEARCH discovery.
     * Searches for devices advertising on the local network within [timeoutMs].
     * If [filterHost] is provided, prioritizes or highlights responses from that IP.
     */
    suspend fun discoverDevices(
        context: Context?,
        filterHost: String? = null,
        timeoutMs: Long = 4000L,
        onLog: (String) -> Unit = {}
    ): List<DiscoveredUpnpDevice> = withContext(Dispatchers.IO) {
        val discoveredMap = mutableMapOf<String, DiscoveredUpnpDevice>()
        var multicastLock: WifiManager.MulticastLock? = null

        // 1. Acquire Android MulticastLock if running within an Android context
        if (context != null) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("royal_remote_lab_ssdp")
                multicastLock?.setReferenceCounted(true)
                multicastLock?.acquire()
                onLog("Acquired Android Wi-Fi MulticastLock for UDP multicast reception.")
            } catch (e: Exception) {
                onLog("Note: Could not acquire MulticastLock (${e.message}). Proceeding with standard socket.")
            }
        }

        var socket: DatagramSocket? = null
        try {
            // Use MulticastSocket bound to ephemeral port
            val mSocket = MulticastSocket(null)
            mSocket.reuseAddress = true
            mSocket.bind(InetSocketAddress(0))
            mSocket.soTimeout = 1000
            socket = mSocket

            val group = InetAddress.getByName(SSDP_MULTICAST_IP)
            val searchTargets = listOf(
                "ssdp:all",
                "upnp:rootdevice",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:dial-multiscreen-org:service:dial:1"
            )

            // 2. Broadcast M-SEARCH packets
            for (st in searchTargets) {
                val mSearchPayload = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $SSDP_MULTICAST_IP:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: $st\r\n" +
                        "\r\n"

                val bytes = mSearchPayload.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(bytes, bytes.size, group, SSDP_PORT)
                mSocket.send(packet)
                onLog("Dispatched SSDP M-SEARCH for target: $st")
            }

            // 3. Also send direct unicast M-SEARCH to targetHost:1900 if provided
            // (Works around router Wi-Fi multicast isolation / AP-isolation!)
            if (!filterHost.isNullOrBlank()) {
                try {
                    val targetInet = InetAddress.getByName(filterHost.trim())
                    val unicastPayload = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: $filterHost:$SSDP_PORT\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: ssdp:all\r\n" +
                            "\r\n"
                    val uBytes = unicastPayload.toByteArray(Charsets.UTF_8)
                    val uPacket = DatagramPacket(uBytes, uBytes.size, targetInet, SSDP_PORT)
                    socket.send(uPacket)
                    onLog("Dispatched direct unicast M-SEARCH to $filterHost:$SSDP_PORT (bypass AP-isolation).")
                } catch (e: Exception) {
                    onLog("Direct unicast M-SEARCH to $filterHost failed: ${e.message}")
                }
            }

            // 4. Listen for incoming M-SEARCH responses within bounded window
            val startTime = System.currentTimeMillis()
            val rxBuffer = ByteArray(8192)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                    socket.receive(rxPacket)

                    val responseText = String(rxPacket.data, 0, rxPacket.length, Charsets.UTF_8)
                    val senderIp = rxPacket.address.hostAddress ?: "Unknown"

                    val parsedDevice = parseSsdpResponse(responseText, senderIp)
                    if (parsedDevice != null) {
                        val key = "${parsedDevice.targetIp}:${parsedDevice.locationUrl}"
                        if (!discoveredMap.containsKey(key)) {
                            discoveredMap[key] = parsedDevice
                            onLog("Discovered device at $senderIp: LOCATION=${parsedDevice.locationUrl}")
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Continue listening until deadline
                }
            }
        } catch (e: Exception) {
            onLog("SSDP Discovery error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                    onLog("Released Wi-Fi MulticastLock.")
                }
            } catch (_: Exception) {}
        }

        // 5. If no SSDP response was received from filterHost, try bounded direct HTTP probe
        // of candidate description URLs (e.g. DIAL port 8008 or UPnP port 49152)
        if (!filterHost.isNullOrBlank() && discoveredMap.none { it.value.targetIp == filterHost.trim() }) {
            onLog("No SSDP multicast response from $filterHost. Probing candidate description URLs...")
            val directUrls = listOf(
                "http://$filterHost:8008/ssdp/device-desc.xml",
                "http://$filterHost:49152/description.xml",
                "http://$filterHost:49153/description.xml",
                "http://$filterHost:7676/smp_2_"
            )
            for (url in directUrls) {
                try {
                    val httpRes = NetworkUtils.executeHttpRequest(url, method = "GET", timeoutMs = 1200)
                    if (httpRes.isSuccess && httpRes.responseBody.contains("<root") || httpRes.responseBody.contains("<device")) {
                        onLog("Direct probe found device description at $url (HTTP ${httpRes.statusCode})")
                        val dev = DiscoveredUpnpDevice(
                            locationUrl = url,
                            serverHeader = "Direct HTTP Probe",
                            searchTarget = "upnp:rootdevice",
                            usn = "direct:$filterHost",
                            targetIp = filterHost
                        )
                        discoveredMap["$filterHost:$url"] = dev
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        discoveredMap.values.toList()
    }

    /**
     * Parses an SSDP raw HTTP-like notification/response packet.
     */
    fun parseSsdpResponse(rawText: String, senderIp: String): DiscoveredUpnpDevice? {
        var location: String? = null
        var server: String? = null
        var st: String? = null
        var usn: String? = null

        val reader = BufferedReader(StringReader(rawText))
        var line: String? = reader.readLine()
        while (line != null) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val headerName = line.substring(0, colonIdx).trim().uppercase()
                val headerVal = line.substring(colonIdx + 1).trim()
                when (headerName) {
                    "LOCATION" -> location = headerVal
                    "SERVER" -> server = headerVal
                    "ST" -> st = headerVal
                    "USN" -> usn = headerVal
                }
            }
            line = reader.readLine()
        }

        return if (!location.isNullOrBlank()) {
            DiscoveredUpnpDevice(
                locationUrl = location,
                serverHeader = server ?: "Unknown",
                searchTarget = st ?: "Unknown",
                usn = usn ?: "Unknown",
                targetIp = senderIp
            )
        } else {
            null
        }
    }
}
