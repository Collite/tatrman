# Phase P3 / Stage 3.1 — Conformance: AST dump + py-vs-ts diff + CI

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.
Goal: pin the Python AST to the **committed TS golden** on every shared fixture.

**Pre-flight:**
- Stage 2.3 merged (parser fully working).
- Read [`../contracts.md`](../contracts.md) §5 (dump schema + normalisation) and
  §6.1 (CI jobs); [`../architecture.md`](../architecture.md) §7.
- Confirm the golden is current: `pnpm --filter @modeler/conformance dump-all`
  produces no git diff under `tests/conformance/out-ts/`.
- Open the TS dumper for the exact normalisation rules and the
  `AST-NAMING.md` Python column (surface-name map).

**Reference files:**
- `tests/conformance/fixtures/` (shared inputs — single files **and** multi-doc
  subdirs; the parser dump ignores subdirs).
- `tests/conformance/out-ts/*.json` (the reference output).
- The TS dump/normalise scripts (`tests/conformance/*.ts`) and Kotlin
  `ConformanceDump.kt` (the same rules, already done twice).

**Tasks** (check each immediately after completion):

- [ ] **3.1.1 — `tests/conformance/dump.py`.** Walk every `*.ttr` file in
      `tests/conformance/fixtures/` (top-level files only — skip subdirectories),
      `parse_file` each, and serialise to the §5 JSON shape:
      `{ schemaDirective, package, imports, definitions:[{kind,name,description,tags,properties}] }`.

- [ ] **3.1.2 — Normalisation (the load-bearing part).** Apply §5 rules exactly:
      (1) **strip every `SourceLocation`** field; (2) **sort object keys**
      alphabetically; (3) `kind` = the `Definition.kind` lowercased keyword;
      (4) property keys = the **TTR surface name** (map snake_case → surface via
      the `AST-NAMING.md` Python column, e.g. `primary_key`→`primaryKey`); (5)
      `by_language` maps as plain objects; (6) native numbers/bools/null. Write
      `out-py/<fixture>.json` with `json.dumps(obj, sort_keys=True, ensure_ascii=False, indent=…)`
      matching the TS formatting byte-for-byte (match indent + trailing newline to
      the golden).

- [ ] **3.1.3 — `tests/conformance/test_conformance.py`.** Parametrise over the
      fixtures; for each, assert `out-py/<f>.json` equals the committed
      `tests/conformance/out-ts/<f>.json` **byte-for-byte** after normalisation.
      Emit a readable diff on mismatch (use `difflib`).

- [ ] **3.1.4 — Prove the gate fails red.** Temporarily mutate one walker output
      (e.g. drop a property), run the suite, confirm a clear red diff, revert.
      Document this in the test module docstring.

- [ ] **3.1.5 — Make it green.** Fix any normalisation/field-name mismatches until
      `py-vs-ts` is green across **all** fixtures. Mismatches here are usually a
      missing entry in the `AST-NAMING.md` Python column or a key-ordering/format
      difference — fix the dumper, not the golden.

- [ ] **3.1.6 — `out-py/` gitignored.** Add `tests/conformance/out-py/` to
      `.gitignore` (CI-only artifact; the **TS** golden is the committed reference,
      not the Python output).

- [ ] **3.1.7 — CI: `py-dump` + `py-vs-ts` in `conformance.yml`.** Add a `py-dump`
      job: `actions/setup-python@v5` (3.10) + `actions/setup-java@v4` (21, for the
      generate step) → `./scripts/generate-python-parser.sh` → `pip install -e .` →
      run `dump.py` → `upload-artifact` `out-py/`. Add a `py-vs-ts` job:
      download `ts-dumps` + `py-dumps`, run the byte diff, fail on any difference.
      Keep the no-paths-filter trigger (matches the existing workflow rationale).

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
python ../../../tests/conformance/dump.py            # writes out-py/
pytest -q tests/conformance/test_conformance.py      # green = no AST drift vs TS
```

**Stage DoD:**
- All seven tasks checked.
- `py-vs-ts` green across every fixture locally and in CI.
- An intentional walker change turns it red (gate proven, then reverted).
- `out-py/` is gitignored; only `out-ts/` is committed.
