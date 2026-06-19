# Review 065 — Python parser Phase P2 / Stage 2.1 (Tests-first: parser suites + fixtures)

**Scope reviewed:** the developer's claim that **Stage 2.1** is ready. 2.1 is the
**TDD "tests-first"** stage of Phase P2: write the parser pytest suites and
fixtures *before* the model/walker/loader (stages 2.2–2.3), so they initially
**fail** for "not-yet-implemented" reasons. Spec:
[`../tasks/02-tests-first-parser.md`](../tasks/02-tests-first-parser.md); contract
the suites must target: [`../contracts.md`](../contracts.md) §2.

**Branch:** `python-parser` (tip `0f984c8 PP P1`). Stage 2.1 files are present in
the working tree but **untracked** (see F6).

**Reviewer ran:** `pytest` (confirmed red state + reason), `py_compile` on every
test file, the raw ANTLR parser over all 20 fixtures (grammar validity), a
fixture-content vs test-assertion cross-check, and verified the two non-obvious
behavioural assertions against the canonical TS walker (`packages/parser/src/walker.ts`).

---

## Verdict

**Stage 2.1 is strong work and ~90% done, but it is NOT ready as-is:** two
fixtures are grammatically invalid, so two loader tests will fail for the **wrong
reason** once the walker lands — which directly violates the stage DoD ("fail for
the right reason … not syntax errors"). That is a must-fix (F1). Everything else
is minor.

What is genuinely good here (verified, not assumed):

| Check | Result |
|---|---|
| Suites target the documented §2 API (`parse_string`, `ParseResult.ok/.errors/.definitions`, `SourceLocation`, `TaggedBlockValue`, `IdValue.parts`, `dedent`) | ✅ faithful |
| TDD red state — suites fail because the impl isn't built | ✅ all 5 new suites fail at collection with `ImportError`; `test_smoke` still passes |
| Failures are **not** test syntax errors | ✅ `py_compile` clean on all test files |
| Coverage — one fixture per `def` kind (18) + inline-mapping + tagged-block + dedent + source-location + id-parts | ✅ 20 fixtures + 6 suites |
| Behavioural assertion: unknown property ⇒ parse error (`not ok`) | ✅ matches walker — recovery fixture `unknown-property-name` is `expectErrors: true` |
| Behavioural assertion: unknown tag ⇒ `warning` + triple-string fallback (not error) | ✅ matches `walker.ts:565-572` (`UnknownLanguageTag`, `severity: 'warning'`) |
| Fixture **contents** match the detailed test assertions | ✅ spot-checked 9 fixtures (01,02,04,09,10,16,17,18,19) — all consistent, incl. surface→field mapping `override:`→`override_auto`, localized `displayLabel`/`valueLabels` |
| Multi-token-span invariant test (`end_column == stop.column + len(stop.text)`) present | ✅ `test_source_location.py` |

The dev clearly read `walker.ts`/the Kotlin specs and mirrored real behaviour
rather than guessing. The findings below are about correctness of two fixtures
and a small amount of contract drift.

---

## Findings

### F1 — `05-index.ttr` and `06-constraint.ttr` are grammatically invalid *(Must fix — DoD violation)*

Both fixtures use the **AST field name** as the property key instead of the **TTR
surface keyword**:

```ttr
# 05-index.ttr           # 06-constraint.ttr
def index ix... {        def constraint uq... {
    indexType: btree         constraintType: unique     # ← WRONG key
    columns: [...]           columns: [...]
}                        }
```

The grammar (`TTR.g4:182-183`) defines the property as `DATA_TYPE propSep? …` —
i.e. the surface keyword is **`type`**, not `indexType`/`constraintType`. The AST
*field* is `index_type`/`constraint_type` (contracts §2.5), but the *surface name*
a `.ttr` file writes is `type`. The dev conflated the two.

Verified: the raw ANTLR parser rejects both fixtures —
`mismatched input 'indexType' expecting {'description', 'columns', 'type', '}'}`
— and accepts them once the key is `type:` (values `btree` / `unique` are fine).

**Consequence:** once stage 2.2/2.3 land, the parametrised
`test_parse_string_yields_expected_kind_and_name[05-index.ttr]` and `[06-…]`
assert `result.ok` and will **fail because the fixture won't parse**, not because
of a real walker gap. That is exactly the "fail for the wrong reason" the DoD
forbids. **Fix the two fixtures** (task R1).

**Resolution (applied).** Both fixtures now use `type:` (`type: btree` /
`type: unique`). Re-ran the raw ANTLR parser over all 20 fixtures → **all parse,
zero syntax errors**. **F1 closed.**

### F2 — Tests introduce an undocumented public symbol `extract_reference` *(Should fix — contract drift)*

`tests/test_id_value_parts.py` does `from ttr_parser import … extract_reference`
and asserts its behaviour. But `extract_reference` is **not** in the public API
contract (§2 lists `parse_string`/`parse_file`/`parse_directory` + the model
types; no `extract_reference`). Confirmed: it appears nowhere in `contracts.md`.

A tests-first suite is a spec for the implementation — introducing a public
function the contract doesn't mention pre-commits stage 2.2 to an unspecified
surface, and risks it being implemented as a private helper (then the test fails
to import it for a reason unrelated to the walker). It mirrors `walker.ts`'s
`extractReference`, but that is an internal walker helper in TS, not necessarily
public.

**Decide and record:** either (a) add `extract_reference` to contracts §2 as a
public helper with its signature, or (b) drop the public-API assumption from the
test (test the `IdValue.ref`/`.parts` surface that *is* in the contract). R2.

**Resolution (applied — Option A).** `extract_reference(value: PropertyValue) ->
Reference | None` is now documented in contracts §2.6 as a public helper (mirror
of `walker.ts` `extractReference`), with an explicit note that the argument is
always a `PropertyValue`. **F2 closed.**

### F3 — `extract_reference(description)` feeds a plain `str`, not a `PropertyValue` *(Low)*

In `test_extract_reference_returns_id_for_id_value_only`, the "returns None for
non-Id" branch is exercised with `desc = …definitions[0].description`, which is a
**`str`** (`description: str | None`, §2.5) — not a `PropertyValue`. `walker.ts`'s
`extractReference(value: PropertyValue)` takes a `PropertyValue`; passing a `str`
either forces the Python signature to widen to `object`/`Any` (weakening the type)
or is simply incoherent.

**Fix:** exercise the None branch with an actual non-Id `PropertyValue` (e.g. a
`StringValue`/`NumberValue` read off a property that stays a `PropertyValue`), not
the unwrapped `description` string. R3. (Couples with the F2 decision.)

**Resolution (applied).** The None branch now constructs a real
`StringValue("x", SourceLocation.UNKNOWN)` and asserts `extract_reference(...) is
None`; the `description`-str path is gone. Test still compiles and stays red for
the right reason (awaits the model). **F3 closed.**

### F4 — `isinstance(d, Definition)` requires `Definition` to be `@runtime_checkable` *(Low — heads-up for stage 2.2)*

`test_loader.py` uses `isinstance(d, Definition)`. Contracts §2.5 declares
`class Definition(Protocol)`. A plain `Protocol` is **not** usable with
`isinstance` — it raises `TypeError: Instance and class checks can only be used
with @runtime_checkable protocols`. This is not a test bug, but it is a coupling
the test imposes on the implementation: stage 2.2 must decorate `Definition` with
`@runtime_checkable` (or make it a concrete ABC base). Flag it now so 2.2 doesn't
trip over it. R4 (doc note in the contract).

**Resolution (applied).** Stage 2.2 task `03-model.md` §2.2.4 now carries an
explicit note: make `Definition` a concrete common base class (consistent with
the `PropertyValue` "base class, not Protocol" decision in §2.2.2), or decorate it
`@runtime_checkable` if kept a Protocol — so `isinstance(d, Definition)` works.
**F4 closed.**

### F5 — Misleading source-line citations in test docstrings *(Nit)*

`test_source_location.py` cites `makeSourceLocation (walker.ts:1847)`; other
docstrings cite specific `walker.ts` line numbers. Line numbers rot on the next
edit to `walker.ts`. Prefer citing the **symbol** (`makeSourceLocation`,
`dedentWithIndent`, `extractReference`) without a line number. R5.

### F6 — Stage 2.1 files are not committed; DoD item 2.1.8 unmet *(Process)*

Task 2.1.8 and the DoD require "Commit the failing suites." The six test files
and `tests/fixtures/` are currently **untracked** (`git status` → `??`). The
"ready" claim can't be true until they're committed. (Tooling caches
`.mypy_cache/`, `.pytest_cache/`, `.ruff_cache/` are present on disk but correctly
gitignored — they do not show in `git status`. Good.) Commit after R1–R3. R6.

---

## What was checked and is fine (no action)

- All 18 `def`-kind fixtures present and named per the §2.5 table; `kind` strings
  in the `EXPECTED` table match the lowercased keywords exactly.
- Error-handling tests assert the contract precisely: syntax error ⇒ no raise,
  `errors` non-empty, 1-indexed `line`/`column`, `definitions == ()`, `ok False`.
- `parse_directory` test covers `.ttr` inclusion, `.ttrg` exclusion, and pruning
  of `.modeler`/`node_modules`/`.git`, plus non-recursive mode — matches §2.1.
- `dedent` cases are self-consistent with the §2.9 algorithm (leading-newline
  drop, longest-common-prefix, tabs-as-prefix, blank-line handling).
- Tag-registry parametrisation covers every supported tag/dialect in
  `tag-registry.ts`.
- The 18-drill-map fixture correctly uses surface `override:`/`from:`/`to:` and
  the test reads `override_auto`/`from_`/`to` — the field/surface split is right.

---

## Disposition

| # | Finding | Status |
|---|---|---|
| F1 | Two invalid fixtures (`indexType`/`constraintType`) | **Fixed** — now `type:`; all 20 fixtures parse. |
| F2 | Undocumented public `extract_reference` | **Fixed** — documented in contracts §2.6 (Option A). |
| F3 | `extract_reference(str)` in test | **Fixed** — now a constructed `StringValue`. |
| F4 | `isinstance(d, Definition)` needs runtime-checkable | **Fixed** — note added to stage 2.2 task `03-model.md` §2.2.4. |
| F5 | Rot-prone `walker.ts:<n>` docstring citations | **Open (nit)** — left for the developer; cosmetic only. |
| F6 | Suites not committed (DoD 2.1.8) | **Open (process)** — the developer should commit `tests/` now that F1–F4 are in. |

Post-fix verification (clean venv): all 20 fixtures parse with zero syntax errors;
`py_compile` clean; the 5 suites still fail at collection with `ImportError`
(awaiting the model/walker) — correct TDD red state. **Stage 2.1 is functionally
ready;** only the commit (F6) and the optional docstring nit (F5) remain, both for
the developer.

Ordered steps (R5/R6 still actionable): [`tasks-review-065.md`](tasks-review-065.md).
