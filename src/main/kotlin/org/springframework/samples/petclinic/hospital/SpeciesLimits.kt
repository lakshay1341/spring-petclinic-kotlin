package org.springframework.samples.petclinic.hospital

/**
 * Default alarm thresholds by species + metric, returned as [lowExtreme, low, high, highExtreme].
 * Stored per-admission at admit time so a vet can retune without a redeploy. Unknown species falls
 * back to a wide band. HR/RR are species-specific; SpO2 is species-agnostic.
 */
object SpeciesLimits {

    fun hr(species: String): IntArray = when (species) {
        "cat" -> intArrayOf(100, 140, 220, 260)
        "dog" -> intArrayOf(40, 60, 140, 180)
        else -> intArrayOf(30, 50, 250, 300)
    }

    fun rr(species: String): IntArray = when (species) {
        "cat" -> intArrayOf(10, 16, 40, 50)
        "dog" -> intArrayOf(8, 10, 35, 45)
        else -> intArrayOf(8, 12, 40, 50)
    }

    // SpO2 is species-agnostic and effectively LOW-only: high/highExtreme sit above the 100%
    // physiological ceiling so a HIGH alarm can never fire. ponytail: a sentinel beats a per-metric
    // direction flag and an extra branch in every alarm evaluation.
    fun spo2(): IntArray = intArrayOf(85, 90, 101, 101)
}
