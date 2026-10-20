package com.example.royalremotelab.model

/**
 * Result of a host reachability audit.
 */
data class HostReachabilityResult(
    val host: String,
    val isIcmpReachable: Boolean,
    val icmpLatencyMs: Long,
    val openFallbackPort: Int?,
    val fallbackLatencyMs: Long,
    val explanation: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * An individual UPnP service discovered within a device description XML.
 */
data class DiscoveredUpnpService(
    val serviceType: String,
    val serviceId: String,
    val controlUrl: String,
    val eventSubUrl: String,
    val scpdUrl: String
)

/**
 * A device discovered via SSDP/UPnP M-SEARCH multicast discovery
 * or direct unicast UPnP probe.
 */
data class DiscoveredUpnpDevice(
    val locationUrl: String,
    val serverHeader: String,
    val searchTarget: String,
    val usn: String,
    val targetIp: String,
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    val modelDescription: String? = null,
    val deviceType: String? = null,
    val services: List<DiscoveredUpnpService> = emptyList(),
    val rawXml: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Confidence level of a protocol or port audit finding.
 */
enum class DiagnosticConfidence(val label: String) {
    HIGH_CONFIDENCE_MATCH("High Confidence Match"),
    INVESTIGATIVE_MATCH("Investigative Match"),
    CONFIRMED_INCOMPATIBLE("Confirmed Incompatible"),
    UNCONFIRMED("Unconfirmed / Pending Evidence")
}

/**
 * Record of a targeted, evidence-backed port test.
 */
data class PortProbeRecord(
    val port: Int,
    val serviceName: String,
    val documentedJustification: String,
    val status: ResultStatus,
    val latencyMs: Long,
    val rawDetails: String,
    val confidence: DiagnosticConfidence
)
