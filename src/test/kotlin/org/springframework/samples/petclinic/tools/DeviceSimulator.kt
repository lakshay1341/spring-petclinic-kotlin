package org.springframework.samples.petclinic.tools

import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import kotlin.math.sin

/**
 * A standalone device simulator: it does what the real Sibel-pattern app does, against a RUNNING
 * server, so the live dashboard can be validated without physical hardware.
 *
 *   1. admits the pet + binds a device   (POST /pets/{id}/admit)
 *   2. exchanges the demo credential for a bearer token   (POST /oauth/token)
 *   3. opens the ingest WebSocket and streams FHIR Observations: HR / SpO2 / RR / Temp numerics
 *      (which slowly wander into alarm bands) plus an ECG waveform chunk, once a second.
 *
 * Run against a server already started with ./gradlew bootRun:
 *   ./gradlew simulateDevice --args="1 localhost:8080 ACC001 sim-1"
 */
fun main(args: Array<String>) {
    val petId = args.getOrNull(0) ?: "1"
    val host = args.getOrNull(1) ?: "localhost:8080"
    val account = args.getOrNull(2) ?: "ACC001"
    val device = args.getOrNull(3) ?: "sim-$petId"
    val http = HttpClient.newHttpClient()

    fun post(path: String, body: String, auth: String? = null): String {
        val b = HttpRequest.newBuilder(URI("http://$host$path"))
            .header("Content-Type", "application/x-www-form-urlencoded")
        if (auth != null) b.header("Authorization", auth)
        return http.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).body()
    }

    post("/pets/$petId/admit", "ward=SIM&cage=S1&deviceUuid=$device")
    val tokenJson = post(
        "/oauth/token",
        "accountNumber=$account&username=demo&Origin=petclinic&Token=demo-token&password=demo",
        "Basic ZGVtbzpkZW1v"
    )
    val token = Regex("\"access_token\":\"([^\"]+)\"").find(tokenJson)?.groupValues?.get(1)
        ?: error("no access_token in /oauth/token response: $tokenJson")

    val headers = WebSocketHttpHeaders()
    headers.add("Authorization", "Bearer $token")
    val session = StandardWebSocketClient().execute(
        object : TextWebSocketHandler() {
            override fun handleTextMessage(s: WebSocketSession, m: TextMessage) = println("ACK ${m.payload.take(120)}")
        },
        headers,
        URI("ws://$host/api/v1/fhir/$account/$device/store")
    ).get()
    println("Simulator connected for pet $petId via device $device. Streaming — Ctrl-C to stop.")

    fun obs(code: String, value: Int) =
        """{"resourceType":"Observation","id":"${UUID.randomUUID()}","code":{"coding":[{"code":"$code"}]},"valueQuantity":{"value":$value},"effectiveDateTime":"${Instant.now()}"}"""

    var t = 0.0
    while (session.isOpen) {
        val hr = (140 + 90 * sin(t / 7)).toInt()                       // wanders past 180 (dog EXTREME)
        val spo2 = (95 + 8 * sin(t / 11)).toInt().coerceAtMost(100)    // dips below 90 (SpO2 LOW)
        val rr = (25 + 20 * sin(t / 5)).toInt()
        session.sendMessage(TextMessage(obs("8867-4", hr)))
        session.sendMessage(TextMessage(obs("2708-6", spo2)))
        session.sendMessage(TextMessage(obs("9279-1", rr)))
        session.sendMessage(TextMessage(obs("8310-5", 38)))
        // ECG waveform: ~1s of samples (FHIR SampledData, space-separated)
        val data = (0 until 64).joinToString(" ") { ((sin((t * 256 + it) / 4) * 100 + 100).toInt()).toString() }
        session.sendMessage(TextMessage("""{"resourceType":"Observation","id":"${UUID.randomUUID()}","code":{"coding":[{"code":"131143"}]},"component":[{"valueSampledData":{"origin":{"value":0},"period":3.9,"factor":1.0,"data":"$data"}}]}"""))
        t += 1
        Thread.sleep(1000)
    }
}
