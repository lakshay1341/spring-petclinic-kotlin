package org.springframework.samples.petclinic.hospital

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Test class for the [HospitalController]
 */
@ExtendWith(SpringExtension::class)
@WebMvcTest(HospitalController::class)
class HospitalControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var admissions: PetAdmissionRepository

    @MockitoBean
    private lateinit var admissionService: AdmissionService

    @MockitoBean
    private lateinit var alarmEngine: AlarmEngine

    @MockitoBean
    private lateinit var alarmEvents: AlarmEventRepository

    @Test
    fun admitRedirectsToMonitor() {
        mockMvc.perform(post("/pets/1/admit"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/hospital/1"))
    }

    @Test
    fun latestVitalReturnsJson() {
        val a = PetAdmission()
        a.petId = 1
        a.latestHeartRate = 90
        given(admissions.findByPetIdAndDischargedAtIsNull(1)).willReturn(a)
        mockMvc.perform(get("/hospital/1/vitals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.heartRate").value(90))
    }

    @Test
    fun dischargeRedirectsToMonitor() {
        mockMvc.perform(post("/pets/1/discharge"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/hospital/1"))
    }
}
