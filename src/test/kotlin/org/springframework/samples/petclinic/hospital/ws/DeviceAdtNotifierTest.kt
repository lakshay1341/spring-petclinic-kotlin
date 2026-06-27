package org.springframework.samples.petclinic.hospital.ws

import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper

/**
 * Verifies the server->device ADT push: a discharge emits a FHIR Patient{active:false} to the
 * connected device, and is a safe no-op when the device has no live session.
 */
class DeviceAdtNotifierTest {

    private val registry = DeviceSessionRegistry()
    private val mapper = JsonMapper.builder().build()
    private val notifier = DeviceAdtNotifier(registry, mapper)

    @Test
    fun pushesPatientInactiveToConnectedDevice() {
        val s = mock(WebSocketSession::class.java)
        given(s.isOpen).willReturn(true)
        given(s.id).willReturn("test-session")
        registry.register("dev-1", s)

        notifier.pushDischarge("dev-1", 7)

        val cap = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(s).sendMessage(cap.capture())
        val p = cap.value.payload
        assert(p.contains("\"resourceType\":\"Patient\""))
        assert(p.contains("\"active\":false"))
        assert(p.contains("\"id\":\"7\""))
    }

    @Test
    fun noOpWhenDeviceNotConnected() {
        notifier.pushDischarge("absent-device", 7) // must not throw
    }
}
