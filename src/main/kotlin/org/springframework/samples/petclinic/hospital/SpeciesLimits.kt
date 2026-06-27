package org.springframework.samples.petclinic.hospital

/**
 * Default HR alarm thresholds by species, returned as [lowExtreme, low, high, highExtreme].
 * Stored per-admission at admit time so a vet can retune a Great Dane vs a Chihuahua without
 * a redeploy. Unknown species falls back to a wide band.
 */
object SpeciesLimits {

    fun hr(species: String): IntArray = when (species) {
        "cat" -> intArrayOf(100, 140, 220, 260)
        "dog" -> intArrayOf(40, 60, 140, 180)
        else -> intArrayOf(30, 50, 250, 300)
    }
}
