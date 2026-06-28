# Module architecture

This is a **modular monolith**: one deployable Spring Boot app, internally split into
feature modules with enforced boundaries. The boundaries are verified on every build by
`ModularityTest` (ArchUnit) — an illegal cross-feature import fails the build.

## Modules

| Module | Package | Responsibility |
|---|---|---|
| owner | `…petclinic.owner` | Owners, Pets, PetTypes, and (via the owner UI) their Visits |
| visit | `…petclinic.visit` | The `Visit` entity + repository |
| vet | `…petclinic.vet` | Veterinarians and specialties |
| hospital | `…petclinic.hospital` (+ `.ws`) | Pet-HMS: admissions/ADT, alarms, vitals, waveform, device WebSocket ingest, provisioning |
| model | `…petclinic.model` | **Shared kernel** — `BaseEntity`, `Person`, `NamedEntity`. Usable by any module. |
| system | `…petclinic.system` | **Infra** — caching/web config. Usable by any module. |

## The rule

A feature module may depend on `model` and `system` and on nothing else outside itself —
**except** these two deliberate, documented edges:

- **hospital → owner** — the hospital references a pet by a loose `petId` FK and reads
  `Pet` / `PetType` / `PetRepository`. This single thread is the whole reason the Pet-HMS
  can attach to the existing clinic without modifying `Owner`/`Pet`/`Vet`/`Visit`.
- **owner → visit** — the Owner aggregate manages a pet's visits.

No module cycles are allowed.

## Why ArchUnit, not Spring Modulith (for now)

ArchUnit enforces the boundaries with a single test-scoped dependency and zero runtime
footprint — version-agnostic, no coupling to the Spring Boot release. Spring Modulith would
add a runtime starter plus a compatibility bet against a very new Boot 4.1, in exchange for
features we do not need yet (named interfaces, module test slices, generated docs, the event
publication registry). **Promote to Spring Modulith when** we need the event-publication
registry for reliable async cross-module communication (a transactional outbox) — that is the
one thing this ArchUnit test cannot provide.
