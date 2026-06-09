# Stage 4 — Item 2: conformance fixtures + harness + DoD

**Goal:** prove TS and Kotlin produce **identical** resolved-qname sets and
diagnostic-code sets for schema-less inputs, and wire that into the conformance
harness so future grammar/semantics changes can't silently diverge.

**Context:** the semantics conformance harness lives in
`packages/kotlin/ttr-semantics/src/test/kotlin/org/tatrman/ttr/semantics/conformance/`
(`SemanticsConformanceSpec.kt`, `SemanticsConformanceDump.kt`) and the
`conformance.yml` CI workflow; TS dumps land under `tests/conformance/out-ts/`
(gitignored). **Read `docs/grammar-master/tasks/phase-2/06-conformance.md`
first** — match the existing fixture + dump-comparison pattern; do not invent a
new one.

---

- [ ] **4.1 — Add shared fixtures.** Add a schema-less fixture file per
  default-schema group to the conformance corpus (wherever the existing
  semantics conformance fixtures live — find with
  `grep -rn "out-ts\|fixtures" packages/kotlin/ttr-semantics/src/test tests/conformance`).
  One file each containing only: a `def entity` (⇒ `er`), a `def table` (⇒ `db`),
  a `def role` (⇒ `cnc`), a `def query` (⇒ `query`), a `def er2db_entity`
  (⇒ `map`). Plus one control file *with* an explicit `schema` directive to
  guard the override path.

- [ ] **4.2 — Extend the dump to include the schema component.** Ensure the
  conformance dump captures each symbol's full qname (so the schema-code prefix
  is compared) and the emitted diagnostic codes. If the existing dump already
  includes full qnames + diagnostic-code sets, no change — just confirm the new
  fixtures are picked up.

- [ ] **4.3 — Run TS dump + Kotlin spec; assert identical.** Regenerate the TS
  dump and run `SemanticsConformanceSpec`. Assert the new fixtures produce
  byte-identical resolved-qname sets and diagnostic-code sets across TS and
  Kotlin. Fix any divergence in Stage 3 code (not by relaxing the harness).

- [ ] **4.4 — Full green + CHANGELOG.** Run the full gates:
  `pnpm -r test`, `pnpm -r typecheck`, `pnpm -r lint`,
  `./gradlew :packages:kotlin:ttr-semantics:test`, and the conformance workflow
  locally. Add a `CHANGELOG.md` entry under an additive/unreleased section:
  "Schema/namespace now optional with defaults derived from object kind
  (namespace already did; schema now does too). No grammar change — **no
  grammar-version bump.**"

- [ ] **4.5 — Final feature DoD sweep.** Re-open [`INDEX.md`](INDEX.md) and tick
  every box in its "Definition of DONE". Confirm with `git diff --stat` that the
  change set is: the two helpers, the qname/resolution wiring, tests, conformance
  fixtures, CHANGELOG — and **no** `TTR.g4` change.

### Stage 4 DoD
- [ ] Conformance harness compares resolved-qname + diagnostic-code sets for the
      schema-less fixtures and is green in CI.
- [ ] All four stages' boxes ticked; `INDEX.md` DoD fully satisfied.
