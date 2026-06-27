package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.model.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Append-only ADT audit row. [correlationId] is UNIQUE and is the sole idempotency fence:
 * a replayed event hits the unique constraint and is treated as a no-op.
 */
@Entity
@Table(name = "adt_events")
class AdtEvent : BaseEntity() {

    @Column(name = "correlation_id")
    var correlationId: String? = null

    @Column(name = "admission_id")
    var admissionId: Int? = null

    @Column(name = "event_type")
    var eventType: String? = null

    @Column(name = "pet_id")
    var petId: Int? = null

    @Column(name = "occurred_at")
    var occurredAt: Instant = Instant.now()
}
