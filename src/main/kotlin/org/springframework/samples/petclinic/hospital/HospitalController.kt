package org.springframework.samples.petclinic.hospital

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * Increment 1 hospital monitoring thread: admit a pet, push one numeric vital (HR) over plain
 * HTTP, poll it on a page, discharge. No device app, FHIR, WebSocket or alarms yet.
 */
@Controller
class HospitalController(val admissions: PetAdmissionRepository) {

    @PostMapping("/pets/{petId}/admit")
    fun admit(@PathVariable petId: Int): String {
        if (admissions.findByPetIdAndDischargedAtIsNull(petId) == null) {
            val admission = PetAdmission()
            admission.petId = petId
            admission.admittedAt = Instant.now()
            admissions.save(admission)
        }
        return "redirect:/hospital/$petId"
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
    fun discharge(@PathVariable petId: Int): String {
        admissions.findByPetIdAndDischargedAtIsNull(petId)?.let {
            it.dischargedAt = Instant.now()
            admissions.save(it)
        }
        return "redirect:/hospital/$petId"
    }
}
