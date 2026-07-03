# Review 068 — Python parser Phase P3 / Stage 3.1 (AST conformance harness) + takeover

**Context:** the developer was **stuck** mid-Stage-3.1 and asked for a review of
the work done (3.1.1–3.1.3) plus a **takeover** to finish the stage. Stage 3.1
pins the Python AST to the committed TS golden on every shared fixture. Spec:
[`../tasks/05-conformance-ast.md`](../tasks/05-conformance-ast.md); rules in
[`../contracts.md`](../contracts.md) §5 / §6.1.

**Branch:** `python-parser` (uncommitted — see "Remaining").

**Reviewer ran:** the full unit suite, the conformance suite, an independent
byte-diff of all 50 `out-py` vs `out-ts`, a prove-red mutation, `mypy --strict`,
`ruff`, and YAML validation of the new CI.

---

## Review of 3.1.1–3.1.3 (developer's work)

**The harness is genuinely well-built and the green is real.** Independently
verified: **50 top-level fixtures → 50 `out-ts` goldens → 50 `out-py` dumps, 0
byte mismatches, 0 missing, 0 orphan goldens.** The 3 multi-doc subdirs
(`32/33/34`) are correctly excluded from the AST dump (they belong to the §5.1
semantics dump, P5).

- `dump.py` — strict, faithful port of the §5 normalisation: `kind` = lowercased
  keyword, surface property names, `SourceLocation` stripped, keys sorted,
  present-only omission, whole-floats-as-ints, `JSON.stringify(…,4)` formatting +
  trailing newline. It **raises** on unexpected shapes rather than silently
  emitting wrong JSON — exactly right for a golden harness.
- `test_conformance.py` — real byte comparison with a `difflib` unified diff on
  mismatch; re-dumps `out-py/` before asserting; guards against orphan dumps and
  unexpected parse errors.
- The parameter-`type`-as-`IdValue` walker change is **legitimate**, not a hack:
  `ParameterDef` is modelled as an `ObjectValue` whose `entries` must be
  `PropertyValue`s (a `DataType` can't go there), so the type-name rides in an
  `IdValue` and `dump._param` renders it as the golden's `{ "name": "int" }`.

### The one real defect — and why the developer was stuck

`walker.py` `_visit_view` contained a debugging hack:
```python
t = p.tagsProperty()
if t is not None and False:          # ← tags parsing for views permanently disabled
    tags = _visit_list_of_strings(t.listOfStrings(), file)
```

This is almost certainly the **3.1.4 "prove the gate fails red" attempt** that
went wrong: the dev disabled view tags as the intentional mutation — but **no
fixture exercises a view with tags** (`03-view.ttr` has none), so the gate stayed
**green**. The mutation could never turn it red, which reads as "the gate is
broken / I'm confused" → stuck. The `and False` was left in, a latent bug that
silently drops tags on any future view that has them.

---

## Takeover — what I did to finish the stage

| Task | Action | Status |
|---|---|---|
| Remove the hack | `_visit_view`: `if t is not None and False:` → `if t is not None:`. Verified view tags now parse (`('a','b')`), conformance still 50/50 green. | ✅ |
| **3.1.4** prove-red | Mutated a **covered** property (dropped `tags` in the dumper) → **50 fixtures turned red**; restored → green. The gate is proven. (The dev's mistake was mutating an *uncovered* path.) | ✅ |
| **3.1.5** make green | All 50 byte-identical after the hack removal. | ✅ |
| **3.1.6** gitignore | Added `tests/conformance/out-py/` (and `out-py-sem/` for P5) to `.gitignore`; confirmed `out-py` is ignored and 0 files tracked. | ✅ |
| **3.1.7** CI | Added `py-dump` (setup-python 3.13 + setup-java 21 → generate → `pip install -e` → `dump.py` → upload `py-dumps`) and `py-vs-ts` (`needs: [ts-dump, py-dump]`, download both, `diff -ru out-ts out-py`, fail on any diff) to `conformance.yml`. YAML validated; 6 jobs total. | ✅ |

**Verification after takeover:** unit suite **86 passed**; conformance **52
passed** (50 fixtures + 2 guards); `mypy --strict` clean (7 files); `ruff` clean;
`conformance.yml` parses with jobs `ts-dump, kt-dump, kt-sem-dump, diff, py-dump,
py-vs-ts`.

Stage 3.1 DoD is now met: `py-vs-ts` green across every fixture locally, an
intentional change turns it red (proven, reverted), and `out-py/` is gitignored
with only `out-ts/` committed.

---

## Finding for the developer — fixture coverage gap *(Low, follow-up)*

The stuck-point exposed a real gap: **the conformance gate only pins what the
fixtures exercise.** No fixture covers a view with `tags` (and likely other
seldom-used property combinations). The byte-diff is strong for what's covered,
but a walker regression on an *uncovered* path passes CI silently. Recommend, as a
small follow-up, adding a `view`-with-`tags` fixture (and regenerating the TS
golden via `pnpm --filter @modeler/conformance dump`) so that path is pinned. Not
a blocker for 3.1. Tracked in [`tasks-review-068.md`](tasks-review-068.md).

---

## Remaining (developer)

Everything is green but **uncommitted**. To land Stage 3.1, commit:
`dump.py`, `test_conformance.py` (new), the `walker.py` hack removal, `.gitignore`,
`.github/workflows/conformance.yml`, plus the benign `py.typed` (PEP 561 marker —
good to have) and the one-line `test_dedent.py` formatting tweak. Steps in
[`tasks-review-068.md`](tasks-review-068.md).
