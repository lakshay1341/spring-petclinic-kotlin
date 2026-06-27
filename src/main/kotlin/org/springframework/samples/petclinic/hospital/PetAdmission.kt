package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.model.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * A pet's hospital admission. Increment 1 proves the admit -> vital -> discharge thread.
 * Active while [dischargedAt] is null. References Pet by a loose, DB-enforced petId FK.
 */
@Entity
@Table(name = "pet_admissions")
class PetAdmission : BaseEntity() {

    @Column(name = "pet_id")
    var petId: Int? = null

    @Column(name = "admitted_at")
    var admittedAt: Instant = Instant.now()

    @Column(name = "discharged_at")
    var dischargedAt: Instant? = null

    @Column(name = "latest_heart_rate")
    var latestHeartRate: Int? = null

    @Column(name = "last_vital_at")
    var lastVitalAt: Instant? = null

    val isActive: Boolean
        get() = dischargedAt == null
}
