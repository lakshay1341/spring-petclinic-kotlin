package org.springframework.samples.petclinic.hospital

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.security.MessageDigest
import java.util.UUID

/**
 * Device provisioning bridge for the Sibel-pattern Android app.
 *
 *  GET  /device/setup  renders a QR the app scans to set its connection settings
 *                      (apiUrl, accountId, oauth creds, Basic authToken).
 *  POST /oauth/token   the app exchanges its Basic authToken for a bearer access token,
 *                      then opens the vital-ingest WebSocket with it.
 *
 * Auth is a single shared secret (device.provisioning.auth-token). The access/refresh
 * tokens are opaque randoms — the WS handshake only checks bearer-presence today.
 * ponytail: mint real signed JWTs the day the WS side verifies token claims/signature.
 */
@Controller
class DeviceProvisioningController(
    @param:Value("\${device.provisioning.auth-token:ZGVtbzpkZW1v}") private val expectedAuthToken: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (expectedAuthToken == DEFAULT_TOKEN) {
            log.warn("Using DEFAULT device.provisioning.auth-token (demo:demo). Override via env for any real deployment.")
        }
    }

    @GetMapping("/device/setup")
    fun setup(model: MutableMap<String, Any>): String {
        // loginUrl auto-filled from this request so the scanned QR points back at this server.
        model["loginUrl"] = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
        model["authToken"] = expectedAuthToken // QR must carry the token /oauth/token expects
        model["accountNumber"] = "ACC001"
        model["username"] = "demo"
        model["password"] = "demo"
        model["origin"] = "petclinic"
        model["bodyToken"] = "demo-token"
        return "device/setup"
    }

    @PostMapping(
        "/oauth/token",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseBody
    fun token(
        @RequestHeader(value = "Authorization", required = false) authorization: String?,
        @RequestParam(required = false) accountNumber: String?
    ): ResponseEntity<TokenResponse> {
        // Trust boundary: only a caller presenting the configured Basic authToken gets a streaming
        // token. Without this gate /oauth/token is an open token-vending machine for the device stream.
        val presented = authorization?.removePrefix("Basic ")?.trim().orEmpty()
        if (presented.isBlank() || !constantTimeEquals(presented, expectedAuthToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        // Never log the password/Token form fields or the credential — account id only.
        log.info("Issued device token for account={}", accountNumber)
        return ResponseEntity.ok(
            TokenResponse(
                access_token = UUID.randomUUID().toString(),
                refresh_token = UUID.randomUUID().toString()
            )
        )
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    companion object {
        private const val DEFAULT_TOKEN = "ZGVtbzpkZW1v"
    }
}

/** OAuth token response. Field names match what the device app parses (access_token / refresh_token). */
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long = 3600
)
