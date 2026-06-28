package org.springframework.samples.petclinic.hospital

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.samples.petclinic.hospital.ws.WaveformRing
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * One census row for the ward triage screen. The stale/escalated verdicts are computed server-side
 * (against the server clock) — a browser clock must not be able to paint dead data as fresh.
 */
data class CensusRow(
    val petId: Int?,
    val cage: String?,
    val ward: String?,
    val heartRate: Int?,
    val lastVitalAt: String?,
    val ageSeconds: Long?,
    val stale: Boolean,
    val level: String?,
    val state: String?,
    val ackedBy: String?,
    val silencedUntil: String?,
    val escalated: Boolean
)

@Controller
class HospitalController(
    val admissions: PetAdmissionRepository,
    val admissionService: AdmissionService,
    val alarmEngine: AlarmEngine,
    val alarmEvents: AlarmEventRepository,
    val alarmResponse: AlarmResponseService,
    val vitalSamples: VitalSampleRepository,
    val waveformRing: WaveformRing
) {

    companion object {
        const val STALE_SECONDS = 15L
        const val ESCALATE_SECONDS = 30L
    }

    @PostMapping("/pets/{petId}/admit")
    fun admit(@PathVariable petId: Int, @RequestParam(required = false) correlationId: String?): String {
        admissionService.admit(petId, correlationId ?: UUID.randomUUID().toString())
        return "redirect:/hospital/$petId"
    }

    @GetMapping("/hospital")
    fun ward(): String = "hospital/ward"

    @GetMapping("/hospital/census", produces = ["application/json"])
    @ResponseBody
    fun census(): List<CensusRow> {
        val now = Instant.now()
        // ponytail: N+1 open-alarm lookup per active admission; fine at ward scale, batch if a ward grows to hundreds.
        return admissions.findByDischargedAtIsNull().map { a ->
            val alarm = a.id?.let { alarmEvents.findByAdmissionIdAndMetricAndState(it, "HR", "OPEN") }
            val age = a.lastVitalAt?.let { Duration.between(it, now).seconds }
            val stale = age == null || age > STALE_SECONDS
            val escalated = alarm != null && alarm.level == AlarmLevel.EXTREME && alarm.ackedAt == null &&
                Duration.between(alarm.startedAt, now).seconds > ESCALATE_SECONDS
            CensusRow(
                a.petId, a.cage, a.ward, a.latestHeartRate, a.lastVitalAt?.toString(), age, stale,
                alarm?.level?.name, alarm?.state, alarm?.ackedBy, alarm?.silencedUntil?.toString(), escalated
            )
        }.sortedWith(compareBy({ rank(it) }, { it.cage ?: "" }))
    }

    private fun rank(r: CensusRow): Int = when {
        r.level == "EXTREME" && r.escalated -> 0
        r.level == "EXTREME" -> 1
        r.level == "HIGH" || r.level == "LOW" -> 2
        r.stale -> 3
        else -> 4
    }

    @GetMapping("/hospital/{petId}")
    fun monitor(@PathVariable petId: Int, model: MutableMap<String, Any>): String {
        model["petId"] = petId
        admissions.findFirstByPetIdOrderByIdDesc(petId)?.let { admission ->
            model["admission"] = admission
            admission.id?.let { id ->
                alarmEvents.findByAdmissionIdAndMetricAndState(id, "HR", "OPEN")?.let { model["alarm"] = it }
            }
        }
        return "hospital/monitor"
    }

    @PostMapping("/hospital/{petId}/vitals")
    fun pushVital(@PathVariable petId: Int, @RequestParam(required = false) heartRate: Int?): String {
        admissions.findByPetIdAndDischargedAtIsNull(petId)?.let {
            alarmEngine.ingest(it, heartRate)
        }
        return "redirect:/hospital/$petId"
    }

    @GetMapping("/hospital/{petId}/vitals", produces = ["application/json"])
    @ResponseBody
    fun latestVital(@PathVariable petId: Int): Map<String, Any?> {
        val admission = admissions.findByPetIdAndDischargedAtIsNull(petId)
        return mapOf(
            "heartRate" to admission?.latestHeartRate,
            "lastVitalAt" to admission?.lastVitalAt?.toString()
        )
    }

    @GetMapping("/hospital/{petId}/vitals/series", produces = ["application/json"])
    @ResponseBody
    fun vitalSeries(@PathVariable petId: Int): Map<String, Any> {
        val admission = admissions.findByPetIdAndDischargedAtIsNull(petId)
        val recent = admission?.id?.let {
            vitalSamples.findTop240ByAdmissionIdAndMetricOrderBySampledAtDesc(it, "HR")
        } ?: emptyList()
        // Chronological, with null sample_value kept as null so uPlot breaks the line over a GAP
        // instead of drawing a misleading straight segment across missing data.
        val chrono = recent.asReversed()
        return mapOf(
            "t" to chrono.map { it.sampledAt.epochSecond },
            "hr" to chrono.map { it.sampleValue }
        )
    }

    @GetMapping("/hospital/{petId}/waveform", produces = ["application/json"])
    @ResponseBody
    fun waveform(@PathVariable petId: Int): Map<String, Any> {
        val admission = admissions.findByPetIdAndDischargedAtIsNull(petId)
        val snap = admission?.id?.let { waveformRing.snapshot(it) }
        // ageMs lets the UI show NO SIGNAL rather than a frozen trace; -1 = no data at all.
        return mapOf(
            "values" to (snap?.values ?: DoubleArray(0)),
            "period" to (snap?.period ?: 0.0),
            "ageMs" to (snap?.ageMs ?: -1L)
        )
    }

    @PostMapping("/hospital/{petId}/alarm/ack", produces = ["application/json"])
    @ResponseBody
    fun ack(@PathVariable petId: Int): Map<String, Any> {
        alarmResponse.acknowledge(petId, "vet")
        return mapOf("ok" to true)
    }

    @PostMapping("/hospital/{petId}/alarm/silence", produces = ["application/json"])
    @ResponseBody
    fun silence(@PathVariable petId: Int): Map<String, Any> {
        alarmResponse.silence(petId, "vet")
        return mapOf("ok" to true)
    }

    @PostMapping("/pets/{petId}/discharge")
    fun discharge(@PathVariable petId: Int, @RequestParam(required = false) correlationId: String?): String {
        admissionService.discharge(petId, correlationId ?: UUID.randomUUID().toString())
        return "redirect:/hospital/$petId"
    }
}
