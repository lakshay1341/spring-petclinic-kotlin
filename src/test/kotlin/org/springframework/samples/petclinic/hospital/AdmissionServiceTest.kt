package org.springframework.samples.petclinic.hospital

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.samples.petclinic.owner.Pet
import org.springframework.samples.petclinic.owner.PetRepository
import org.springframework.samples.petclinic.owner.PetType

/**
 * Test class for [AdmissionService] — the audit-first / idempotent ADT logic.
 */
class AdmissionServiceTest {

    private val admissions = mock(PetAdmissionRepository::class.java)
    private val adtEvents = mock(AdtEventRepository::class.java)
    private val pets = mock(PetRepository::class.java)
    private val alarmLimits = mock(AlarmLimitRepository::class.java)
    private val service = AdmissionService(admissions, adtEvents, pets, alarmLimits)

    private fun <T> anyObject(): T = Mockito.any()

    private fun dogPet(): Pet {
        val type = PetType()
        type.name = "dog"
        val pet = Pet()
        pet.type = type
        return pet
    }

    @Test
    fun admitCreatesAdmissionAndAuditWhenNoneActive() {
        given(adtEvents.existsByCorrelationId("c1")).willReturn(false)
        given(admissions.findByPetIdAndDischargedAtIsNull(1)).willReturn(null)
        given(pets.findById(1)).willReturn(dogPet())

        val result = service.admit(1, "c1")

        assertNotNull(result)
        verify(admissions, times(1)).save(anyObject())
        verify(adtEvents, times(1)).save(anyObject())
        verify(alarmLimits, times(1)).save(anyObject())
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
