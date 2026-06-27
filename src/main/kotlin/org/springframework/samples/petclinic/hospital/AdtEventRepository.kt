package org.springframework.samples.petclinic.hospital

import org.springframework.data.repository.Repository

interface AdtEventRepository : Repository<AdtEvent, Int> {

    fun save(event: AdtEvent)

    fun existsByCorrelationId(correlationId: String): Boolean
}
