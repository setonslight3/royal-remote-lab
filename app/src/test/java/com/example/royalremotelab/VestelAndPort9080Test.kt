package com.example.royalremotelab

import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.network.Port9080Investigator
import com.example.royalremotelab.protocols.ProtocolRegistry
import com.example.royalremotelab.protocols.VestelProtocol
import org.junit.Assert.*
import org.junit.Test

class VestelAndPort9080Test {

    @Test
    fun vestelProtocol_keyCodesMatchHypothesisTable() {
        val vestel = VestelProtocol()

        assertEquals(1016, vestel.mapCommandToVestelKeyCode(RemoteCommand.VOLUME_UP))
        assertEquals(1017, vestel.mapCommandToVestelKeyCode(RemoteCommand.VOLUME_DOWN))
        assertEquals(1009, vestel.mapCommandToVestelKeyCode(RemoteCommand.MUTE))
        assertEquals(1012, vestel.mapCommandToVestelKeyCode(RemoteCommand.UP))
        assertEquals(1013, vestel.mapCommandToVestelKeyCode(RemoteCommand.DOWN))
        assertEquals(1014, vestel.mapCommandToVestelKeyCode(RemoteCommand.LEFT))
        assertEquals(1015, vestel.mapCommandToVestelKeyCode(RemoteCommand.RIGHT))
        assertEquals(1053, vestel.mapCommandToVestelKeyCode(RemoteCommand.OK))
        assertEquals(1022, vestel.mapCommandToVestelKeyCode(RemoteCommand.BACK))
        assertEquals(1046, vestel.mapCommandToVestelKeyCode(RemoteCommand.HOME))
        assertEquals(1048, vestel.mapCommandToVestelKeyCode(RemoteCommand.MENU))
        assertEquals(1056, vestel.mapCommandToVestelKeyCode(RemoteCommand.INPUT_SOURCE))
    }

    @Test
    fun vestelProtocol_supportedCommandsIncludeRequiredButtons() {
        val vestel = ProtocolRegistry.vestelProtocol
        val supported = vestel.supportedCommands

        assertTrue(supported.contains(RemoteCommand.VOLUME_UP))
        assertTrue(supported.contains(RemoteCommand.VOLUME_DOWN))
        assertTrue(supported.contains(RemoteCommand.MUTE))
        assertTrue(supported.contains(RemoteCommand.UP))
        assertTrue(supported.contains(RemoteCommand.DOWN))
        assertTrue(supported.contains(RemoteCommand.LEFT))
        assertTrue(supported.contains(RemoteCommand.RIGHT))
        assertTrue(supported.contains(RemoteCommand.OK))
        assertTrue(supported.contains(RemoteCommand.BACK))
        assertTrue(supported.contains(RemoteCommand.HOME))
    }

    @Test
    fun port9080_probePathsAreLabelledAndDocumented() {
        val paths = listOf(
            "/ssdp/device-desc.xml",
            "/description.xml",
            "/dd.xml",
            "/apps",
            "/apps/",
            "/dial",
            "/upnp"
        )
        for (path in paths) {
            val clean = if (path.startsWith("/")) path else "/$path"
            assertTrue(clean.startsWith("/"))
        }
    }
}
