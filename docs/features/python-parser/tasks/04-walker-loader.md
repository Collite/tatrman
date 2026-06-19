# Phase P2 / Stage 2.3 — Walker + dedent + tag registry + loader

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1.5–2 days.
This is the core port. The binding instruction: **mirror `walker.ts` exactly**;
cross-check each builder against the Kotlin `TtrWalker.kt`.

**Pre-flight:**
- Stage 2.2 merged (model types exist; suites fail only on walker/loader).
- Read [`../architecture.md`](../architecture.md) §5 (the per-concern port table)
  and [`../contracts.md`](../contracts.md) §2.1 (loader behaviour).
- Open `packages/parser/src/walker.ts` + `tag-registry.ts` and Kotlin
  `walker/{TtrWalker,Dedent,TagRegistry}.kt`.

**ANTLR Python runtime cheat-sheet** (used by the walker/loader):
```python
from antlr4 import InputStream, CommonTokenStream
from antlr4.error.ErrorListener import ErrorListener
# token: tok.line (1-indexed), tok.column (0-indexed charPositionInLine),
#        tok.start / tok.stop (0-indexed char offsets, INCLUSIVE), tok.text
# rule context: ctx.start (first Token), ctx.stop (last Token)
# SourceLocation:
#   line=ctx.start.line, column=ctx.start.column,
#   end_line=ctx.stop.line,
#   end_column=ctx.stop.column + len(ctx.stop.text),   # span invariant (contracts §2.4)
#   offset_start=ctx.start.start, offset_end=ctx.stop.stop + 1   # stop is inclusive
```

**Tasks** (check each immediately after completion):

- [ ] **2.3.1 — `dedent.py`.** Implement the 3-step dedent (contracts §2.9):
      drop the leading newline after `"""`; longest common whitespace prefix over
      non-blank lines; strip it; blank lines → empty. **Do not** just call
      `textwrap.dedent` — steps 1 and 3 differ. Make `test_dedent.py` green first.

- [ ] **2.3.2 — `tag_registry.py`.** Port `tag-registry.ts`: map a block tag
      (`sql`, `postgres`, …) → `(LanguageKind, dialect | None)`. Make
      `test_tagged_block.py`'s tag-resolution assertions reachable.

- [ ] **2.3.3 — `walker.py` skeleton + `make_source_location`.** Implement the
      `SourceLocation` helper exactly per the cheat-sheet (the span invariant is
      load-bearing — `test_source_location.py` pins it). Add the top-down entry
      `walk_document(ctx) -> tuple[Document-ish]` returning schema directive,
      package, imports, definitions, warnings.

- [ ] **2.3.4 — `PropertyValue` builders.** Port `visitValue`: string / triple
      string / number (`float`) / bool / null / id (with `parts` split on `.`) /
      list / object / function-call / tagged-block. `IdValue` builds a `Reference`
      carrying the **reference token's own span** (contracts §2.7). Make
      `test_id_value_parts.py` green.

- [ ] **2.3.5 — The 16 def builders.** One per `def` kind, reading the property
      list into the model fields from contracts §2.5. Apply the v2.0.0 fix
      (`searchable` only inside `search`) and the v2.2 constructs (`drill_map`,
      entity `roles`, `display_label`, `value_labels`). Mirror the TS field
      handling for localised strings (`{ cs: "...", en: "..." }`) and search
      blocks. Cross-check each against `TtrWalker.kt`.

- [ ] **2.3.6 — Mapping walker (v2.1).** Port the inline-mapping handling into
      `MappingProperty`/`TargetValue`/`MappingColumn*`. Make
      `test_inline_mappings.py` and `test_drill_map.py` green.

- [ ] **2.3.7 — `loader.py`.** A `CollectingErrorListener(ErrorListener)` whose
      `syntaxError(self, recognizer, offendingSymbol, line, column, msg, e)`
      appends a `ParseError` (line as-is = 1-indexed; **column + 1** for 1-indexed
      display per contracts §2.3). Wire `parse_string`: build lexer+parser, remove
      the default listeners, add the collector to **both**, call `parser.document()`,
      then walk. On any error → `definitions=()` (no partial trees). `parse_file`
      reads UTF-8 and sets `source_file`. `parse_directory` walks `*.ttr`,
      **excludes** `*.ttrg`, skips `.modeler`/`node_modules`/`.git`. Re-export the
      three from `__init__.py`.

- [ ] **2.3.8 — Green the whole parser suite.** All of stage 2.1's suites pass.
      Run a real `samples/` model through `parse_file` and spot-check the AST.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
pytest -q                      # all parser suites green
mypy --strict src ; ruff check .
python -c "from ttr_parser import parse_file; r=parse_file('../../../samples/<pick>.ttr'); print(r.ok, len(r.definitions))"
```

**Stage DoD:**
- All eight tasks checked; every stage-2.1 suite green.
- `mypy --strict` + `ruff` clean (excluding `_generated/`).
- A real `samples/` model parses with `ok is True` and an AST that matches a
  manual read of the file.
