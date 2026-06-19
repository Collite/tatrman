# Phase P2 / Stage 2.1 — Tests first: parser pytest suites + fixtures

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half–1 day.
**TDD:** these suites are written **before** the model/walker (stages 2.2–2.3) and
must initially **fail** (import errors / not-implemented) — that is expected.

**Pre-flight:**
- Stage 1.1 merged (package installs, `_generated/` builds).
- Read [`../contracts.md`](../contracts.md) §2 (parser API) and §2.9 (dedent).
- Open the canonical specs to mirror: Kotlin
  `packages/kotlin/ttr-parser/src/test/kotlin/.../{loader,walker,model}/*Spec.kt`
  and TS `packages/parser/src/__tests__/*.test.ts`.

**Reference files (port the assertions, not the syntax):**
- `TtrLoaderSpec.kt`, `ParseDirectorySpec.kt`, `DedentSpec.kt`,
  `TaggedBlockSpec.kt`, `InlineMappingsSpec.kt`, `DrillMapParserSpec.kt`,
  `SourceLocationSpec.kt`, `IdValuePartsSpec.kt`.
- Existing `.ttr` snippets in `packages/parser/src/__tests__/` and `samples/`.

**Tasks** (check each immediately after completion):

- [ ] **2.1.1 — Test fixtures dir.** Create `tests/fixtures/` with small `.ttr`
      snippets, one per `def` kind (model, table, view, column, index, constraint,
      fk, procedure, entity, attribute, relation, er2db_entity, er2db_attribute,
      er2db_relation, query, role, er2cnc_role, drill_map). Reuse content from the
      Kotlin/TS specs verbatim so behaviour is comparable.

- [ ] **2.1.2 — `tests/test_loader.py`** (← `TtrLoaderSpec`): assert
      `parse_string` returns a `ParseResult` whose `definitions` contain the right
      `kind` + `name` for each fixture; `ok is True`; `errors == ()`. One
      parametrised test over the fixtures is fine.

- [ ] **2.1.3 — Error-handling tests** (in `test_loader.py`): a syntactically
      broken `.ttr` → `parse_string` **does not raise**; `errors` non-empty with
      1-indexed `line`/`column`; `definitions == ()` (no partial trees);
      `ok is False`. (Contracts §2.1, §2.3.)

- [ ] **2.1.4 — `tests/test_parse_directory.py`** (← `ParseDirectorySpec`): a temp
      dir with two `.ttr` files + one `.ttrg` + a `.modeler/` subdir → returns
      exactly the two `.ttr` results; `.ttrg` **excluded**; `.modeler`/`node_modules`/
      `.git` skipped. (Contracts §2.1.)

- [ ] **2.1.5 — `tests/test_dedent.py`** (← `DedentSpec`): port the CPython
      reference cases for the 3-step dedent (drop leading newline after `"""`,
      longest common prefix over non-blank lines, blank lines → empty). Assert
      against `ttr_parser.dedent` (contracts §2.9). Include a tagged-block case.

- [ ] **2.1.6 — `tests/test_tagged_block.py`** (← `TaggedBlockSpec`): a
      `"""sql\n  SELECT 1\n"""` value parses to a `TaggedBlockValue` with
      `tag=="sql"`, resolved `language`, `dialect`, fence-stripped + dedented
      `value`, and the two source spans. (Contracts §2.6.)

- [ ] **2.1.7 — `tests/test_source_location.py`** (← `SourceLocationSpec`) +
      `tests/test_id_value_parts.py` (← `IdValuePartsSpec`): assert the
      multi-token-span invariant `end_column == stop.column + len(stop.text)` on a
      multi-token def header; assert `IdValue.parts == ("db","dbo","customers")`
      for `db.dbo.customers`. (Contracts §2.4, §2.6.)

- [ ] **2.1.8 — Confirm red.** `pytest -q` runs and the new suites **fail** with
      `ImportError`/`AttributeError` (model/walker/loader not built yet). Commit
      the failing suites. (TDD checkpoint.)

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
pytest -q tests/ ; echo "expected: failures (model/walker not implemented yet)"
```

**Stage DoD:**
- All eight tasks checked.
- Fixtures cover every `def` kind plus dedent / tagged-block / mapping / drill_map
  / source-location / id-parts edge cases.
- Suites are committed and fail for the right reason (not-yet-implemented), not
  syntax errors in the tests.
