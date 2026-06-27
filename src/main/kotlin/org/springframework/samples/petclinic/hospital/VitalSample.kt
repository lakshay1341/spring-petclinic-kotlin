package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.model.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * One numeric vital reading for an admission. A null [sampleValue] is an explicit GAP marker
 * (sensor off/detached) — never interpolated. The durable series is what the alarm debounce
 * is computed over; a single mutable column cannot express a 3-sample debounce.
 */
@Entity
@Table(name = "vital_samples")
class VitalSample : BaseEntity() {

    @Column(name = "admission_id")
    var admissionId: Int? = null

    @Column(name = "metric")
    var metric: String? = null

    @Column(name = "sample_value")
    var sampleValue: Int? = null

    @Column(name = "sampled_at")
    var sampledAt: Instant = Instant.now()
}
