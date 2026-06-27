package org.springframework.samples.petclinic.hospital

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Server-side alarm detection. Persists each vital sample and advances a per-(admission,metric)
 * state machine in one transaction. Debounce rules:
 *  - EXTREME breach opens immediately (a 3-6s-late crash alarm can kill).
 *  - HIGH/LOW advisory opens only after 3 consecutive REAL breach samples (rejects jitter).
 *  - Return-to-range closes only after 3 consecutive REAL in-range samples.
 *  - A GAP (null sample) FREEZES state: it never advances toward firing and never clears an
 *    active alarm. Absence of data never moves the patient's apparent state toward safe.
 */
@Component
class AlarmEngine(
    val samples: VitalSampleRepository,
    val limits: AlarmLimitRepository,
    val events: AlarmEventRepository,
    val admissions: PetAdmissionRepository
) {

    companion object {
        const val METRIC = "HR"
        const val DEBOUNCE = 3
    }

    @Transactional
    fun ingest(admission: PetAdmission, value: Int?) {
        val sample = VitalSample()
        sample.admissionId = admission.id
        sample.metric = METRIC
        sample.sampleValue = value
        sample.sampledAt = Instant.now()
        samples.save(sample)

        // GAP: freeze alarm state, do not update the cached vital.
        if (value == null) return

        admission.latestHeartRate = value
        admission.lastVitalAt = Instant.now()
        admissions.save(admission)

        evaluate(admission.id!!, value)
    }

    private fun evaluate(admissionId: Int, value: Int) {
        val limit = limits.findByAdmissionIdAndMetric(admissionId, METRIC) ?: return
        val level = classify(value, limit)
        val open = events.findByAdmissionIdAndMetricAndState(admissionId, METRIC, "OPEN")

        when {
            level == AlarmLevel.EXTREME ->
                if (open == null) open(admissionId, level, value)
            level == AlarmLevel.HIGH || level == AlarmLevel.LOW ->
                if (open == null && recentRealAllBreach(admissionId, limit)) open(admissionId, level!!, value)
            else ->
                if (open != null && recentRealAllInRange(admissionId, limit)) close(open)
        }
    }

    private fun recentReal(admissionId: Int): List<VitalSample> =
        samples.findTop3ByAdmissionIdAndMetricAndSampleValueNotNullOrderBySampledAtDesc(admissionId, METRIC)

    private fun recentRealAllBreach(admissionId: Int, limit: AlarmLimit): Boolean {
        val last = recentReal(admissionId)
        return last.size >= DEBOUNCE && last.take(DEBOUNCE).all { classify(it.sampleValue!!, limit) != null }
    }

    private fun recentRealAllInRange(admissionId: Int, limit: AlarmLimit): Boolean {
        val last = recentReal(admissionId)
        return last.size >= DEBOUNCE && last.take(DEBOUNCE).all { classify(it.sampleValue!!, limit) == null }
    }

    private fun classify(value: Int, limit: AlarmLimit): AlarmLevel? = when {
        value <= limit.lowExtreme || value >= limit.highExtreme -> AlarmLevel.EXTREME
        value < limit.low -> AlarmLevel.LOW
        value > limit.high -> AlarmLevel.HIGH
        else -> null
    }

    private fun open(admissionId: Int, level: AlarmLevel, value: Int) {
        val e = AlarmEvent()
        e.admissionId = admissionId
        e.metric = METRIC
        e.level = level
        e.state = "OPEN"
        e.startedAt = Instant.now()
        e.triggerValue = value
        events.save(e)
    }

    private fun close(open: AlarmEvent) {
        open.state = "CLOSED"
        open.endedAt = Instant.now()
        events.save(open)
    }
}
