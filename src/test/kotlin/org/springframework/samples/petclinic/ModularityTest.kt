package org.springframework.samples.petclinic

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.junit.jupiter.api.Test

/**
 * Enforces the modular-monolith boundaries (see docs/architecture/modules.md).
 *
 * Feature modules: owner, vet, visit, hospital (incl. hospital.ws). Shared: `model` (kernel —
 * BaseEntity/Person) and `system` (infra/config), usable from anywhere. The ONLY allowed
 * cross-feature dependencies are the two deliberate ones:
 *   - hospital -> owner  (loose petId FK: Pet / PetType / PetRepository)
 *   - owner    -> visit  (the Owner aggregate manages a pet's visits)
 * Any other feature -> feature dependency, or any module cycle, fails the build.
 */
class ModularityTest {

    private val base = "org.springframework.samples.petclinic"

    private val imported = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(base)

    private val features = listOf("owner", "vet", "visit", "hospital")
    private val allowed = mapOf(
        "hospital" to setOf("owner"),
        "owner" to setOf("visit")
    )

    @Test
    fun featuresOnlyDependOnAllowedModules() {
        for (feature in features) {
            val forbidden = features.filter { it != feature && it !in (allowed[feature] ?: emptySet()) }
            if (forbidden.isEmpty()) continue
            val forbiddenPkgs = forbidden.map { "..$it.." }.toTypedArray()
            noClasses().that().resideInAPackage("..$feature..")
                .should().dependOnClassesThat().resideInAnyPackage(*forbiddenPkgs)
                .because(
                    "feature '$feature' may depend only on model, system" +
                        (allowed[feature]?.joinToString(prefix = " and ") ?: "")
                )
                .check(imported)
        }
    }

    @Test
    fun featureModulesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
            .matching("$base.(*)..")
            .should().beFreeOfCycles()
            .check(imported)
    }
}
