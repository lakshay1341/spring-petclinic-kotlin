package org.springframework.samples.petclinic.hospital.ws

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Registers the raw device-ingest WebSocket. Raw handler registration does NOT support
 * {uri templates}; we register an Ant pattern and parse path vars inside the handler.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(private val vitalIngestHandler: VitalIngestHandler) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(vitalIngestHandler, "/api/v1/fhir/**").setAllowedOrigins("*")
    }
}
