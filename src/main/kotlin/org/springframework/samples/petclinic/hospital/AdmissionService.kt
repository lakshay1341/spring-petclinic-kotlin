package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.hospital.ws.DeviceAdtNotifier
import org.springframework.samples.petclinic.owner.PetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
    val alarmLimits: AlarmLimitRepository,
    val adtNotifier: DeviceAdtNotifier
) {

    @Transactional
    fun admit(
        petId: Int,
        correlationId: String,
        ward: String? = null,
        cage: String? = null,
        deviceUuid: String? = null
    ): PetAdmission? {
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
        // Device/location binding set ONLY on the fresh-admission path — never on an idempotent
        // replay or an already-active pet, which would silently rewrite a live binding.
        admission.ward = ward
        admission.cage = cage
        admission.deviceUuid = deviceUuid
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
        // Tell the streaming device its pet is discharged — only AFTER this commits, never inside
        // the tx (a network write must not ride on a transaction that may still roll back).
        val deviceUuid = active.deviceUuid ?: return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = adtNotifier.pushDischarge(deviceUuid, petId)
            })
        } else {
            adtNotifier.pushDischarge(deviceUuid, petId)
        }
    }

    private fun seedAlarmLimits(admissionId: Int?, petId: Int) {
        val species = pets.findById(petId).type?.name?.lowercase() ?: ""
        // Temp is intentionally NOT seeded: it is ingested + displayed but not alarmed until a real
        // device confirms its unit (degC vs degF) — a wrong band would misfire. Add it then.
        val byMetric = mapOf("HR" to SpeciesLimits.hr(species), "RR" to SpeciesLimits.rr(species), "SpO2" to SpeciesLimits.spo2())
        byMetric.forEach { (metric, t) ->
            val limit = AlarmLimit()
            limit.admissionId = admissionId
            limit.metric = metric
            limit.lowExtreme = t[0]
            limit.low = t[1]
            limit.high = t[2]
            limit.highExtreme = t[3]
            alarmLimits.save(limit)
        }
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
