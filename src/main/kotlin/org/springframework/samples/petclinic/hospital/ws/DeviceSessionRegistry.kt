package org.springframework.samples.petclinic.hospital.ws

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the live ingest WebSocket per device so the server can push back to it (e.g. an ADT
 * discharge). Each session is wrapped in a [ConcurrentWebSocketSessionDecorator] because two
 * threads write to one socket — the device's ingest ACK and a server-initiated push — and raw
 * sendMessage forbids concurrent/partial writes; a bare monitor lock cannot span the partial-frame
 * window the container owns.
 */
@Component
class DeviceSessionRegistry {

    private val sessions = ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator>()

    fun register(deviceUuid: String, session: WebSocketSession) {
        sessions[deviceUuid] = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT)
    }

    fun unregister(deviceUuid: String, session: WebSocketSession) {
        // Remove only if the entry still wraps THIS raw session, so a fast reconnect's fresh
        // session is not evicted by the old socket's late close.
        val cur = sessions[deviceUuid] ?: return
        if (cur.delegate === session) sessions.remove(deviceUuid, cur)
    }

    /** Best-effort send. A dead/half-open socket is swallowed — the device also infers discharge from rejected ingest. */
    fun send(deviceUuid: String, text: String) {
        val s = sessions[deviceUuid] ?: return
        if (!s.isOpen) return
        try {
            s.sendMessage(TextMessage(text))
        } catch (e: Exception) {
            // best-effort: device will re-sync on reconnect / rejected ingest
        }
    }

    companion object {
        private const val SEND_TIME_LIMIT_MS = 5_000
        private const val BUFFER_SIZE_LIMIT = 512 * 1024
    }
}
