package com.example.royalremotelab.protocols

/**
 * Central registry of smart TV protocol adapters available in Royal Remote Lab.
 */
object ProtocolRegistry {

    val demoProtocol = DemoProtocol()
    val dlnaProtocol = DlnaProtocol()
    val vestelProtocol = VestelProtocol()
    val dialProtocol = DialNovatekProtocol()
    val androidTvProtocol = AndroidTvRemoteProtocol()
    val portAuditProtocol = RawTcpProbeProtocol()

    val allProtocols: List<TvProtocol> = listOf(
        demoProtocol,
        dlnaProtocol,
        vestelProtocol,
        dialProtocol,
        androidTvProtocol,
        portAuditProtocol
    )

    fun getById(id: String): TvProtocol {
        return allProtocols.find { it.id == id } ?: dlnaProtocol
    }

    fun getDefault(isDemoMode: Boolean): TvProtocol {
        return if (isDemoMode) demoProtocol else dlnaProtocol
    }
}
