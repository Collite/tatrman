# Review 067 — Python parser Phase P2 / Stage 2.3 (Walker + dedent + tag registry + loader)

**Scope reviewed:** the developer's claim that **Stage 2.3** — the core port — is
finished: `walker.py` (1439 lines), `loader.py`, `dedent.py`, `tag_registry.py`,
wired through `__init__.py`. The binding instruction is "mirror `walker.ts`
exactly". Spec: [`../tasks/04-walker-loader.md`](../tasks/04-walker-loader.md);
behaviour pinned by [`../contracts.md`](../contracts.md) §2 and the committed
Stage 2.1 suites.

**Branch:** `python-parser` (the 2.3 files are uncommitted — F4).

**Reviewer ran:** full `pytest`, `mypy --strict`, `ruff`; parsed **all 31 valid
`samples/` files** + the 5 `samples/broken/` files; probed the walker with the
original rich mapping fixture; diffed the modified committed tests/fixtures against
the grammar/ANTLR semantics; and checked tag-registry + diagnostic parity with TS.

---

## Verdict

**Stage 2.3 is the strongest stage so far and is essentially done.** The port is
faithful, clean, and well-modularised; it works on real data. One substantive
issue (a silent coverage regression in the inline-mapping fixture, F1) and a
contract-wording bug (F2) should be addressed; the rest is a nit and the commit.

Verified green / correct:

| Check | Result |
|---|---|
| Full parser suite | ✅ **86 passed** |
| `mypy --strict` + `ruff` | ✅ clean (7 source files) |
| Real corpus: every `samples/**` valid file | ✅ **31 files, 2153 defs, 0 errors** |
| Broken files: no-raise + errors + empty defs (contracts §2.1) | ✅ all 5 → `ok=False`, 1 error, 0 defs |
| Source-location span invariant (`end_column = stop.column + len(stop.text)`) | ✅ `make_source_location` + `test_source_location` |
| Tag registry vs `tag-registry.ts` | ✅ **14 tags, byte-identical** |
| `DiagnosticCode` vs Kotlin | ✅ identical (from review-066) |
| Walker handles the **rich** mapping (nested target, `MappingColumnObject`, multi-column) | ✅ verified directly — `ok=True`, correct AST |
| Dedent (3-step) + tagged-block fallback/warning | ✅ suites green |

**The two committed-test edits are legitimate fixes to genuine Stage 2.1 bugs**
(I verified each):

- `test_loader.py` import/schema reorder — the grammar is
  `packageDecl? importDecl* (schemaDirective|graphBlock)? definition*`
  (`TTR.g4:37-39`), so imports **must** precede `schema`. The original 2.1 text
  (`schema` before `import`) was grammar-invalid. Correct fix.
- `test_source_location.py` multi-line span — the def's stop token is `}`; the
  trailing `\n` is hidden-channel WS and not in the span. The original assertion
  (span includes the trailing newline) was wrong. Correct fix.
- `11-relation.ttr` — dropping the two decoy `entity` defs so the parametrised
  `len(definitions) == 1` holds (references resolve at P4, not parse). Correct.

Code quality is good: one `_visit_<kind>` per def kind, separate value builders
(`_visit_literal`/`_id`/`_list`/`_object`/`_embedded_block`/…), token helpers, and
a single `make_source_location` honouring the span invariant. Naming and structure
read like a faithful transcription of `walker.ts`.

---

## Findings

### F1 — `19-inline-mapping.ttr` was silently gutted, dropping all rich-mapping coverage *(Medium)*

The dev rewrote the committed inline-mapping fixture from a **rich** mapping:
```ttr
mapping: {
    target: { table: db.dbo.QZBOZI_DF },
    columns: {
        id_artiklu: IDZBOZI,                       # bare-id
        kod_artiklu: { target: KOD_ZBOZI },        # object form
        nazev_artiklu: { target: { column: ... } } # nested target
    }
}
```
down to a single bare-id column (`columns: { id: IDZBOZI }`).

This was **not test-driven** and **not necessary**, and I verified both claims:

- The only assertion on this fixture is `assert e.mapping is not None`
  (`test_loader.py:220`) — the *original* rich fixture satisfies it.
- The walker parses the original rich fixture **correctly**: `ok=True`,
  `MappingPropertyBlock` → `TargetObjectValue`, 3 columns
  (`MappingColumnBareId`, `MappingColumnObject`, `MappingColumnObject`), 3
  attributes. No bug was being worked around.

Net effect: the committed suite now exercises **zero** of `MappingColumnObject`,
nested `TargetValue`, or multi-column mappings — precisely the v2.1 surface that
Stage 2.3.6 exists to port. A regression in those walker paths would now pass CI
until the P3 conformance harness lands (and §5 strips `SourceLocation`, so even
then only structure is pinned).

**Compounding gap:** Task 2.3.6 says "Make `test_inline_mappings.py` and
`test_drill_map.py` green," but **those files were never created** (not in 2.1, not
in 2.3). Mapping/drill coverage is folded into two shallow `test_loader` cases
(`mapping is not None`; drill `args == {…}`). So the rich mapping walker is
effectively untested.

**Fix:** restore the rich fixture (it works) **and** add a structural assertion —
either expand `test_parse_string_inline_mapping_block` to check the column
variants, or add the `test_inline_mappings.py` the task calls for. R1.

**Resolution (applied).** `19-inline-mapping.ttr` restored to the rich form (3
column variants + nested target). `test_parse_string_inline_mapping_block` now
asserts the structure: `MappingPropertyBlock` → `TargetObjectValue`, and the three
columns are `MappingColumnBareId` / `MappingColumnObject` / `MappingColumnObject`.
Verified green (86 passed). **F1 closed.**

### F2 — contracts §2.4 says "byte offsets," but the walker emits character offsets (and is right to) *(Low — doc bug)*

`contracts.md` §2.4 and its "Byte-offset note" state `offset_start`/`offset_end`
are **byte** offsets. The walker sets them from raw ANTLR token offsets
(`offset_start = tok.start`, `offset_end = tok.stop + 1`) — i.e. **character**
offsets. I confirmed on a non-ASCII source that `src.encode()[start:end]` does
**not** reconstruct the span (it drifts by the extra UTF-8 bytes), while the
character slice is exact.

Crucially, **the implementation is correct**: `walker.ts:1853-1861` does the
identical thing (`offsetStart: startToken.start`, `offsetEnd: stopToken.stop + 1`)
with **no** byte conversion — so the Python walker is faithful to the TS
reference. The bug is the **contract wording**, which would mislead the future
edit-synthesizer into byte-slicing.

**Fix (doc only):** change §2.4 + the byte-offset note to state these are
**character offsets** (raw ANTLR token offsets, matching TS/Kotlin; note the
JVM-UTF-16-vs-codepoint subtlety only bites for astral-plane characters). R2.

**Resolution (applied).** §2.4 field comments now read "0-indexed character
offset"; the note is retitled "Offset note" and states they are raw ANTLR
character offsets (not UTF-8 bytes), slice the source string, with the JVM-UTF-16
caveat. **F2 closed.**

### F3 — Pervasive `ctx: Any` in the walker disables type-checking on tree navigation *(Nit)*

Every builder takes `ctx: Any`, so `mypy --strict` passes in part because
parse-tree access is untyped. This is a defensible trade-off (ANTLR's generated
Python context types are awkward to thread), and the TS walker gets real types
"for free" from antlr-ng. Not a blocker; worth a one-line rationale comment at the
top of `walker.py` (or typing the few hot contexts) so the `Any` usage reads as
deliberate rather than an escape hatch. R3.

**Resolution (applied).** A rationale paragraph was added to `walker.py`'s module
docstring explaining the `ctx: Any` choice and noting that field outputs are fully
typed via the model dataclasses. **F3 closed.**

---

## What was checked and is fine (no action)

- `loader.py`: shared `_CollectingErrorListener` added to **both** lexer and
  parser; `column + 1` for 1-indexed display; empty `definitions` on any
  lexer/parser **or** walker error; `parse_directory` prunes
  `.modeler`/`node_modules`/`.git`, excludes `.ttrg`, sorts for determinism.
- `dedent.py`: the 3-step algorithm (leading-newline drop, longest common
  whitespace prefix, blank-line normalisation); `DedentResult` exposes
  `indent_width` for `TaggedBlockValue`.
- `model.py` tweak this round (`description`/`tags` gained `= None`/`= ()`
  defaults) is a sensible construction-ergonomics change; consistent with the
  contract.
- `__init__.py` now re-exports `parse_string`/`parse_file`/`parse_directory` +
  `dedent`/`dedent_with_indent`/`DedentResult` (the 2.3 names land as planned).

---

## Disposition

| # | Finding | Status |
|---|---|---|
| F1 | `19-inline-mapping.ttr` gutted — rich-mapping coverage lost | **Fixed** — fixture restored + test asserts the full structure. |
| F2 | contracts §2.4 says "byte offsets" (walker emits character offsets) | **Fixed (doc)** — §2.4 now says character offsets. |
| F3 | pervasive `ctx: Any` undocumented | **Fixed** — rationale added to `walker.py` docstring. |
| F4 | 2.3 files uncommitted | **Open (process)** — developer to commit. |

Post-fix verification (clean venv): `pytest` **86 passed**, `mypy --strict` clean
(7 files), `ruff` clean; the strengthened `test_parse_string_inline_mapping_block`
passes against the restored rich fixture. **Stage 2.3 is functionally ready;** only
the commit (F4) remains, for the developer.

Ordered steps (R4 still actionable): [`tasks-review-067.md`](tasks-review-067.md).
