# Phase 2.6 — Conformance harness extension (semantics)

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half day.

**Pre-flight:**
- Phase 2.5 DoD met. `ttr-semantics` module is green.
- Phase 1.6 conformance harness in place and running in CI.

**Goal:** every fixture is now resolved by both the TS and Kotlin semantics
layers, and the resolved-qname sets must match exactly.

**Tasks:**

- [ ] **2.6.1 — Extend the dump schema with a `resolved` section.** Update
      `contracts.md` §5: each definition's properties may now include a
      `_resolved` map of `(referenceString → resolvedQname)` per the
      Resolver's output. Diagnostics get their own top-level array.
      ```json
      {
        "definitions": [ ... ],
        "diagnostics": [ { "code": "ttr/unimported-reference", "file": "x.ttr", "line": 12, "column": 4 } ]
      }
      ```
      (No message text — code+position only, per §5 rule.)

- [ ] **2.6.2 — Implement the TS side.** Update `tests/conformance/dump.ts`
      to also run the TS resolver/validator and include the resolved
      references + diagnostics.

- [ ] **2.6.3 — Implement the Kotlin side.** Update
      `org.tatrman.ttr.parser.conformance.ConformanceDump` (in `ttr-semantics`
      now, or as a new helper in `ttr-semantics`) to do the same — parse via
      `TtrLoader`, build `SymbolTable`, run `Resolver` per def, run
      `Validator`, dump.

- [ ] **2.6.4 — Regenerate expected dumps and update fixtures.** For each
      fixture in `tests/conformance/fixtures/`, run both sides and diff.
      Add a few new fixtures specifically exercising the resolver: the
      non-recursion case, ambiguity, stock auto-import.

- [ ] **2.6.5 — Verify CI is still green.** Push a draft PR. The
      `conformance.yml` workflow runs both dumps + diff; expect green.

- [ ] **2.6.6 — Intentionally break and revert.** Modify a Kotlin resolver
      step (e.g. comment out the non-recursion guard in step 4). Confirm the
      harness fails specifically on the non-recursion fixture. Revert.
      Same test as Phase 1.6.8 — the harness must catch semantics drift, not
      just walker drift.

**Stage DoD:**
- Six tasks checked.
- `contracts.md` §5 updated to include `_resolved` + `diagnostics`.
- `conformance.yml` green on the extended dump.
- Manual drift-injection test passes.
