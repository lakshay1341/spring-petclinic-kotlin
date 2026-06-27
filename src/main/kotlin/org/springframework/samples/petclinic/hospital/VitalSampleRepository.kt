package org.springframework.samples.petclinic.hospital

import org.springframework.data.repository.Repository

interface VitalSampleRepository : Repository<VitalSample, Int> {

    fun save(sample: VitalSample): VitalSample

    fun findTop3ByAdmissionIdAndMetricAndSampleValueNotNullOrderBySampledAtDesc(
        admissionId: Int,
        metric: String
    ): List<VitalSample>
}
