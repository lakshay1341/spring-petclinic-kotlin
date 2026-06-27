package org.springframework.samples.petclinic.hospital

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Safety-critical tests for [AlarmEngine]: per-level debounce, EXTREME bypass, gap freezing.
 */
class AlarmEngineTest {

    private val samples = mock(VitalSampleRepository::class.java)
    private val limits = mock(AlarmLimitRepository::class.java)
    private val events = mock(AlarmEventRepository::class.java)
    private val admissions = mock(PetAdmissionRepository::class.java)
    private val engine = AlarmEngine(samples, limits, events, admissions)

    private fun <T> anyObject(): T = Mockito.any()

    private val catLimit = AlarmLimit().apply {
        admissionId = 1; metric = "HR"; lowExtreme = 100; low = 140; high = 220; highExtreme = 260
    }

    private fun admission(): PetAdmission = PetAdmission().apply { id = 1; petId = 1 }
    private fun sample(v: Int): VitalSample = VitalSample().apply { admissionId = 1; metric = "HR"; sampleValue = v }

    @BeforeEach
    fun setup() {
        given(limits.findByAdmissionIdAndMetric(1, "HR")).willReturn(catLimit)
    }

    @Test
    fun extremeLowFiresImmediatelyWithoutDebounce() {
        given(events.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(null)
        // 90 <= lowExtreme(100) => EXTREME, must fire on the first sample
        engine.ingest(admission(), 90)
        verify(events, times(1)).save(anyObject())
    }

    @Test
    fun advisoryFiresOnThirdConsecutiveBreach() {
        given(events.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(null)
        given(samples.findTop3ByAdmissionIdAndMetricAndSampleValueNotNullOrderBySampledAtDesc(1, "HR"))
            .willReturn(listOf(sample(130), sample(130), sample(130)))
        // 130 is LOW advisory (between lowExtreme 100 and low 140) for a cat
        engine.ingest(admission(), 130)
        verify(events, times(1)).save(anyObject())
    }

    @Test
    fun advisoryDoesNotFireOnTwoBreaches() {
        given(events.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(null)
        given(samples.findTop3ByAdmissionIdAndMetricAndSampleValueNotNullOrderBySampledAtDesc(1, "HR"))
            .willReturn(listOf(sample(130), sample(130)))
        engine.ingest(admission(), 130)
        verify(events, never()).save(anyObject())
    }

    @Test
    fun gapDoesNotClearActiveAlarm() {
        val open = AlarmEvent().apply { admissionId = 1; metric = "HR"; level = AlarmLevel.EXTREME; state = "OPEN" }
        given(events.findByAdmissionIdAndMetricAndState(1, "HR", "OPEN")).willReturn(open)
        // null = gap: must not close the open alarm
        engine.ingest(admission(), null)
        verify(events, never()).save(anyObject())
    }
}
