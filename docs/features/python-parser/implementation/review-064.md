# Review 064 — Python parser Phase P1 (Scaffolding + generated parser)

**Scope reviewed:** the developer's claim that **Phase P1** of the `python-parser`
feature is done. P1 = `packages/python/ttr-parser/` builds, the ANTLR Python
parser generates from the canonical `TTR.g4`, imports cleanly, and parses a
trivial `.ttr`. Spec: [`../plan.md`](../plan.md) §P1, task list
[`../tasks/01-scaffolding.md`](../tasks/01-scaffolding.md), decisions
[`../INDEX.md`](../INDEX.md) D1–D8, pinned card [`../PINNED.md`](../PINNED.md).

**Branch:** `python-parser`. **Reviewer ran:** fresh `generate-python-parser.sh`,
`pip install -e ".[dev]"`, `pytest`, `ruff check`, `mypy src`, `python -m build
--wheel` + wheel-content inspection, and a `git diff master...python-parser`
audit.

---

## Verdict

**P1's functional Definition of Done is MET.** Everything in the stage DoD runs
green on a clean checkout:

| DoD item | Result |
|---|---|
| `./scripts/generate-python-parser.sh` → `_generated/{TTRLexer,TTRParser,TTRListener,TTRVisitor,__init__}.py` | ✅ regenerates cleanly |
| `pip install -e ".[dev]"` then `import ttr_parser._generated.TTRParser` | ✅ |
| `pytest -q` | ✅ 4 passed |
| `ruff check .` | ✅ all checks passed |
| `mypy src` | ✅ no issues |
| Wheel bundles `_generated/*.py` | ✅ verified via `python -m build --wheel` + `unzip -l` |
| `pnpm -r test` unaffected (no TS/Kotlin source touched) | ✅ confirmed by diff — `master...python-parser` touches **no** TS/Kotlin source |

The scaffolding is clean, well-commented, and faithfully mirrors the TS generate
script. The package tree matches [`../architecture.md`](../architecture.md) §3.
The build hook (`hatch_build.py`) is correct and idempotent. **This is solid
work.** The findings below are about deviations from the *pinned spec* and one
serious repo-hygiene problem — none of them block P1 functionally, but two of
them (F1, F3) should be resolved before this branch merges.

---

## Findings

### F1 — `force-include` for `_generated/` is missing, contradicting pinned D4 *(Medium)*

[`../PINNED.md`](../PINNED.md) line 32 states D4 verbatim:

> **D4 — `_generated/` gitignored**, regenerated from `TTR.g4` at build; bundled
> into the wheel via **`force-include`**.

Task `01-scaffolding.md` §1.1.2 specified the exact block, with a rationale
comment explaining *why* it is needed (`_generated/` is gitignored, so
`force-include` "guarantees the generated parser ships even though it is not
tracked by git"):

```toml
[tool.hatch.build.targets.wheel.force-include]
"src/ttr_parser/_generated" = "ttr_parser/_generated"
```

**The actual `pyproject.toml` omits this block entirely.** The wheel *does*
currently bundle `_generated/*.py` — but only because Hatchling's default
`packages = ["src/ttr_parser"]` walk happens to include the on-disk directory
regardless of `.gitignore`. This is an **undocumented deviation from a pinned
decision**, and it is fragile:

- It relies on Hatchling's default file-selection behaviour rather than the
  explicit, intent-revealing `force-include` the design mandates. A future
  Hatchling version or a `[tool.hatch.build] ignore-vcs`/exclude tweak could
  start honouring the gitignore for the wheel and silently ship a wheel with
  **no parser** — exactly the failure D4's `force-include` exists to prevent.
- The code now disagrees with PINNED.md. Either the code is wrong or the pinned
  doc is wrong; right now they contradict each other.

**Resolution (applied in this branch).** The original plan was to add the
`force-include` block as the pinned doc specified — but doing so **fails the
build**: Hatchling's default `packages = ["src/ttr_parser"]` walk already includes
`_generated/` (it does **not** honour `.gitignore` for the wheel target), so
`force-include` double-adds the same paths (`ValueError: A second file is being
added to the wheel archive at the same path`). That is why the developer omitted
it. The correct, idiomatic mechanism is Hatchling's **`artifacts`** pattern, which
un-ignores the VCS-ignored directory *within* the normal selection instead of
copying it in as a second source:

```toml
[tool.hatch.build.targets.wheel]
packages = ["src/ttr_parser"]
artifacts = ["src/ttr_parser/_generated/**"]
```

This is now in `pyproject.toml`; the wheel bundles all five `_generated/*.py`
files (verified). PINNED.md D4 and task `01-scaffolding.md` §1.1.2 were corrected
to describe `artifacts` (and to warn off `force-include`). **F1 closed.**

### F2 — CPython floor bumped 3.10 → 3.13 by editing the *locked* spec, on a toolchain-convenience rationale *(Medium — needs user sign-off)*

The branch edits **PINNED.md, INDEX-adjacent docs, architecture.md, contracts.md,
plan.md, and the task lists** to change the CPython floor from **3.10** to
**3.13**. PINNED.md is explicitly normative ("These are **fixed** by the design
docs; do not change them without updating `plan.md`/`contracts.md` first"), and
the original 3.10 floor was a deliberate choice — the docs justified it precisely
by the features actually used (`match`, PEP 604 unions, `dataclass(slots=True)`),
all of which are **3.10**.

The new rationale is *"Matches the toolchain available in this repo (`pyenv
3.13.9`)"* — i.e., the developer's local interpreter, **not** a consumer
requirement. The only cited 3.13-only feature (PEP 742 `TypeIs` narrowing) is not
used anywhere in P1 and is not load-bearing in the design.

This matters because this is a **public PyPI library for external consumers**
(D3). A `requires-python = ">=3.13"` floor excludes everyone on 3.11/3.12 (the
bulk of the installed base as of mid-2026) for no functional gain. Optimising the
*published library's* floor for the *developer's* local interpreter is the wrong
trade, and changing a locked decision unilaterally — by rewriting the spec to
match the implementation rather than raising it as a deviation — defeats the
purpose of a pinned card.

**This is the reviewer's main architectural concern and is the user's call, not
the developer's.** The bump may well be fine, but it must be an explicit,
user-approved decision recorded as such — not a silent spec edit. See task R2.

**Resolution.** The user confirmed (2026-06-19): **3.13+ is the intended floor**
for the Python target — "no reason to use 3.10 nowadays". This is now an explicit
owner decision, not a toolchain accident. The pinned 3.13 value stands as-is.
**F2 closed.**

> Note: this is a *documented* deviation (the developer did update the docs), so
> it is better than a silent one. The objection is to **the substance** (3.13-only
> for a public lib) and **the process** (self-approving a pinned decision), not to
> a lack of paper trail.

### F3 — `graphify-out/` (≈55 MB, 610k-line `graph.json`) committed to the branch and *not* gitignored *(High — repo hygiene; must fix before merge)*

The branch's tip commit (`3c295d6 graphify`) adds 8 tracked files under
`graphify-out/` totalling ~55 MB, including `graph.json` at **610,438 lines**.
This is `/graphify` skill output — a knowledge-graph artifact — and has **nothing
to do with the Python parser feature**. It is not in `.gitignore` (the developer
added Python ignore rules but not this directory), so it is fully tracked.

Merging this would permanently bloat the repository history by tens of MB.
**Resolution.** The user confirmed (2026-06-19) this is **intentional**:
`graphify-out/` (including `graph.json`) is meant to be **committed and shared**
so coding agents can use the prebuilt knowledge graph to navigate the codebase —
regenerating it is expensive, so it is checked in deliberately. It stays tracked;
it must **not** be gitignored. (Follow-up the user noted, out of scope for this
review: a `graphify` skill that instructs agents to consult the committed graph
when exploring the code.) **F3 closed — not a defect.**

### F4 — `pyproject.toml` project URLs point to the wrong GitHub org *(Low — surfaces at P6 as wrong PyPI metadata)*

```toml
[project.urls]
Homepage = "https://github.com/anomalyco/modeler"
Issues   = "https://github.com/anomalyco/modeler/issues"
```

The actual remote is `git@github.com:Collite/modeler.git` — the org is
**Collite**, not `anomalyco`. These URLs become the project's PyPI metadata at
publish (P6) and currently 404. Fix now while it's cheap. See task R4.

**Resolution.** Both URLs repointed to `https://github.com/Collite/modeler`.
**F4 closed.**

### F5 — Build-hook Java-presence guard is inconsistent with the script it runs *(Low)*

`hatch_build.py` only emits the friendly "Java is required…" error **when
`_generated/TTRParser.py` does not already exist**. But the generate script it
invokes is unconditional: it `rm -f`s the generated `.py` files and *always*
calls `java -jar`. So on a machine where `_generated/` is populated-but-stale and
Java is absent, the guard is skipped and the build dies with a raw
`CalledProcessError` from `subprocess.run(..., check=True)` instead of the helpful
message. Either run the guard unconditionally (the script always needs Java) or
drop the conditional and let the script's own failure speak. See task R5.

**Resolution.** The guard is now unconditional in `hatch_build.py`. **F5 closed.**

### F6 — Smoke-test grammar fix was correct; the *task spec's* example was wrong *(Informational — no action on the code)*

Task `01-scaffolding.md` §1.1.7 and §1.1.6 show the smoke test parsing
`"model X {}"`. The grammar (`TTR.g4`) requires the `def` keyword:
`def model X {}`. The developer correctly used `def model X {}` and documented
why in the test docstring. **This is a good catch, not a defect** — flagged only
so the *task document* gets corrected so the next person isn't misled. See task
R6 (doc-only).

**Resolution.** `tasks/01-scaffolding.md` §1.1.7 now uses `def model X {}`.
**F6 closed.**

---

## What was checked and is fine (no action)

- Package tree, `__init__.py` (`__version__` only), and module layout match
  architecture §3.
- `generate-python-parser.sh` faithfully mirrors the TS script; pins ANTLR
  **4.13.2** reference jar (D1), `-Dlanguage=Python3 -visitor`, clears stale
  outputs, caches the jar, reads `TTR.g4` directly (no vendoring, D2).
- `antlr4-python3-runtime==4.13.2` pinned (D1).
- `.gitignore` correctly ignores `_generated/`, `dist/`, `build/`, venvs and
  Python caches (D4) — **except** `graphify-out/` (F3).
- ruff (`E,F,I,B,UP,N`) and mypy (`strict = true`, `exclude_gitignore = true`)
  configured and green; `_generated/` excluded from both.
- The build hook regenerates the parser fresh inside an isolated `python -m
  build` environment, proving the CI wheel path works end-to-end.
- No TS or Kotlin source touched — the feature is purely additive, as designed.

---

## Disposition — all findings closed

P1 is **done**. Every finding is resolved:

| # | Finding | Disposition |
|---|---|---|
| F1 | `_generated/` wheel inclusion | **Fixed** — `artifacts` pattern (not `force-include`, which collides); wheel bundles all 5 generated `.py`. Docs corrected. |
| F2 | CPython 3.13 floor | **User decision** — 3.13+ confirmed as the intended floor. |
| F3 | `graphify-out/` committed | **Intentional** — committed-and-shared by design; not a defect. |
| F4 | Wrong project URLs | **Fixed** — repointed to `Collite/modeler`. |
| F5 | Build-hook Java guard | **Fixed** — now unconditional. |
| F6 | Task-doc smoke example | **Fixed** — `def model X {}`. |

Post-fix verification (clean venv): `pytest` 4 passed · `ruff` clean · `mypy src`
clean · `python -m build --wheel` bundles `_generated/*.py`. No TS/Kotlin source
touched. **P1 accepted.**

The original ordered task steps are retained in
[`tasks-review-064.md`](tasks-review-064.md) for the record; R1/R4/R5/R6 were
applied by the reviewer, R2/R3 resolved as owner decisions.
