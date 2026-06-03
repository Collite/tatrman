# ai-platform Upgrade — TTR grammar v2.0.0 (packages + search-block rework)

**Status:** Task list v1, 2026-05-21. Consolidated, ai-platform-facing upgrade
plan covering **two** Modeler language changes that ship together in TTR grammar
**v2.0.0**:

1. **v1.1 packages** — `package` / `import` / `graph` constructs, the new
   six-step resolution chain, and the `.ttrg` file kind. Contract spec:
   [`v1-1/design/grammar-v1-1-changes.md`](v1-1/design/grammar-v1-1-changes.md).
2. **Search-block rework** — top-level `searchable` removed; `search { … }`
   widened to all data-bearing kinds and given `searchable` + `fuzzy`
   sub-properties. Feature spec:
   [`features/search-block/README.md`](features/search-block/README.md)
   (this supersedes the standalone
   [`features/search-block/T5-ai-platform-yaml.md`](features/search-block/T5-ai-platform-yaml.md)).

In Modeler, search-block T1–T4 and the v1.1 grammar are **already implemented**;
the canonical `packages/grammar/src/TTR.g4` is at `2.0.0` and contains **both**
deltas. That is the key fact for this upgrade: **one grammar sync carries both
features, so ai-platform regenerates its Kotlin parser once and then has two
loader workstreams** (Section A = packages, Section B = search-block).

> ⚠️ The `ai-platform` repo (`~/Dev/ai-platform`) is **not mounted** in this
> workspace. Every path and symbol named below is written against the *described*
> behaviour and the vendoring contract in `CLAUDE.md`. **Verify each one against
> the actual ai-platform code before editing.**

---

## Section 0 — Sync the grammar + regenerate the Kotlin parser (shared)

This is the single foundation step for both features. Do it once.

- [ ] **Sync the canonical grammar into ai-platform.** From this repo:
  ```bash
  packages/grammar/scripts/sync-to-ai-platform.sh ~/Dev/ai-platform
  packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform   # hashes must match
  ```
- [ ] **Confirm the synced copy carries both deltas.** Spot-check the vendored
  `TTR.g4`:
  - Packages: tokens `PACKAGE`, `IMPORT`, `GRAPH`, `OBJECTS`, `LAYOUT`, `STAR`;
    rules `packageDecl`, `importDecl`, `graphBlock`.
  - Search-block: `SEARCHABLE` / `FUZZY` appear **inside** the `searchBlock`
    body productions (`searchableProperty`, `fuzzyProperty`), and **no** kind's
    property list (`columnProperty`, `attributeProperty`, …) still references a
    top-level `searchableProperty`.
- [ ] **Bump the vendored grammar version to `2.0.0`** in both lockstep
  locations: ai-platform's copy of `packages/grammar/package.json`
  (`"version": "2.0.0"`) and the `// TTR (Tatrman) v2 grammar …` header comment
  at the top of the vendored `TTR.g4`.
- [ ] **Regenerate ai-platform's Kotlin parser** from its vendored copy (follow
  ai-platform's own generation step) and rebuild.
- [ ] **Re-run ai-platform's existing parser suite** (the ~17-case suite) against
  the v2 grammar to confirm no regressions before touching loader logic.

---

## Section A — Packages, imports & `.ttrg` (v1.1)

Spec: [`v1-1/design/grammar-v1-1-changes.md`](v1-1/design/grammar-v1-1-changes.md).
Note the **two adoption depths** — pick deliberately:

- **Minimal (don't break):** Section 0 + the stock-vocab decision (A1) + tests.
  Because of the *default (empty) package* rule, any TTR ai-platform generates
  today with **no** `package`/`import` keeps parsing and resolving exactly as
  under v1 — the resolver changes below are inert until packages are actually
  emitted.
- **Full (use packages):** also do A2 (resolver chain) and A4 (converter emits
  `package`/`import`).

### A1 — Decide the stock-vocab qname (blocking for Modeler v1.1 ship)

- [ ] **Confirm the qname for the six built-in CNC roles** (`fact`, `dimension`,
  `structural`, `master`, `transaction`, `bridge`), which are auto-imported as if
  `import cnc.*` were present (spec §4.3). Options:
  - (a) `cnc.cnc.role.fact` — strict rule, ugly doubled `cnc.`
  - **(b) `cnc.role.fact`** — Modeler's lean; matches v1 → **no migration**
  - (c) `stock.cnc.role.fact` — clean, but migrates every existing reference
- [ ] **Report ai-platform's preference back to Bora.** The chosen answer goes
  into both `grammar-v1-1-changes.md` §4.3 and the design doc §13.10 **before**
  Modeler v1.1 ships. This is the one item that touches every existing TTR file's
  stock-vocab references, so settle it early.

### A2 — Update the resolution chain (full adoption only)

- [ ] **Replace the v1 two-step resolver** (lexical → project symbol table) with
  the v1.1 **six-step** chain (spec §4):
  1. lexical scope (unchanged)
  2. same-package symbols (no import needed for siblings)
  3. named imports (full `<schema>.<ns-or-kind>.<defName>` suffix; bare-defName
     imports are illegal)
  4. wildcard imports — **non-recursive** (`import x.y.*` does not expose `x.y.z`)
  5. auto-imports (`cnc.*`, per A1)
  6. fully-qualified name (always resolves if the symbol exists)
- [ ] **Adopt the new qname shape:** `<package>.<schema>.<ns-or-kind>.<defName>[.<subDef>]`
  (spec §4.1). For default-package files the leading package segment is omitted,
  so `er.entity.artikl` still resolves to itself — preserving v1 behaviour.
- [ ] **Implement the package = directory rule** (spec §4.4): the directory
  holding `modeler.toml` is the classpath root; a file at `<root>/foo/bar/baz.ttr`
  is in package `foo.bar`. If ai-platform walks the tree to discover `.ttr`
  files, compute the package the same way; if it reads files flat, trust the
  file's `package` declaration.

### A3 — `.ttrg` files: do NOT load them

- [ ] **Filter `.ttrg` out of the metadata loader** (spec §5). `.ttrg` files are
  editor-only render artefacts — they describe how a slice is drawn, not what the
  model contains. Treat `.ttrg` as a non-loadable extension. (Only parse them if
  you specifically want to validate that a graph references real entities; not
  required.)

### A4 — YAML→TTR converter: emit packages (full adoption only)

- [ ] **If adopting packages, teach the converter to emit `package <name>`**
  (inferred from directory per A2) and the appropriate `import` statements for
  cross-package references. If staying minimal, emit neither and rely on the
  default-package rule.

### A5 — Diagnostics (optional; ai-platform decides load-blocking severity)

- [ ] **Decide which new codes the loader emits during loading** (spec §4.5).
  Recommended: at least the error-severity ones. Codes:
  `ttr/unimported-reference` (E), `ttr/ambiguous-reference` (E),
  `ttr/package-declaration-mismatch` (E), `ttr/wrong-file-kind` (E),
  `ttr/unused-import` (W), `ttr/wildcard-with-no-matches` (W),
  `ttr/duplicate-import` (W), `ttr/circular-package-dependency` (W),
  `ttr/missing-package-declaration` (Info), `ttr/graph-object-not-found` (W),
  `ttr/graph-layout-stale-node` (W).

---

## Section B — Search-block rework (former T5)

Feature spec: [`features/search-block/README.md`](features/search-block/README.md).
What changed in the grammar: the top-level `searchable` boolean is **gone**;
`search { … }` is now allowed on `table`, `column`, `view`, `entity`,
`attribute`, `relation`, `query`, `role`; and the block gained `searchable` and
`fuzzy` sub-properties. **`def column X { searchable: true }` is now a parse
error** — the flag must live inside `search { searchable: true }`.

Context: ai-platform's metadata service loads entity/attribute metadata from
YAML; in this repo's copies (`samples/yaml/entities/*.yaml`) the relevant fields
look like:
```yaml
- name: "jméno_uživatele"
  column: "JMENO_UZIV"
  type: "varchar(100)"
  searchable: true
  search_hint: "Hledání uživatele podle jména. Příklad: ..."
```
Today the loader reads `searchable` and the converter emits it as a **top-level**
TTR property. After this upgrade that's a syntax error.

- [ ] **Update the YAML→TTR converter** to fold `searchable` into a
  `search { … }` block. Locate it: `grep -rn "searchable" ~/Dev/ai-platform` and
  find the metadata service's YAML loader / TTR serializer.
  - `searchable: true` → emit `search { searchable: true }`.
  - If the row already produces other search content (`search_hint` →
    `patterns`/`descriptions`, or any existing `search` mapping), **merge** into a
    single `search { … }` block — never emit two `search` blocks for one element.
- [ ] **Update the YAML loader's reading of `searchable`.** Wherever it maps
  `searchable` onto a top-level model field, route it through the same in-block
  representation the converter now produces, so loader and converter agree on one
  shape.
- [ ] **Decide `fuzzy` provenance (scope check).** This change only *relocates*
  `searchable`; there is no YAML `fuzzy` source field today. Default: leave
  `fuzzy` unset by the converter. Only add a YAML `fuzzy:` field + mapping if the
  model owner wants it — confirm before adding.
- [ ] **Update ai-platform's loader-side `search`-block validator** if present.
  If it enumerates allowed sub-properties, add `searchable` and `fuzzy`. Mirror
  Modeler's two diagnostics only if ai-platform wants parity:
  - `fuzzy-without-searchable` (**warning**) — `fuzzy: true` while `searchable`
    is not `true`.
  - `duplicate-search-property` (**error**) — a sub-property appears more than
    once in one `search` block.
- [ ] **Update ai-platform tests/fixtures.** Any fixture asserting a top-level
  `searchable` in generated TTR must move it inside `search { … }`. Run the
  metadata service test suite.

---

## Consolidated verification

- [ ] `check-sync.sh` reports matching hashes (vendored grammar == canonical v2.0.0).
- [ ] ai-platform builds with the regenerated Kotlin parser; the existing parser
  suite is green against the v2 grammar.
- [ ] **Search-block round-trip:** a YAML entity with `searchable: true` produces
  TTR containing `search { searchable: true }` (merged with any other search
  content), and that TTR parses cleanly under v2.
- [ ] **Packages, per chosen depth:**
  - Minimal: existing generated TTR (no `package`/`import`) still parses and
    resolves identically; stock-vocab refs resolve under the A1 decision.
  - Full: same-package, named-import, wildcard (incl. non-recursion), and
    fully-qualified references all resolve per spec §6; package mismatch /
    unimported / ambiguous cases fire the expected diagnostics.
- [ ] ai-platform metadata test suite is green.

---

## Coordination & merge ordering

- **Modeler ships v1.1 independently of ai-platform's grammar adoption**
  (decision B12). There is a coordination window during which Modeler emits v2
  syntax while ai-platform still parses v1. Modeler's grammar-sync CI is set to
  **warn on drift** during the window and returns to **block on drift** once
  ai-platform is on v2.0.0 — tell Bora when that lands.
- **Search-block ordering:** Section B must land in ai-platform **before** any
  ai-platform metadata generated with the old top-level `searchable` is
  re-validated against the v2 grammar, otherwise that metadata fails to parse.
- **Blocking item for Modeler:** the A1 stock-vocab qname decision needs to be
  fed back before Modeler v1.1.0 ships.
