package org.springframework.samples.petclinic.hospital.ws

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.samples.petclinic.hospital.AlarmEngine
import org.springframework.samples.petclinic.hospital.PetAdmissionRepository
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper

/**
 * Raw WebSocket ingest of device vitals. A Sibel-pattern device bridge opens
 * wss://host/api/v1/fhir/{accountId}/{deviceUuid}/store with a Bearer header and streams
 * FHIR R4 Observation frames; the server replies an OperationOutcome ACK matched by id.
 *
 * Singleton handler: holds NO mutable per-connection state in fields. Per-socket data
 * (the bound device uuid) lives in session.attributes; the admission is re-resolved per
 * message so a mid-stream discharge cleanly stops ingest.
 */
@Component
class VitalIngestHandler(
    private val admissions: PetAdmissionRepository,
    private val alarmEngine: AlarmEngine,
    private val mapper: ObjectMapper,
    private val registry: DeviceSessionRegistry,
    @param:Value("\${hospital.ws.idle-timeout-ms:60000}") private val idleTimeoutMs: Long = 60_000
) : TextWebSocketHandler() {

    companion object {
        const val HR_CODE = "8867-4" // LOINC heart rate — the only numeric vital ingested in this slice
        const val ATTR_DEVICE = "deviceUuid"
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // ponytail: bearer-PRESENT check only, NOT a real JWT verify. Upgrade to signature
        // verification keyed on accountId at the first non-localhost / multi-tenant deploy.
        val auth = session.handshakeHeaders.getFirst("Authorization")
        if (auth.isNullOrBlank() || !auth.startsWith("Bearer ")) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        val deviceUuid = deviceUuidFromPath(session)
        if (deviceUuid.isNullOrBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE)
            return
        }
        val admission = resolveAdmission(deviceUuid)
        if (admission?.id == null) {
            // No open admission bound to this device (or two share it) — refuse, never guess a patient.
            session.close(CloseStatus.NOT_ACCEPTABLE)
            return
        }
        session.attributes[ATTR_DEVICE] = deviceUuid
        registry.register(deviceUuid, session)
        // Heartbeat-driven eviction: the device pings every ~10s, so a live socket keeps resetting
        // this idle timer; a dead / half-open socket (kill -9, dropped network) goes silent and the
        // container closes it after the window, firing afterConnectionClosed -> registry.unregister,
        // so the registry never leaks the session. ponytail: leans on client pings + idle close; add
        // a server-side PingMessage scheduler only if a future client never pings.
        (session as? StandardWebSocketSession)?.nativeSession?.maxIdleTimeout = idleTimeoutMs
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // A half-open socket (kill -9 / dropped network) may never fire this on its own, but the
        // per-session idle-timeout (set in afterConnectionEstablished) closes a silent socket and
        // triggers this path, so the registry entry is reclaimed.
        (session.attributes[ATTR_DEVICE] as? String)?.let { registry.unregister(it, session) }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val deviceUuid = session.attributes[ATTR_DEVICE] as? String ?: run {
            session.close(CloseStatus.POLICY_VIOLATION); return
        }
        val node = try {
            mapper.readTree(message.payload)
        } catch (e: Exception) {
            return // non-JSON frame: ignore, gap-tolerant
        }
        val uuid = node.path("id").asString()
        val code = node.path("code").path("coding").path(0).path("code").asString()

        // Only numeric HR Observations feed the alarm engine. Waveforms / unknown codes are
        // ACKed-and-ignored: never error on input we do not model yet.
        if (code != HR_CODE) {
            ack(deviceUuid, uuid, "ignored: non-HR code")
            return
        }
        val value = node.path("valueQuantity").path("value").asInt()

        val admission = resolveAdmission(deviceUuid)
        if (admission == null) {
            ack(deviceUuid, uuid, "error: no open admission for device")
            return
        }

        try {
            alarmEngine.ingest(admission, value, uuid)
            ack(deviceUuid, uuid, "Observation stored")
        } catch (e: DataIntegrityViolationException) {
            // Duplicate observation id (device resend): the whole ingest transaction rolled back,
            // so no sample was double-counted and no alarm state advanced. Idempotent no-op.
            ack(deviceUuid, uuid, "duplicate ignored")
        }
    }

    private fun resolveAdmission(deviceUuid: String) = try {
        admissions.findByDeviceUuidAndDischargedAtIsNull(deviceUuid)
    } catch (e: IncorrectResultSizeDataAccessException) {
        null // two open admissions share one device — refuse rather than pick a patient
    }

    private fun ack(deviceUuid: String, uuid: String, text: String) {
        val outcome = mapOf(
            "resourceType" to "OperationOutcome",
            "id" to uuid,
            "issue" to listOf(mapOf("severity" to "information", "details" to mapOf("text" to text)))
        )
        registry.send(deviceUuid, mapper.writeValueAsString(outcome))
    }

    private fun deviceUuidFromPath(session: WebSocketSession): String? {
        // /api/v1/fhir/{accountId}/{deviceUuid}/store
        val parts = session.uri?.path?.trim('/')?.split('/') ?: return null
        val storeIdx = parts.indexOf("store")
        return if (storeIdx >= 1) parts[storeIdx - 1] else null
    }
}
