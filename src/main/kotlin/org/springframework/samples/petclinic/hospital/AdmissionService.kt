package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.owner.PetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Owns ADT state changes. Each method is audit-first within one transaction: the adt_events row
 * (carrying the UNIQUE correlationId fence) and the admission state change commit together, so a
 * replayed correlationId is an idempotent no-op. Admit also seeds species-aware alarm limits from
 * the pet's PetType. Vitals are NOT ADT events and never pass through here.
 */
@Service
class AdmissionService(
    val admissions: PetAdmissionRepository,
    val adtEvents: AdtEventRepository,
    val pets: PetRepository,
    val alarmLimits: AlarmLimitRepository
) {

    @Transactional
    fun admit(petId: Int, correlationId: String): PetAdmission? {
        if (adtEvents.existsByCorrelationId(correlationId)) {
            return admissions.findByPetIdAndDischargedAtIsNull(petId)
        }
        // One active admission per pet (business invariant, distinct from replay dedup).
        val active = admissions.findByPetIdAndDischargedAtIsNull(petId)
        if (active != null) return active

        val admission = PetAdmission()
        admission.petId = petId
        admission.status = AdmissionStatus.ADMITTED
        admission.admittedAt = Instant.now()
        admissions.save(admission)
        audit(correlationId, "ADMIT", petId, admission.id)
        seedAlarmLimits(admission.id, petId)
        return admission
    }

    @Transactional
    fun discharge(petId: Int, correlationId: String) {
        if (adtEvents.existsByCorrelationId(correlationId)) return
        val active = admissions.findByPetIdAndDischargedAtIsNull(petId) ?: return
        active.status = AdmissionStatus.DISCHARGED
        active.dischargedAt = Instant.now()
        admissions.save(active)
        audit(correlationId, "DISCHARGE", petId, active.id)
    }

    private fun seedAlarmLimits(admissionId: Int?, petId: Int) {
        val species = pets.findById(petId).type?.name?.lowercase() ?: ""
        val hr = SpeciesLimits.hr(species)
        val limit = AlarmLimit()
        limit.admissionId = admissionId
        limit.metric = "HR"
        limit.lowExtreme = hr[0]
        limit.low = hr[1]
        limit.high = hr[2]
        limit.highExtreme = hr[3]
        alarmLimits.save(limit)
    }

    private fun audit(correlationId: String, eventType: String, petId: Int, admissionId: Int?) {
        val event = AdtEvent()
        event.correlationId = correlationId
        event.eventType = eventType
        event.petId = petId
        event.admissionId = admissionId
        event.occurredAt = Instant.now()
        adtEvents.save(event)
    }
}
