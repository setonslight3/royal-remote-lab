package com.example.royalremotelab.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * User observation feedback after looking at the physical TV screen.
 */
enum class UserFeedback(val label: String, val badge: String) {
    UNCONFIRMED("Unconfirmed", "❓"),
    VISIBLE_REACTION("TV Reacted Visually", "✅"),
    NO_REACTION("No Visual Reaction", "❌")
}

/**
 * Record of an individual command or diagnostic test.
 */
data class TestLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val protocolId: String,
    val protocolName: String,
    val targetHost: String,
    val actionName: String,
    val result: CommandResult,
    val userFeedback: UserFeedback = UserFeedback.UNCONFIRMED,
    val userNotes: String = ""
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDateTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    /**
     * Formats this log entry as a clean human-readable text line.
     * When [includeIp] is false, the TV IP address is scrubbed for privacy.
     */
    fun toExportString(includeIp: Boolean = false): String {
        val hostDisplay = if (includeIp) targetHost else "[IP_REDACTED]"
        val simTag = if (result.isSimulated) "[DEMO] " else ""
        return "[$formattedDateTime] $simTag$protocolName -> $actionName on $hostDisplay | " +
                "Status: ${result.status.name} (${result.summary}) | " +
                "Physical TV Observation: ${userFeedback.badge} ${userFeedback.label}" +
                if (userNotes.isNotBlank()) " | Notes: $userNotes" else ""
    }
}
