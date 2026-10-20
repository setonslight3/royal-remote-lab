package com.example.royalremotelab

import com.example.royalremotelab.network.DeviceDescriptionParser
import com.example.royalremotelab.network.HostReachabilityChecker
import com.example.royalremotelab.network.SsdpDiscoveryManager
import org.junit.Assert.*
import org.junit.Test

class NetworkDiagnosticsTest {

    @Test
    fun parseSsdpResponse_extractsHeadersCorrectly() {
        val rawResponse = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            DATE: Tue, 20 Oct 2026 18:00:00 GMT
            EXT:
            LOCATION: http://192.168.1.38:49152/description.xml
            SERVER: Linux/4.9.118 UPnP/1.0 DLNADOC/1.50
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            USN: uuid:12345678-1234-5678-1234-567812345678::urn:schemas-upnp-org:device:MediaRenderer:1
        """.trimIndent()

        val device = SsdpDiscoveryManager.parseSsdpResponse(rawResponse, "192.168.1.38")
        assertNotNull(device)
        assertEquals("http://192.168.1.38:49152/description.xml", device!!.locationUrl)
        assertEquals("Linux/4.9.118 UPnP/1.0 DLNADOC/1.50", device.serverHeader)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", device.searchTarget)
        assertEquals("uuid:12345678-1234-5678-1234-567812345678::urn:schemas-upnp-org:device:MediaRenderer:1", device.usn)
        assertEquals("192.168.1.38", device.targetIp)
    }

    @Test
    fun parseDeviceDescription_extractsMetadataAndServices() {
        val xml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Smart TV DLNA</friendlyName>
                <manufacturer>Novatek Semiconductor</manufacturer>
                <modelName>RTV65D8100</modelName>
                <modelNumber>NT671LA</modelNumber>
                <modelDescription>65-inch QLED Smart TV DMR</modelDescription>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                    <controlURL>/upnp/control/RenderingControl1</controlURL>
                    <eventSubURL>/upnp/event/RenderingControl1</eventSubURL>
                    <SCPDURL>/upnp/scpd/RenderingControl1.xml</SCPDURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                    <controlURL>/upnp/control/AVTransport1</controlURL>
                    <eventSubURL>/upnp/event/AVTransport1</eventSubURL>
                    <SCPDURL>/upnp/scpd/AVTransport1.xml</SCPDURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val parsed = DeviceDescriptionParser.parse(xml, "http://192.168.1.38:49152/description.xml")

        assertEquals("Smart TV DLNA", parsed.friendlyName)
        assertEquals("Novatek Semiconductor", parsed.manufacturer)
        assertEquals("RTV65D8100", parsed.modelName)
        assertEquals("NT671LA", parsed.modelNumber)
        assertEquals("65-inch QLED Smart TV DMR", parsed.modelDescription)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", parsed.deviceType)

        assertEquals(2, parsed.services.size)
        val renderingControl = parsed.services[0]
        assertEquals("urn:schemas-upnp-org:service:RenderingControl:1", renderingControl.serviceType)
        assertEquals("http://192.168.1.38:49152/upnp/control/RenderingControl1", renderingControl.controlUrl)

        val avTransport = parsed.services[1]
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", avTransport.serviceType)
        assertEquals("http://192.168.1.38:49152/upnp/control/AVTransport1", avTransport.controlUrl)
    }

    @Test
    fun resolveRelativeUrl_correctlyCombinesBaseAndRelative() {
        val resolved = DeviceDescriptionParser.resolveUrl(
            baseUrl = "http://192.168.1.38:8008/ssdp/device-desc.xml",
            relativeUrl = "/apps/YouTube"
        )
        assertEquals("http://192.168.1.38:8008/apps/YouTube", resolved)
    }

    @Test
    fun evidenceBackedPorts_allHaveJustifications() {
        val ports = HostReachabilityChecker.EVIDENCE_BACKED_PORTS
        assertTrue(ports.isNotEmpty())
        for (portDef in ports) {
            assertTrue("Port ${portDef.port} must have a non-blank justification", portDef.justification.isNotBlank())
            assertTrue("Port ${portDef.port} must have a service name", portDef.serviceName.isNotBlank())
        }
        val portNumbers = ports.map { it.port }.toSet()
        assertTrue(portNumbers.contains(8008)) // DIAL
        assertTrue(portNumbers.contains(49152)) // Dynamic UPnP
        assertTrue(portNumbers.contains(56789)) // Vestel clue
        assertTrue(portNumbers.contains(6466))  // Google TV Remote
    }
}
