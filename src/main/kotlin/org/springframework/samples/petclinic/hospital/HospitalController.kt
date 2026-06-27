package org.springframework.samples.petclinic.hospital

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * Hospital monitoring HTTP surface. Admit/discharge delegate to AdmissionService (audited ADT);
 * the vitals push/poll stay a thin direct path on the live admission row — vitals are not ADT events.
 */
@Controller
class HospitalController(
    val admissions: PetAdmissionRepository,
    val admissionService: AdmissionService
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
        admissions.findFirstByPetIdOrderByIdDesc(petId)?.let { model["admission"] = it }
        return "hospital/monitor"
    }

    @PostMapping("/hospital/{petId}/vitals")
    fun pushVital(@PathVariable petId: Int, @RequestParam heartRate: Int): String {
        admissions.findByPetIdAndDischargedAtIsNull(petId)?.let {
            it.latestHeartRate = heartRate
            it.lastVitalAt = Instant.now()
            admissions.save(it)
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
