package org.springframework.samples.petclinic.hospital

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Safety invariants for [AlarmResponseService]: ack never closes; silence is an advisory-only lease.
 */
class AlarmResponseServiceTest {

    private val admissions = mock(PetAdmissionRepository::class.java)
    private val alarms = mock(AlarmEventRepository::class.java)
    private val service = AlarmResponseService(admissions, alarms)

    private fun <T> anyObject(): T = Mockito.any()

    private fun openAlarm(level: AlarmLevel): AlarmEvent =
        AlarmEvent().apply { admissionId = 1; metric = "HR"; this.level = level; state = "OPEN" }

    @BeforeEach
    fun setup() {
        given(admissions.findByPetIdAndDischargedAtIsNull(1))
            .willReturn(PetAdmission().apply { id = 1; petId = 1 })
    }

    @Test
    fun ackStampsAckedByAndDoesNotClose() {
        val a = openAlarm(AlarmLevel.HIGH)
        given(alarms.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(a)

        service.acknowledge(1, "drwho")

        assertEquals("drwho", a.ackedBy)
        assertNotNull(a.ackedAt)
        assertEquals("OPEN", a.state) // ack must NOT close the alarm
        assertNull(a.endedAt)
        verify(alarms).save(a)
    }

    @Test
    fun silenceSetsLeaseForAdvisory() {
        val a = openAlarm(AlarmLevel.LOW)
        given(alarms.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(a)

        service.silence(1, "drwho")

        assertNotNull(a.silencedUntil)
        assertEquals("drwho", a.silencedBy)
        verify(alarms).save(a)
    }

    @Test
    fun silenceRefusedForExtreme() {
        val a = openAlarm(AlarmLevel.EXTREME)
        given(alarms.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(a)

        service.silence(1, "drwho")

        assertNull(a.silencedUntil) // EXTREME is not silenceable
        verify(alarms, never()).save(anyObject())
    }
}
