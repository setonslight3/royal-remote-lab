package com.example.royalremotelab

import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.protocols.ProtocolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProtocolRegistryAndCommandTest {

    @Test
    fun protocolRegistry_hasAllCandidates() {
        val protocols = ProtocolRegistry.allProtocols
        assertEquals(6, protocols.size)

        val ids = protocols.map { it.id }.toSet()
        assertTrue(ids.contains("demo_mode"))
        assertTrue(ids.contains("dlna_upnp"))
        assertTrue(ids.contains("vestel_smartcenter"))
        assertTrue(ids.contains("dial_novatek"))
        assertTrue(ids.contains("android_tv_remote"))
        assertTrue(ids.contains("targeted_port_probe"))
    }

    @Test
    fun protocolRegistry_defaultSelection() {
        val normalDefault = ProtocolRegistry.getDefault(isDemoMode = false)
        assertEquals("dlna_upnp", normalDefault.id)

        val demoDefault = ProtocolRegistry.getDefault(isDemoMode = true)
        assertEquals("demo_mode", demoDefault.id)
    }

    @Test
    fun dlnaProtocol_commandSupportDistinction() = runTest {
        val dlna = ProtocolRegistry.dlnaProtocol
        val config = ConnectionConfig()

        // DLNA supports volume and media
        assertTrue(dlna.supportedCommands.contains(RemoteCommand.VOLUME_UP))
        assertTrue(dlna.supportedCommands.contains(RemoteCommand.VOLUME_DOWN))
        assertTrue(dlna.supportedCommands.contains(RemoteCommand.MUTE))
        assertTrue(dlna.supportedCommands.contains(RemoteCommand.PLAY))
        assertTrue(dlna.supportedCommands.contains(RemoteCommand.PAUSE))
        assertTrue(dlna.supportedCommands.contains(RemoteCommand.STOP))

        // DLNA explicitly does NOT support D-Pad arrow navigation
        assertFalse(dlna.supportedCommands.contains(RemoteCommand.UP))
        assertFalse(dlna.supportedCommands.contains(RemoteCommand.DOWN))
        assertFalse(dlna.supportedCommands.contains(RemoteCommand.LEFT))
        assertFalse(dlna.supportedCommands.contains(RemoteCommand.RIGHT))
        assertFalse(dlna.supportedCommands.contains(RemoteCommand.OK))

        // Sending unsupported command returns ResultStatus.UNSUPPORTED
        val result = dlna.sendCommand(RemoteCommand.UP, config)
        assertEquals(ResultStatus.UNSUPPORTED, result.status)
        assertTrue(result.summary.contains("not supported"))
    }

    @Test
    fun demoMode_honestSimulationTag() = runTest {
        val demo = ProtocolRegistry.demoProtocol
        val config = ConnectionConfig(isDemoMode = true)

        val result = demo.sendCommand(RemoteCommand.OK, config)
        assertTrue("Demo results must be marked as simulated", result.isSimulated)
        assertTrue("Demo summary must contain [SIMULATED]", result.summary.contains("[SIMULATED]"))

        val diag = demo.runDiagnostics(config)
        assertTrue("Demo diagnostics must be marked as simulated", diag.isSimulated)
        assertTrue("Demo diagnostic headline must contain [SIMULATED]", diag.headline.contains("[SIMULATED]"))
    }
}
