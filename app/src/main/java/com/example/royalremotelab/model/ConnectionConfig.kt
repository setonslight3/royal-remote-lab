package com.example.royalremotelab.model

/**
 * Configuration for connecting to the smart TV on the local network.
 */
data class ConnectionConfig(
    val host: String = "192.168.1.38",
    val timeoutMs: Int = 3000,
    val isDemoMode: Boolean = false,
    val customPort: Int? = null
) {
    /**
     * Validates that the host is a plausible IPv4 address or hostname.
     * Returns null if valid, or a descriptive error message if invalid.
     */
    fun validate(): String? {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) {
            return "TV IP address or hostname cannot be empty."
        }
        val ipv4Regex = Regex("""^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$""")
        val hostnameRegex = Regex("""^([a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,6}$|^[a-zA-Z0-9\-]+$""")
        if (!ipv4Regex.matches(trimmed) && !hostnameRegex.matches(trimmed)) {
            return "Please enter a valid IPv4 address (e.g., 192.168.1.38) or local hostname."
        }
        if (timeoutMs < 500 || timeoutMs > 30000) {
            return "Timeout must be between 500 ms and 30,000 ms."
        }
        return null
    }

    val isValid: Boolean get() = validate() == null
}
