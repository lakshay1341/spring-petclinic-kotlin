package org.springframework.samples.petclinic.hospital

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Test class for [AdmissionService] — the audit-first / idempotent ADT logic.
 */
class AdmissionServiceTest {

    private val admissions = mock(PetAdmissionRepository::class.java)
    private val adtEvents = mock(AdtEventRepository::class.java)
    private val service = AdmissionService(admissions, adtEvents)

    // Kotlin-safe Mockito any(): registers the matcher and returns null typed via type-erasure,
    // so it does not trip Kotlin's non-null parameter check the way ArgumentMatchers.any() does.
    private fun <T> anyObject(): T = Mockito.any()

    @Test
    fun admitCreatesAdmissionAndAuditWhenNoneActive() {
        given(adtEvents.existsByCorrelationId("c1")).willReturn(false)
        given(admissions.findByPetIdAndDischargedAtIsNull(1)).willReturn(null)

        val result = service.admit(1, "c1")

        assertNotNull(result)
        verify(admissions, times(1)).save(anyObject())
        verify(adtEvents, times(1)).save(anyObject())
    }

    @Test
    fun replayedCorrelationIdIsNoOp() {
        given(adtEvents.existsByCorrelationId("c1")).willReturn(true)

        service.admit(1, "c1")

        verify(admissions, never()).save(anyObject())
        verify(adtEvents, never()).save(anyObject())
    }

    @Test
    fun admitOnAlreadyActivePetCreatesNoSecondAdmission() {
        val active = PetAdmission()
        active.petId = 1
        active.id = 5
        given(adtEvents.existsByCorrelationId("c2")).willReturn(false)
        given(admissions.findByPetIdAndDischargedAtIsNull(1)).willReturn(active)

        service.admit(1, "c2")

        verify(admissions, never()).save(anyObject())
        verify(adtEvents, never()).save(anyObject())
    }
}
