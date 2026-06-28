package org.springframework.samples.petclinic.hospital

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * The alarm RESPONSE loop. ACK annotates the open event (who/when) but NEVER closes it — only the
 * AlarmEngine closes an alarm, on return-to-range. SILENCE sets a bounded auto-revoking lease so a
 * silence can never become a permanent mute, and EXTREME is not silenceable at all (only
 * acknowledgeable) — a muteable crash alarm is the affordance wards abuse into disaster.
 */
@Service
class AlarmResponseService(
    val admissions: PetAdmissionRepository,
    val alarms: AlarmEventRepository
) {

    companion object {
        val SILENCE_WINDOW: Duration = Duration.ofSeconds(120)
    }

    @Transactional
    fun acknowledge(petId: Int, who: String) {
        // Ack addresses the PATIENT: stamp every open alarm for the pet (never closes — only the
        // engine closes on return-to-range).
        openAlarms(petId).forEach {
            it.ackedBy = who
            it.ackedAt = Instant.now()
            alarms.save(it)
        }
    }

    @Transactional
    fun silence(petId: Int, who: String) {
        openAlarms(petId).forEach {
            if (it.level == AlarmLevel.EXTREME) return@forEach // EXTREME is not silenceable, only ackable
            it.silencedUntil = Instant.now().plus(SILENCE_WINDOW)
            it.silencedBy = who
            alarms.save(it)
        }
    }

    private fun openAlarms(petId: Int): Collection<AlarmEvent> {
        val admission = admissions.findByPetIdAndDischargedAtIsNull(petId) ?: return emptyList()
        return admission.id?.let { alarms.findByAdmissionIdAndState(it, "OPEN") } ?: emptyList()
    }
}
