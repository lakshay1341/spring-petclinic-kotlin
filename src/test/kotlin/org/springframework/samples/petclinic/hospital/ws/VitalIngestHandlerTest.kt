package org.springframework.samples.petclinic.hospital.ws

import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.samples.petclinic.hospital.AlarmEngine
import org.springframework.samples.petclinic.hospital.PetAdmission
import org.springframework.samples.petclinic.hospital.PetAdmissionRepository
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper

/**
 * Unit tests for [VitalIngestHandler]: HR ingest + ACK, non-HR ignore, duplicate-resend tolerance.
 * No real socket, no Spring context — the handler is constructed directly with mocked deps.
 */
class VitalIngestHandlerTest {

    private val admissions = mock(PetAdmissionRepository::class.java)
    private val alarmEngine = mock(AlarmEngine::class.java)
    private val mapper = JsonMapper.builder().build()
    private val registry = DeviceSessionRegistry()
    private val waveformRing = WaveformRing()
    private val handler = VitalIngestHandler(admissions, alarmEngine, mapper, registry, waveformRing)

    private fun <T> anyObject(): T = Mockito.any()

    private fun session(): WebSocketSession {
        val s = mock(WebSocketSession::class.java)
        given(s.attributes).willReturn(mutableMapOf<String, Any>("deviceUuid" to "dev-1"))
        given(s.isOpen).willReturn(true)
        given(s.id).willReturn("test-session")
        registry.register("dev-1", s) // ACK now goes out through the registry's per-device session
        return s
    }

    @Test
    fun ingestsHrObservationAndAcksWithSameUuid() {
        val admission = PetAdmission().apply { id = 1; petId = 7; deviceUuid = "dev-1" }
        given(admissions.findByDeviceUuidAndDischargedAtIsNull("dev-1")).willReturn(admission)
        val s = session()
        val json = """{"resourceType":"Observation","id":"obs-9","code":{"coding":[{"code":"8867-4"}]},"valueQuantity":{"value":142}}"""

        handler.handleTextMessage(s, TextMessage(json))

        verify(alarmEngine, times(1)).ingest(anyObject(), eq(142), eq("obs-9"))
        val cap = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(s).sendMessage(cap.capture())
        assert(cap.value.payload.contains("obs-9"))
        assert(cap.value.payload.contains("OperationOutcome"))
    }

    @Test
    fun nonHrCodeIsIgnoredNotIngested() {
        val s = session()
        val json = """{"resourceType":"Observation","id":"obs-2","code":{"coding":[{"code":"9279-1"}]},"valueQuantity":{"value":20}}"""

        handler.handleTextMessage(s, TextMessage(json))

        verify(alarmEngine, never()).ingest(anyObject(), anyObject(), anyObject())
        verify(s).sendMessage(anyObject())
    }

    @Test
    fun duplicateObservationIsAckedNotCrashing() {
        val admission = PetAdmission().apply { id = 1; petId = 7; deviceUuid = "dev-1" }
        given(admissions.findByDeviceUuidAndDischargedAtIsNull("dev-1")).willReturn(admission)
        willThrow(DataIntegrityViolationException("dup"))
            .given(alarmEngine).ingest(anyObject(), eq(142), eq("dup-1"))
        val s = session()
        val json = """{"resourceType":"Observation","id":"dup-1","code":{"coding":[{"code":"8867-4"}]},"valueQuantity":{"value":142}}"""

        handler.handleTextMessage(s, TextMessage(json))

        verify(s).sendMessage(anyObject())
    }

    @Test
    fun waveformFrameIsBufferedForDisplay() {
        val admission = PetAdmission().apply { id = 1; petId = 7; deviceUuid = "dev-1" }
        given(admissions.findByDeviceUuidAndDischargedAtIsNull("dev-1")).willReturn(admission)
        val s = session()
        val json = """{"resourceType":"Observation","id":"w1","code":{"coding":[{"code":"131143"}]},"component":[{"valueSampledData":{"origin":{"value":0},"period":3.9,"factor":1.0,"data":"10 20 30"}}]}"""

        handler.handleTextMessage(s, TextMessage(json))

        val snap = waveformRing.snapshot(1)!!
        assert(snap.values.toList() == listOf(10.0, 20.0, 30.0))
        verify(s).sendMessage(anyObject()) // still ACKed
    }
}
