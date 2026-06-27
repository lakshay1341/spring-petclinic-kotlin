package org.springframework.samples.petclinic.hospital

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Hospital monitoring HTTP surface. Admit/discharge delegate to AdmissionService (audited ADT).
 * Vitals push goes through AlarmEngine.ingest (persist sample + evaluate alarm in one transaction);
 * a missing heartRate is an explicit GAP. The vitals path stays off the ADT audit machinery.
 */
@Controller
class HospitalController(
    val admissions: PetAdmissionRepository,
    val admissionService: AdmissionService,
    val alarmEngine: AlarmEngine,
    val alarmEvents: AlarmEventRepository
) {

    @PostMapping("/pets/{petId}/admit")
    fun admit(@PathVariable petId: Int, @RequestParam(required = false) correlationId: String?): String {
        admissionService.admit(petId, correlationId ?: UUID.randomUUID().toString())
        return "redirect:/hospital/$petId"
    }

    @GetMapping("/hospital")
    fun ward(model: MutableMap<String, Any>): String {
        model["admissions"] = admissions.findByDischargedAtIsNull()
        return "hospital/ward"
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

    @PostMapping("/pets/{petId}/discharge")
    fun discharge(@PathVariable petId: Int, @RequestParam(required = false) correlationId: String?): String {
        admissionService.discharge(petId, correlationId ?: UUID.randomUUID().toString())
        return "redirect:/hospital/$petId"
    }
}
