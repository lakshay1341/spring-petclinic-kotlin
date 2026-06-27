package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.model.BaseEntity
import jakarta.persistence.*
import java.time.Instant

enum class AdmissionStatus { ADMITTED, TRANSFERRED, DISCHARGED }

/**
 * A pet's hospital admission. Carries the durable ADT state. References Pet by a loose,
 * DB-enforced petId FK. Active while [dischargedAt] is null.
 */
@Entity
@Table(name = "pet_admissions")
class PetAdmission : BaseEntity() {

    @Column(name = "pet_id")
    var petId: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: AdmissionStatus = AdmissionStatus.ADMITTED

    @Column(name = "admitted_at")
    var admittedAt: Instant = Instant.now()

    @Column(name = "discharged_at")
    var dischargedAt: Instant? = null

    @Column(name = "ward")
    var ward: String? = null

    @Column(name = "cage")
    var cage: String? = null

    @Column(name = "device_uuid")
    var deviceUuid: String? = null

    @Column(name = "responsible_vet_id")
    var responsibleVetId: Int? = null

    @Column(name = "latest_heart_rate")
    var latestHeartRate: Int? = null

    @Column(name = "last_vital_at")
    var lastVitalAt: Instant? = null

    val isActive: Boolean
        get() = dischargedAt == null
}
