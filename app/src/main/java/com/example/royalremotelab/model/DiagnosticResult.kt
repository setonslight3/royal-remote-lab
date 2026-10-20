package com.example.royalremotelab.model

/**
 * Diagnostic health status for candidate protocols.
 */
enum class DiagnosticStatus(val label: String) {
    NOT_TESTED("Not Tested"),
    RESPONDING("Responding"),
    REJECTED("Rejected"),
    TIMED_OUT("Timed Out"),
    UNSUPPORTED("Unsupported"),
    EXPERIMENTAL("Experimental")
}

/**
 * Detail for an individual TCP/UDP port test.
 */
data class PortCheck(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val latencyMs: Long,
    val details: String
)

/**
 * Comprehensive diagnostic result for an investigated protocol.
 */
data class DiagnosticResult(
    val status: DiagnosticStatus,
    val headline: String,
    val summary: String,
    val portChecks: List<PortCheck> = emptyList(),
    val recommendation: String = "",
    val isSimulated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun simulated(protocolName: String): DiagnosticResult {
            return DiagnosticResult(
                status = DiagnosticStatus.RESPONDING,
                headline = "[SIMULATED] $protocolName Diagnostic",
                summary = "All checks simulated offline. No network packets were sent to the host.",
                portChecks = listOf(
                    PortCheck(56789, "Simulated Control Port", true, 5L, "Virtual open socket"),
                    PortCheck(8008, "Simulated DIAL Port", true, 8L, "Virtual HTTP 200 OK")
                ),
                recommendation = "Demo mode active: Exit demo mode in Connection tab to test physical TV.",
                isSimulated = true
            )
        }
    }
}
