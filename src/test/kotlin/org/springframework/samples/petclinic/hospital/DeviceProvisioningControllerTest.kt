package org.springframework.samples.petclinic.hospital

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

/**
 * Verifies the device provisioning bridge: the QR setup page renders and /oauth/token mints a
 * bearer token only for a caller presenting the configured Basic authToken.
 */
@WebMvcTest(DeviceProvisioningController::class)
@TestPropertySource(properties = ["device.provisioning.auth-token=test-token"])
class DeviceProvisioningControllerTest(@param:Autowired val mvc: MockMvc) {

    @Test
    fun setupPageRenders() {
        mvc.perform(get("/device/setup"))
            .andExpect(status().isOk)
            .andExpect(view().name("device/setup"))
    }

    @Test
    fun tokenIssuedForValidCredential() {
        mvc.perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic test-token")
                .param("accountNumber", "ACC001")
                .param("username", "demo")
                .param("Origin", "petclinic")
                .param("Token", "demo-token")
                .param("password", "demo")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.refresh_token").exists())
    }

    @Test
    fun tokenRefusedForBadCredential() {
        mvc.perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic wrong")
                .param("accountNumber", "ACC001")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun tokenRefusedWhenCredentialMissing() {
        mvc.perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("accountNumber", "ACC001")
        )
            .andExpect(status().isUnauthorized)
    }
}
