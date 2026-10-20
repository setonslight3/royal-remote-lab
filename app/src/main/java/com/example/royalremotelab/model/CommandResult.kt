package com.example.royalremotelab.model

/**
 * High-level categorization of the outcome of a TV command or network transaction.
 */
enum class ResultStatus(val label: String) {
    SUCCESS("Success"),
    NETWORK_RESPONSE("Network Response"),
    REJECTED("Connection Refused / Rejected"),
    TIMED_OUT("Timed Out"),
    UNSUPPORTED("Unsupported by Protocol"),
    ERROR("Error")
}

/**
 * Result data class describing what happened when a command was sent.
 * Note: A status of SUCCESS or NETWORK_RESPONSE only indicates network acknowledgment;
 * actual TV screen reaction requires human physical observation.
 */
data class CommandResult(
    val status: ResultStatus,
    val summary: String,
    val details: String = "",
    val isSimulated: Boolean = false,
    val latencyMs: Long = 0L
) {
    companion object {
        fun simulated(command: RemoteCommand): CommandResult {
            return CommandResult(
                status = ResultStatus.SUCCESS,
                summary = "[SIMULATED] ${command.label} acknowledged in Demo Mode",
                details = "Simulated offline execution. No actual network packets were transmitted.",
                isSimulated = true,
                latencyMs = 12L
            )
        }

        fun unsupported(protocolName: String, command: RemoteCommand): CommandResult {
            return CommandResult(
                status = ResultStatus.UNSUPPORTED,
                summary = "${command.label} is not supported by $protocolName",
                details = "$protocolName does not provide a standard mechanism for ${command.description.lowercase()}.",
                isSimulated = false,
                latencyMs = 0L
            )
        }
    }
}
