package org.springframework.samples.petclinic.hospital

import org.springframework.samples.petclinic.model.BaseEntity
import jakarta.persistence.*
import java.time.Instant

enum class AlarmLevel { LOW, HIGH, EXTREME }

/**
 * An alarm lifecycle row: OPEN on a debounced breach (EXTREME bypasses the debounce), CLOSED on
 * return-to-range. The [ackedBy]/[ackedAt] seats are reserved now (acknowledge logic ships in #4)
 * so the safety table is not migrated later.
 */
@Entity
@Table(name = "alarm_events")
class AlarmEvent : BaseEntity() {

    @Column(name = "admission_id")
    var admissionId: Int? = null

    @Column(name = "metric")
    var metric: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    var level: AlarmLevel = AlarmLevel.HIGH

    @Column(name = "state")
    var state: String = "OPEN"

    @Column(name = "started_at")
    var startedAt: Instant = Instant.now()

    @Column(name = "ended_at")
    var endedAt: Instant? = null

    @Column(name = "trigger_value")
    var triggerValue: Int? = null

    @Column(name = "acked_by")
    var ackedBy: String? = null

    @Column(name = "acked_at")
    var ackedAt: Instant? = null
}
