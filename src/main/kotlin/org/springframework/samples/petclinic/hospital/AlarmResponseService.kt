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
        const val METRIC = "HR"
        val SILENCE_WINDOW: Duration = Duration.ofSeconds(120)
    }

    @Transactional
    fun acknowledge(petId: Int, who: String) {
        openAlarm(petId)?.let {
            it.ackedBy = who
            it.ackedAt = Instant.now()
            alarms.save(it) // annotate only; ack never closes the alarm
        }
    }

    @Transactional
    fun silence(petId: Int, who: String) {
        openAlarm(petId)?.let {
            if (it.level == AlarmLevel.EXTREME) return // EXTREME is not silenceable, only acknowledgeable
            it.silencedUntil = Instant.now().plus(SILENCE_WINDOW)
            it.silencedBy = who
            alarms.save(it)
        }
    }

    private fun openAlarm(petId: Int): AlarmEvent? {
        val admission = admissions.findByPetIdAndDischargedAtIsNull(petId) ?: return null
        return admission.id?.let { alarms.findByAdmissionIdAndMetricAndState(it, METRIC, "OPEN") }
    }
}
