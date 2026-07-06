# T4 — Kotlin parity (ttr-parser / ttr-writer / ttr-semantics)

Pre-flight: T1–T3 green. Kotlin modules read `TTR.g4` directly (no sync); the ANTLR Gradle plugin regenerates at build.

- [ ] **T4.1 — Kotest specs first.** In `packages/kotlin/ttr-parser` add `SemanticsBlockSpec` (StringSpec): parse `59-semantics.ttrm` → AST carries the semantics entries on the four attachment kinds; duplicate-key bookkeeping matches TS (`duplicateProperties`); scalar-only enforcement matches T2.4. In `packages/kotlin/ttr-semantics` add `SemanticsValidationSpec`: the same TTR-SEM-200…211 matrix as T3.2 (share fixture text via test resources; keep case names aligned with the TS suite for conformance triage). All red.
- [ ] **T4.2 — Kotlin AST + walker.** Mirror T2: `SemanticsBlock(entries, duplicateProperties, location)` data class; walker folding for `semanticsBlockProperty` contexts; attach to entity/attribute/table/column AST nodes.
- [ ] **T4.3 — Kotlin validator.** Mirror T3.3/T3.4: vocabulary object (with `SEMANTICS_VOCABULARY_VERSION = 1` — assert equality with the TS constant in a conformance fixture), `ResolvedSemantics`, per-kind validation pass, same diagnostic codes/messages.
- [ ] **T4.4 — ttr-writer.** Emit `semantics { … }` blocks (property ordering: after `search`, stable key order: `kind` | `role` first, then refs, then params). Round-trip test: parse `59-semantics.ttrm` → write → reparse → AST-equal modulo trivia.
- [ ] **T4.5 — Conformance harness.** Add `59-semantics.ttrm` + the negative roster to the TS↔Kotlin conformance run (`conformance.yml` fixtures dir) so cross-target drift on the block is caught the same way as every other construct.
- [ ] **T4.6 — Green.** `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-writer:test :packages:kotlin:ttr-semantics:test` and the conformance workflow locally; ktlint green.
