# 05 — Kotlin twin + conformance (modeler) — **normative parity**

Goal: Kotlin `ttr-semantics` mirrors the slot model; conformance compares
canonical-key + diagnostic-code sets byte-identical. See [`../plan.md`](../plan.md)
Phase 5, [`../contracts.md`](../contracts.md) §6.

**Pre-flight:** Phase 04 merged. Baseline `./gradlew
:packages:kotlin:ttr-semantics:test` green. Read `Kinds.kt` (`defaultSchemaForKind`
twin) and the conformance harness (`tests/conformance/`).

## Tasks

- [ ] **5.1 Tests first (Kotest).** Port the §6 fixtures: full elision, context kind,
  er/md no-schema, explicit escape hatch, each collision rule — assert identical
  canonical keys + diagnostic codes.
- [ ] **5.2 Port `modelForKind` + slots.** Mirror the kind→model map and the
  `{package, model, schema?, kind, parts}` key in Kotlin.
- [ ] **5.3 Port resolution.** Slot fill + scoped unique-match + header-schema-is-db-
  only (D12), matching the TS fill order exactly.
- [ ] **5.4 Conformance fixtures.** Add the new `.ttrm` fixtures (35–40 style) to the
  harness; wire TS-dump vs Kotlin-dump comparison of resolved-qname + symbol-qname +
  diagnostic-code sets.

## Done when

- [ ] `./gradlew :packages:kotlin:ttr-semantics:test` green.
- [ ] Conformance harness green (TS dump ≡ Kotlin dump, byte-identical).

**Verify:** `./gradlew :packages:kotlin:ttr-semantics:test` ·
`pnpm --filter @modeler/conformance test` (or the harness's runner)
