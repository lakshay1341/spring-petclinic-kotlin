package org.springframework.samples.petclinic.hospital

import org.springframework.data.repository.Repository

interface AlarmEventRepository : Repository<AlarmEvent, Int> {

    fun save(event: AlarmEvent)

    fun findByAdmissionIdAndMetricAndState(admissionId: Int, metric: String, state: String): AlarmEvent?

    fun findByAdmissionIdAndState(admissionId: Int, state: String): Collection<AlarmEvent>
}
