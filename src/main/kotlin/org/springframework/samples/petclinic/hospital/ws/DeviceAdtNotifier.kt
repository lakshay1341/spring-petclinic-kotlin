package org.springframework.samples.petclinic.hospital.ws

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Pushes ADT changes to a device over its existing ingest WebSocket, as a FHIR Patient resource —
 * the shape the device app already consumes (active=false => discharged, stop monitoring).
 */
@Component
class DeviceAdtNotifier(
    private val registry: DeviceSessionRegistry,
    private val mapper: ObjectMapper
) {

    fun pushDischarge(deviceUuid: String?, petId: Int?) {
        deviceUuid ?: return
        val patient = mapOf(
            "resourceType" to "Patient",
            "id" to petId?.toString(),
            "active" to false
        )
        registry.send(deviceUuid, mapper.writeValueAsString(patient))
    }
}
