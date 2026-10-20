package com.example.royalremotelab.network

import com.example.royalremotelab.model.DiscoveredUpnpDevice
import com.example.royalremotelab.model.DiscoveredUpnpService
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

object DeviceDescriptionParser {

    /**
     * Parses a UPnP / DLNA / DIAL device description XML string safely.
     * Uses standard javax.xml DOM parser for seamless execution across both Android OS
     * and local JVM unit testing environments.
     */
    fun parse(xmlContent: String, locationUrl: String, baseDevice: DiscoveredUpnpDevice? = null): DiscoveredUpnpDevice {
        var friendlyName: String? = null
        var manufacturer: String? = null
        var modelName: String? = null
        var modelNumber: String? = null
        var modelDescription: String? = null
        var deviceType: String? = null
        val services = mutableListOf<DiscoveredUpnpService>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false // Parse UPnP tags directly without strict namespace prefixes
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))

            friendlyName = getFirstElementText(doc, "friendlyName")
            manufacturer = getFirstElementText(doc, "manufacturer")
            modelName = getFirstElementText(doc, "modelName")
            modelNumber = getFirstElementText(doc, "modelNumber")
            modelDescription = getFirstElementText(doc, "modelDescription")
            deviceType = getFirstElementText(doc, "deviceType")

            val serviceNodes = doc.getElementsByTagName("service")
            for (i in 0 until serviceNodes.length) {
                val serviceElem = serviceNodes.item(i) as? Element ?: continue
                val sType = getChildText(serviceElem, "serviceType")
                val sId = getChildText(serviceElem, "serviceId")
                val cUrl = getChildText(serviceElem, "controlURL")
                val eUrl = getChildText(serviceElem, "eventSubURL")
                val sUrl = getChildText(serviceElem, "SCPDURL")

                if (sType.isNotBlank()) {
                    services.add(
                        DiscoveredUpnpService(
                            serviceType = sType,
                            serviceId = sId,
                            controlUrl = resolveUrl(locationUrl, cUrl),
                            eventSubUrl = resolveUrl(locationUrl, eUrl),
                            scpdUrl = resolveUrl(locationUrl, sUrl)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return whatever metadata was successfully extracted
        }

        return (baseDevice ?: DiscoveredUpnpDevice(
            locationUrl = locationUrl,
            serverHeader = "Unknown",
            searchTarget = "Unknown",
            usn = "Unknown",
            targetIp = extractHostFromUrl(locationUrl)
        )).copy(
            friendlyName = friendlyName?.trim(),
            manufacturer = manufacturer?.trim(),
            modelName = modelName?.trim(),
            modelNumber = modelNumber?.trim(),
            modelDescription = modelDescription?.trim(),
            deviceType = deviceType?.trim(),
            services = services,
            rawXml = xmlContent.take(16384)
        )
    }

    private fun getFirstElementText(doc: Document, tagName: String): String? {
        val list = doc.getElementsByTagName(tagName)
        return if (list.length > 0) list.item(0).textContent?.trim() else null
    }

    private fun getChildText(elem: Element, tagName: String): String {
        val list = elem.getElementsByTagName(tagName)
        return if (list.length > 0) list.item(0).textContent?.trim() ?: "" else ""
    }

    /**
     * Resolves a potentially relative UPnP control URL against the location base URL.
     */
    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isBlank()) return baseUrl
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeUrl).toString()
        } catch (_: Exception) {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val cleanBase = baseUrl.substringBeforeLast('/')
                val cleanRel = if (relativeUrl.startsWith("/")) relativeUrl else "/$relativeUrl"
                "$cleanBase$cleanRel"
            }
        }
    }

    private fun extractHostFromUrl(url: String): String {
        return try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }
}
