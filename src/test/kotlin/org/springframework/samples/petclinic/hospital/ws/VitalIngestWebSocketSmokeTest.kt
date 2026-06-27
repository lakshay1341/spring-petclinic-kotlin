package org.springframework.samples.petclinic.hospital.ws

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.samples.petclinic.hospital.AdmissionService
import org.springframework.samples.petclinic.hospital.PetAdmissionRepository
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end smoke test against a real embedded server: proves the Ant-pattern handler
 * registration matches, the Bearer handshake survives, an HR Observation is ingested, and
 * the OperationOutcome ACK comes back on the same socket. This is the live verification
 * a unit test cannot give.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VitalIngestWebSocketSmokeTest(
    @param:Autowired val admissions: PetAdmissionRepository,
    @param:Autowired val admissionService: AdmissionService,
    @param:LocalServerPort val port: Int
) {

    @Test
    fun deviceStreamsHrOverWebSocketAndGetsAck() {
        admissionService.admit(1, UUID.randomUUID().toString())
        val admission = admissions.findByPetIdAndDischargedAtIsNull(1)!!
        admission.deviceUuid = "smoke-dev"
        admissions.save(admission)

        val latch = CountDownLatch(1)
        val ack = arrayOfNulls<String>(1)
        val client = StandardWebSocketClient()
        val headers = WebSocketHttpHeaders()
        headers.add("Authorization", "Bearer test")
        val clientHandler = object : TextWebSocketHandler() {
            override fun handleTextMessage(s: WebSocketSession, m: TextMessage) {
                ack[0] = m.payload
                latch.countDown()
            }
        }

        val session = client.execute(
            clientHandler, headers, URI("ws://localhost:$port/api/v1/fhir/acct-1/smoke-dev/store")
        ).get(5, TimeUnit.SECONDS)

        val obs = """{"resourceType":"Observation","id":"smoke-obs","code":{"coding":[{"code":"8867-4"}]},"valueQuantity":{"value":150}}"""
        session.sendMessage(TextMessage(obs))

        assert(latch.await(5, TimeUnit.SECONDS)) { "no ACK received" }
        assert(ack[0]!!.contains("smoke-obs")) { "ACK did not echo observation id" }

        Thread.sleep(500)
        assert(admissions.findByPetIdAndDischargedAtIsNull(1)!!.latestHeartRate == 150) { "HR not ingested" }

        session.close()
    }
}
