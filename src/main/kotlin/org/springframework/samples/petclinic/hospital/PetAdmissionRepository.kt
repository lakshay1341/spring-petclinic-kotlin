package org.springframework.samples.petclinic.hospital

import org.springframework.data.repository.Repository

interface PetAdmissionRepository : Repository<PetAdmission, Int> {

    fun save(admission: PetAdmission)

    fun findByPetIdAndDischargedAtIsNull(petId: Int): PetAdmission?

    fun findFirstByPetIdOrderByIdDesc(petId: Int): PetAdmission?
}
