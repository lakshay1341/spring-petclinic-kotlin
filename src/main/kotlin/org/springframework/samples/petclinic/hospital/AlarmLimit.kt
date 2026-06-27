package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.model.BaseEntity
import jakarta.persistence.*

/**
 * Per-admission alarm thresholds for one metric, seeded from species (PetType) at admit time.
 * Four thresholds give LOW/HIGH advisory bands plus EXTREME (peri-arrest) bands.
 */
@Entity
@Table(name = "alarm_limits")
class AlarmLimit : BaseEntity() {

    @Column(name = "admission_id")
    var admissionId: Int? = null

    @Column(name = "metric")
    var metric: String? = null

    @Column(name = "low_extreme")
    var lowExtreme: Int = 0

    @Column(name = "low")
    var low: Int = 0

    @Column(name = "high")
    var high: Int = 0

    @Column(name = "high_extreme")
    var highExtreme: Int = 0
}
