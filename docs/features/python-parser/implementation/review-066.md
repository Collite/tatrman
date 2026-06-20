# Review 066 — Python parser Phase P2 / Stage 2.2 (Model: frozen dataclasses)

**Scope reviewed:** the developer's claim that **Stage 2.2** is done. 2.2 is the
**model layer**: `model.py` (the `Definition`/`PropertyValue`/support/mapping
dataclasses + `ParseResult`) and `diagnostics.py` (the `DiagnosticCode` /
`DiagnosticSeverity` enums), re-exported from `__init__.py`, plus the Python
column in `AST-NAMING.md`. Spec:
[`../tasks/03-model.md`](../tasks/03-model.md); the authoritative signatures are in
[`../contracts.md`](../contracts.md) §2.4–§2.8; the committed Stage 2.1 suites are
the behavioural pin.

**Branch:** `python-parser`. The 2.2 files (`model.py`, `diagnostics.py`, the
`__init__.py`/`AST-NAMING.md` edits) are present but **uncommitted** (F6).

**Reviewer ran:** `mypy --strict` + `ruff` (clean), code-vs-contract diff,
diagnostic-code diff against the canonical Kotlin enum, a runtime instantiation
check, `pytest --collect-only`, and a direct check of the model types against the
**committed** 2.1 test assertions.

---

## Verdict

**Stage 2.2 is structurally excellent but NOT done:** two `Definition` field types
contradict the committed Stage 2.1 tests *and* the §2.5/§2.6 contract, and will
make those tests fail for the wrong reason the moment the walker (2.3) lands.
There is also a broken public export. These must be fixed before 2.2 is "done".

What is genuinely strong (verified):

| Check | Result |
|---|---|
| All 18 `Definition` kinds present, `kind` = lowercased keyword | ✅ |
| 10 `PropertyValue` variants + mapping + support types, every variant carries `source` | ✅ |
| `DiagnosticCode.value` == Kotlin `DiagnosticCode.id` | ✅ **byte-identical — 29 codes, `diff` empty** |
| `@dataclass(frozen=True, slots=True)`, snake_case fields, tuple collections | ✅ (instantiates cleanly at runtime — slots+inheritance OK) |
| v2.0.0 fix: no top-level `searchable` on `ColumnDef`/`AttributeDef`; `ColumnDef.indexed` stays | ✅ |
| `from_` (+`metadata={"surface":"from"}`), `Reference.of`, `SourceLocation.UNKNOWN` | ✅ |
| `AST-NAMING.md` Python column (DoD 2.2.8) | ✅ complete and accurate (TS↔Kotlin↔Python for every kind, PropertyValue, support type) |
| `mypy --strict` (3 files) + `ruff` | ✅ clean |
| 2.1 suites now collect (78 tests); only `test_dedent` fails — on the 2.3 `dedent` module | ✅ matches DoD "fail only on walker/loader" |

The structural port is faithful and the diagnostics work is exemplary. The
findings are specific type/coupling defects, not a flawed approach.

---

## Findings

### F1 — `Definition.description` is `StringValue | TripleStringValue | None`, contradicting §2.5 and the committed 2.1 tests *(High — breaks tests at 2.3)*

`model.py:448`:
```python
description: StringValue | TripleStringValue | None
```
But contracts §2.5 declares `description: str | None`, and the committed
`test_loader.py` asserts it as a **plain string**:
```python
assert m.description == "ERP v1 model"      # :119
assert m.description == "with equals"        # :269
```

I verified the bind directly: a `StringValue("ERP v1 model", …) == "ERP v1 model"`
is **`False`** (dataclass equality is type-sensitive). So at stage 2.3 the walker
faces an unwinnable choice:

- store a `StringValue` (per this annotation) → `test_loader` assertions **fail**;
- store a `str` (per the contract/tests) → **mypy error** against the annotation.

The §5 conformance dump also serialises `description` as a bare string. **Fix:
`description: str | None`** on the `Definition` base (R1).

**Resolution (applied).** `Definition.description` is now `str | None`;
`mypy --strict` clean. **F1 closed.**

### F2 — `QueryDef.source_text` / `ViewDef.definition_sql` are `str` + invented `*_block` fields, contradicting the committed tagged-block tests *(High — breaks tests at 2.3)*

`model.py` `QueryDef`:
```python
source_text: str | None = None
source_text_block: PropertyValue | None = None      # ← not in the contract
```
(same pattern on `ViewDef`: `definition_sql: str | None` + `definition_sql_block`).

But the committed `test_tagged_block.py` requires `q.source_text` itself to be the
`TaggedBlockValue`:
```python
block = q.source_text
assert isinstance(block, TaggedBlockValue), f"... got {type(block)}"   # :41-42
# and C7/unknown-tag: assert not isinstance(q.source_text, TaggedBlockValue)
```

Contracts §2.5 lists `source_text` with **no** `_block` companion, and §2.6 says
`TaggedBlockValue` is the carrier "produced … by sourceText/definitionSql" — i.e.
`source_text` holds the `PropertyValue` (a `StringValue`/`TripleStringValue`/
`TaggedBlockValue`). Since `source_text` is typed `str | None`, it can never hold
a `TaggedBlockValue`, so every `_query_source_text(...)` test fails at 2.3.

The `*_block` split is an **undocumented invention**. **Fix: type
`source_text: PropertyValue | None` and `definition_sql: PropertyValue | None`;
remove the `*_block` fields.** If a flattened-string convenience is genuinely
wanted, that is a contract change (update §2.5 + the 2.1 tests) and needs sign-off
— it is not the default. R2.

**Resolution (applied).** `QueryDef.source_text` and `ViewDef.definition_sql` are
now single `PropertyValue | None` fields; the `*_block` companions are removed
(docstrings updated). `mypy --strict` clean; no `_block` fields remain. **F2
closed.**

### F3 — `PropertyValue` is exported in `__all__` but never imported into `__init__.py` *(Medium — broken public API)*

`__all__` lists `"PropertyValue"` (line 110), but the `from .model import (...)`
block omits it. Verified: `from ttr_parser import PropertyValue` raises
`ImportError`, and `"PropertyValue" in dir(ttr_parser)` is `False`. So a documented
public base type (§2.6) is unimportable, and `from ttr_parser import *` would break
on the dangling `__all__` entry. (`ruff`'s F822 did not catch it here.) This also
matters for F2: typing `source_text` as `PropertyValue` makes the public symbol
load-bearing. **Fix: add `PropertyValue` to the import list.** R3.

**Resolution (applied).** `PropertyValue` is now imported in `__init__.py`;
`from ttr_parser import PropertyValue` succeeds. **F3 closed.**

### F4 — `DiagnosticSeverity` values are lowercase, contradicting contract §2.8 *(Low)*

`diagnostics.py` has `ERROR = "error"` / `"warning"` / `"information"` / `"hint"`;
contracts §2.8 specifies capitalised `"Error"` / `"Warning"` / `"Information"` /
`"Hint"` (matching Kotlin `enum class DiagnosticSeverity { Error, Warning,
Information, Hint }` → `.name`). The TS walker uses lowercase, so there is a real
TS-vs-Kotlin split; severity is **not** part of the conformance dump (§5.1 rule 2),
so impact is low. But code and contract must agree. **Reconcile:** either set the
values capitalised to match §2.8/Kotlin, or change §2.8 to lowercase and note the
TS alignment. R4.

**Resolution (applied — Option A).** `DiagnosticSeverity` values are now
capitalised (`"Error"`/`"Warning"`/`"Information"`/`"Hint"`), matching contract
§2.8 and Kotlin `.name`. **F4 closed.**

### F5 — `extract_reference(value: object)` widens the documented signature *(Low)*

`__init__.py:71` types the parameter `object`; contracts §2.6 (added in review-065)
documents `extract_reference(value: PropertyValue) -> Reference | None`, "the
argument is always a `PropertyValue`". `object` is wider and defeats type-checking
of callers. The 2.1 test now passes a real `StringValue`, so the parameter can be
tightened to `PropertyValue` without breaking anything. **Fix: type it
`PropertyValue`.** R5.

**Resolution (applied).** `extract_reference(value: PropertyValue)` now;
`mypy --strict` clean. **F5 closed.**

---

## What was checked and is fine (no action)

- Mapping hierarchy (`MappingProperty`/`…BareId`/`…Block`, `TargetValue`/
  `TargetObjectValue`/`TargetReferenceValue`, `MappingColumnEntry`/
  `MappingColumnValue`/`…BareId`/`…Object`) matches §2.7 with concrete bases for
  isinstance.
- `IdValue` carries both `ref: Reference` and `parts` (cross-language parity).
- `ObjectValue.entries` as a `Mapping` (Kotlin shape) — documented divergence from
  TS ordered entries; acceptable and noted in `AST-NAMING.md`.
- `Definition` is a concrete base class (so `isinstance(d, Definition)` works) —
  the review-065 F4 note was correctly applied.
- Re-exports: `parse_string`/`parse_file`/`parse_directory`/`dedent` deliberately
  not yet exported (land in 2.3) — matches the task.

---

## Disposition

| # | Finding | Status |
|---|---|---|
| F1 | `description` typed `StringValue\|TripleStringValue` | **Fixed** — now `str \| None`. |
| F2 | `source_text`/`definition_sql` str + invented `*_block` | **Fixed** — single `PropertyValue \| None`; `*_block` removed. |
| F3 | `PropertyValue` in `__all__` but not imported | **Fixed** — imported; export resolves. |
| F4 | `DiagnosticSeverity` casing | **Fixed (Option A)** — capitalised to match §2.8/Kotlin. |
| F5 | `extract_reference(value: object)` | **Fixed** — tightened to `PropertyValue`. |
| F6 | 2.2 files uncommitted | **Open (process)** — the developer should commit `model.py`, `diagnostics.py`, `__init__.py`, `AST-NAMING.md`. |

Post-fix verification (clean venv): `mypy --strict` clean (3 files), `ruff` clean,
`from ttr_parser import PropertyValue` resolves, severity values capitalised, both
carrier fields `PropertyValue | None`, no `_block` fields remain, and the 2.1
suites still collect 78 tests with only `test_dedent` erroring (awaits the 2.3
`dedent` module). **Stage 2.2 is functionally ready;** only the commit (F6)
remains, for the developer.

Ordered steps (R6 still actionable): [`tasks-review-066.md`](tasks-review-066.md).
