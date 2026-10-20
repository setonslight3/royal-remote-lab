package com.example.royalremotelab.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Port9080PathResult(
    val label: String,
    val path: String,
    val url: String,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val bodyPreview: String,
    val durationMs: Long,
    val isSuccess: Boolean,
    val applicationUrl: String? = null,
    val discoveredManufacturer: String? = null,
    val discoveredModel: String? = null,
    val discoveredServices: List<String> = emptyList(),
    val errorDetails: String? = null
)

data class Port9080AuditSummary(
    val host: String,
    val rootResult: Port9080PathResult,
    val probeResults: List<Port9080PathResult>,
    val advertisedApplicationUrl: String?,
    val youtubeQueryStatus: String?,
    val technicalVerdict: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Task B: Port 9080 Dedicated Investigator.
 *
 * Grounded in empirical finding: TCP port 9080 returns HTTP 200.
 * Safely inspects root, headers (Application-URL), candidate descriptor paths,
 * and read-only DIAL app state without sending destructive commands or arbitrary payloads.
 */
object Port9080Investigator {

    private val CANDIDATE_PROBE_PATHS = listOf(
        Pair("/ssdp/device-desc.xml", "DIAL / UPnP Root Device Description"),
        Pair("/description.xml", "Standard UPnP Device Description"),
        Pair("/dd.xml", "Compact DIAL Device Description"),
        Pair("/apps", "DIAL REST Application Resource Endpoint"),
        Pair("/apps/", "DIAL REST App Root Directory"),
        Pair("/dial", "DIAL Service Descriptor"),
        Pair("/upnp", "UPnP Base Descriptor")
    )

    /**
     * Executes GET "/" on port 9080 and records response headers, status, and body preview.
     */
    suspend fun probeRoot(host: String, timeoutMs: Int = 3000): Port9080PathResult = withContext(Dispatchers.IO) {
        probeSinglePath(host, "/", "Root Endpoint (GET /)", timeoutMs)
    }

    /**
     * Probes an individual path on port 9080.
     */
    suspend fun probeSinglePath(
        host: String,
        path: String,
        label: String,
        timeoutMs: Int = 3000
    ): Port9080PathResult = withContext(Dispatchers.IO) {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val url = "http://$host:9080$cleanPath"

        val response = NetworkUtils.executeHttpRequest(
            urlString = url,
            method = "GET",
            headers = mapOf(
                "Accept" to "*/*",
                "User-Agent" to "RoyalRemoteLab/1.1 (Android; Port 9080 Audit)"
            ),
            timeoutMs = timeoutMs
        )

        // Extract DIAL Application-URL header if present
        var appUrl = response.getHeader("Application-URL")
        if (appUrl == null) {
            // Also check for case-insensitive variants
            val entry = response.headers.entries.find { it.key.equals("Application-URL", ignoreCase = true) }
            appUrl = entry?.value?.firstOrNull()
        }

        // Parse XML metadata if XML returned
        var manufacturer: String? = null
        var model: String? = null
        val services = mutableListOf<String>()

        if (response.responseBody.contains("<root", ignoreCase = true) ||
            response.responseBody.contains("<device", ignoreCase = true)
        ) {
            try {
                val parsed = DeviceDescriptionParser.parse(response.responseBody, url)
                manufacturer = parsed.manufacturer
                model = "${parsed.modelName ?: ""} ${parsed.modelNumber ?: ""}".trim().ifEmpty { null }
                services.addAll(parsed.services.map { "${it.serviceType.substringAfterLast(':')} (${it.controlUrl})" })
            } catch (_: Exception) {}
        } else if (response.responseBody.contains("<manufacturer", ignoreCase = true)) {
            val match = Regex("""<manufacturer>(.*?)</manufacturer>""", RegexOption.IGNORE_CASE).find(response.responseBody)
            manufacturer = match?.groupValues?.get(1)
        }

        Port9080PathResult(
            label = label,
            path = cleanPath,
            url = url,
            statusCode = response.statusCode,
            headers = response.headers,
            bodyPreview = response.responseBody.take(2048),
            durationMs = response.durationMs,
            isSuccess = response.isSuccess,
            applicationUrl = appUrl?.trim(),
            discoveredManufacturer = manufacturer,
            discoveredModel = model,
            discoveredServices = services,
            errorDetails = if (!response.isSuccess) response.rawException ?: "HTTP ${response.statusCode}" else null
        )
    }

    /**
     * Executes a read-only query against an advertised DIAL Application-URL for an app (e.g. YouTube).
     * Does NOT POST or launch the app.
     */
    suspend fun queryAdvertisedApp(appBaseUrl: String, appName: String = "YouTube", timeoutMs: Int = 3000): String =
        withContext(Dispatchers.IO) {
            val cleanBase = if (appBaseUrl.endsWith("/")) appBaseUrl else "$appBaseUrl/"
            val queryUrl = "$cleanBase$appName"

            try {
                val response = NetworkUtils.executeHttpRequest(
                    urlString = queryUrl,
                    method = "GET",
                    headers = mapOf("Accept" to "*/*"),
                    timeoutMs = timeoutMs
                )
                if (response.isSuccess) {
                    "GET $queryUrl returned HTTP ${response.statusCode} OK in ${response.durationMs}ms: ${response.responseBody.take(512)}"
                } else {
                    "GET $queryUrl returned HTTP ${response.statusCode} (${response.rawException ?: "Not found or app not running"})"
                }
            } catch (e: Exception) {
                "Failed to query $queryUrl: ${e.message}"
            }
        }

    /**
     * Executes a complete, disciplined investigation of port 9080:
     * 1. GET /
     * 2. Candidate labelled paths
     * 3. Read-only query of YouTube if Application-URL is advertised.
     */
    suspend fun runFullInvestigation(
        host: String,
        timeoutMs: Int = 2500,
        onProgress: (String) -> Unit = {}
    ): Port9080AuditSummary = withContext(Dispatchers.IO) {
        onProgress("Checking root endpoint: GET http://$host:9080/ ...")
        val rootResult = probeRoot(host, timeoutMs)

        var foundAppUrl: String? = rootResult.applicationUrl
        val probeResults = mutableListOf<Port9080PathResult>()

        for ((path, label) in CANDIDATE_PROBE_PATHS) {
            onProgress("Checking probe path: $label ($path)...")
            val pResult = probeSinglePath(host, path, label, timeoutMs)
            probeResults.add(pResult)
            if (foundAppUrl == null && pResult.applicationUrl != null) {
                foundAppUrl = pResult.applicationUrl
            }
        }

        // If an Application-URL header was discovered, perform a read-only state query of YouTube
        var youtubeQuery: String? = null
        if (foundAppUrl != null) {
            onProgress("Discovered advertised Application-URL: $foundAppUrl. Querying YouTube app state (read-only)...")
            youtubeQuery = queryAdvertisedApp(foundAppUrl, "YouTube", timeoutMs)
        } else {
            onProgress("No Application-URL header advertised across tested paths on port 9080.")
        }

        // Build technical verdict based solely on empirical evidence
        val successfulPaths = probeResults.filter { it.isSuccess }.map { it.path }
        val verdict = when {
            rootResult.isSuccess && foundAppUrl != null -> {
                "Port 9080 confirmed active (HTTP 200) and advertises DIAL Application-URL: $foundAppUrl. " +
                        "This confirms a DIAL REST server is running on port 9080!"
            }
            rootResult.isSuccess && rootResult.discoveredManufacturer != null -> {
                "Port 9080 confirmed active (HTTP 200). Device description revealed manufacturer: ${rootResult.discoveredManufacturer}."
            }
            rootResult.isSuccess -> {
                "Port 9080 confirmed active (HTTP 200). Responding paths: / (root) ${if (successfulPaths.isNotEmpty()) ", " + successfulPaths.joinToString() else ""}. " +
                        "Inspect response body preview for embedded service signatures."
            }
            else -> {
                "Port 9080 did not return HTTP 200 on / (${rootResult.errorDetails})."
            }
        }

        Port9080AuditSummary(
            host = host,
            rootResult = rootResult,
            probeResults = probeResults,
            advertisedApplicationUrl = foundAppUrl,
            youtubeQueryStatus = youtubeQuery,
            technicalVerdict = verdict
        )
    }
}
