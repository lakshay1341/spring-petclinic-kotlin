package org.springframework.samples.petclinic.hospital

import org.springframework.data.repository.Repository

interface AlarmLimitRepository : Repository<AlarmLimit, Int> {

    fun save(limit: AlarmLimit)

    fun findByAdmissionIdAndMetric(admissionId: Int, metric: String): AlarmLimit?
}
