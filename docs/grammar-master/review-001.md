# Review 001 — grammar-master Phases 1 & 2 (resolver-consolidation excluded)

**Reviewed:** 2026-06-10 · branch `feat/move-aip-here` @ `991265b`
**Scope:** committed/pushed state of Phase 1 (`ttr-parser` + `ttr-writer`) and
Phase 2 (`ttr-semantics`) against `plan.md`, `contracts.md`, `architecture.md`,
and the canonical TS layer (`packages/semantics/`, `packages/parser/`). The
resolver-consolidation follow-up (`docs/grammar-master/resolver-consolidation/`)
is **out of scope** per the request.

**Verification run:** `./gradlew :packages:kotlin:{ttr-parser,ttr-writer,ttr-semantics}:test`
→ **BUILD SUCCESSFUL**, all suites green. Findings below come from comparing the
Kotlin port line-for-line against the TS source of truth, not from test failures —
the gaps are where tests/fixtures don't reach.

---

## Verdict

The port is **substantially faithful** in its core: the six-step resolution
chain, the Tarjan cycle detector, package inference, symbol-table lookups, the
validator subset, and the bundled stock vocab are all accurate mirrors of the TS
canon (several verified byte- or line-identical). The diagnostic-code enum is at
full parity (26 ↔ 26) and the stock `.ttrm` is hash-identical across trees.

The headline claim that the conformance harness **proves** "faithful mirror,"
however, is **over-stated** — and one concrete parser-model gap (H1) demonstrates
that the harness's coverage holes are not theoretical. Two High findings, all in
areas the green test suite does not exercise.

---

## High

### H1 — Kotlin parser model drops top-level `search {}` on table/view/relation (data loss) — ✅ RESOLVED 2026-06-10
**Fix:** added `search: SearchHintsValue` to Kotlin `TableDef`/`ViewDef`/`RelationDef`
+ walker paths; aligned `Validator.searchBlocksOf` to the TS def set (top-level
search on table/view/relation; dropped the spurious procedure-result-column walk);
taught `TtrRenderer` to render the three blocks (round-trip); emitted `search` for
these kinds in **both** conformance dumpers; added fixture
`31-search-on-table-relation.ttrm`. Conformance now green at 31/31 (parser + semantics),
with the fixture confirming `ttr/fuzzy-without-searchable` fires identically on a
relation. Original finding below.

`packages/kotlin/ttr-parser/src/main/kotlin/.../model/Definition.kt` —
`TableDef` (L38), `ViewDef` (L49), `RelationDef` (L144) have **no `search`
field**, and `TtrWalker.kt` never reads `searchBlockProperty()` for those three
kinds.

But the canonical grammar **allows** it — `TTR.g4:128/130/146`:
`tableProperty | viewProperty | relationProperty ... | searchBlockProperty` —
and the TS model carries `search?: SearchBlock` on all three
(`ast.ts:225,236,332`). So a legal TTR file with a top-level `search { fuzzy: true }`
on a table/view/relation is **silently dropped** by the Kotlin walker.

Consequences:
- **Parser fidelity break** (Phase 1 deliverable): Kotlin AST ≠ TS AST for valid input.
- **Downstream validator divergence** (Phase 2): `searchBlocksOf` (`Validator.kt:340-355`)
  can't see these blocks, so `ttr/fuzzy-without-searchable` fires in TS but not
  Kotlin. (Same function also walks **procedure result-columns**, which TS never
  does — a spurious-diagnostic mirror of the same root cause.)
- **The harness never caught it** because no fixture puts a search block on a
  table/view/relation — see H2. This is the smoking gun that the coverage gap has teeth.

Fix: add `search: SearchHintsValue?` to the three Kotlin defs + walker paths,
align `searchBlocksOf` to the TS def set, and **add a conformance fixture** that
exercises a table-level/relation-level search block.

### H2 — Conformance harness never exercises cross-file resolution; "faithful mirror" rests on untested paths — ✅ RESOLVED 2026-06-10
**Fix:** extended the semantics harness to multi-document scenarios. A fixture
subdirectory now bundles several `.ttrm` files loaded into one project symbol table
before resolving — `dumpSemDocs` (TS) / `SemanticsConformanceDump.dumpDocs`
(Kotlin), both auto-discovered by the run scripts/spec; single-doc output stays
byte-identical. Added `32-same-package/`, `33-named-import/`, `34-wildcard-import/`,
each with a same-named **decoy** in another package so the targeted step is
load-bearing (sensitivity-tested: stripping the import collapses resolution to
`[]` via the now-ambiguous fully-qualified fallback). Semantics diff now green at
34/34, with the three previously-uncovered resolution successes
(`Customer => sales.er.entity.Customer`) verified identical across TS and Kotlin.
Original finding below.

The semantics harness is **single-document**: `dump-sem.ts:39` and
`SemanticsConformanceDump.kt` each `upsertDocument` exactly one fixture doc (plus
stock) before resolving. Confirmed against every committed dump:

| Resolver step | Exercised? | Evidence |
|---|---|---|
| Lexical (same-file) | ✅ | `28-resolve-lexical`, `30` |
| `cnc.*` auto-import | ✅ | `29-resolve-autoimport` (`fact => cnc.cnc.role.fact`) |
| Fully-qualified (→ stock) | ✅ | `17-er2cnc_role` (`cnc.role.fact => cnc.cnc.role.fact`) |
| **Same-package siblings** | ❌ | never |
| **Named-import success** | ❌ | `23-package-import` → `resolved:[]` + `unused-import` |
| **Wildcard-import success** | ❌ | `23` → `wildcard-with-no-matches` |
| Cross-file fully-qualified | ❌ | every qualified ref in 07/11/12/13/14/26 → `resolved:[]` |

A Kotlin resolver that got named-import or wildcard expansion **completely wrong**
would still emit identical `resolved:[]` + the same diagnostic codes and pass.
These are the most port-prone paths (multi-doc symbol lookup, import-alias match,
wildcard depth) and they have **zero positive coverage**. The 6-step chain *reads*
as a faithful mirror (manually verified), but the harness does not back that claim
for half the steps.

Fix: add multi-document fixtures (the dumper needs to ingest ≥2 files) covering
same-package, named-import, and wildcard-import **success**.

---

## Medium

### M1 — Harness rigor: no pinned baseline, codes-only semantics diff, not in `ci.yml` — ✅ RESOLVED 2026-06-10 (2 of 3 parts)
**Fix:**
- **Always runs.** `conformance.yml` lost its `paths:` filter — it now runs on
  every PR to `main`/`v0`, so a resolver/walker change in any file (shared util,
  re-export) can't slip through a path-scoped skip.
- **Pinned golden baseline.** `tests/conformance/out-ts{,-sem}/` are now committed
  (un-ignored) as the golden baseline. A new `ts-dump` step regenerates them and
  `git diff --exit-code`s — so a TS-side dump change (or co-drift) fails CI until
  regenerated and committed, surfacing it in review. Added a `dump-all` script for
  one-command regen; dumps verified deterministic.
- **Codes-only semantics diff** is *not* addressed here — it's the deliberate
  §5.1 weakening (the Kotlin `Reference` carries no source span). Tightening it to
  compare positions/severity is tracked under **M2**, not M1.

Original finding below.

- `out-ts/` and `out-ts-sem/` are **gitignored** (`.gitignore:18-19`, 0 tracked)
  and regenerated fresh each CI run. Fresh-vs-fresh is the *correct* design for a
  cross-impl parity check, but it means there is **no golden snapshot**: if both
  sides misread the same spec (co-drift), or a TS-only dump-shape regression
  lands, nothing flags it. The only pinned snapshots are the two trivial
  one-def cases (`dump.test.ts`, `DumpSchemaSpec`/`model-min.json`); the semantics
  envelope shape is unpinned on both sides.
- The semantics diff compares **diagnostic codes only** — not position, severity,
  or message (per contract §5.1, by design). A validator firing the *right code
  for the wrong reason / on the wrong node* passes. Combined with H2 this makes
  the semantics check coarse.
- The cross-language diff runs **only** in `conformance.yml` (path-filtered);
  `ci.yml` runs `pnpm -r test`, which executes only the trivial `dump.test.ts`,
  not `diff`/`diff-sem`. A resolver-affecting change to a file outside the path
  filter won't trigger the diff.

### M2 — Reference source spans point at the owning def, not the reference token — ✅ RESOLVED 2026-06-10
**Fix:** `Reference` changed from a path-only `@JvmInline value class` to a
`data class(path, parts, source)` — matching the canonical TS
`Reference { path, parts, source }`. The walker now threads the reference token's
own span at all 16 construction sites (via a `makeRef(ctx)` helper); a single-arg
convenience constructor (derives `parts`, `SourceLocation.UNKNOWN`) keeps
non-parser construction ergonomic. `References.kt:refOf` now uses `ref.source`
instead of the `owner.source` fallback, so every collected reference — both
`Reference`-typed slots and `IdValue` slots — reports its own span. New
`ReferenceSourceSpec` asserts a parsed `nameAttribute` lands on the token (line 3,
exact identifier width), not the def (line 1). Contracts §2.7 + AST-NAMING updated.

**Note — diagnostic positions in the conformance dump (the §5.1 "codes-only"
weakness from M1) are *now possible* but deliberately deferred.** Adding positions
would newly assert TS↔Kotlin `SourceLocation` parity, which nothing currently
verifies (the parser dump strips source by design). That deserves its own
verification pass rather than riding along here. Tracked as a follow-up.

Original finding below.

`References.kt:95-98` (`refOf`) substitutes `owner.source` because the Kotlin
`Reference` value class (`Definition.kt:288`) carries no `source`, whereas the TS
`Reference` does (`ast.ts:101`) and `collectReferences` preserves it. Every
`Reference`-typed slot (`nameAttribute`, `codeAttribute`, er2db
`entity`/`attribute`/`relation`/`fk`, er2cnc `entity`/`role`) therefore reports
the *whole def's* span. Invisible to the harness (positions aren't compared) but a
real divergence for any consumer building diagnostics/reference-index locations
from these. Rooted in the parser model — worth a tracked decision.

### M3 — `param()` dumper reads divergent AST shapes with silent `as?` drops — ✅ RESOLVED 2026-06-10
**Fix:** rewrote `ConformanceDump.param()` to be **strict** — it no longer drops
silently. `name` is required (errors if absent or not an id); a present-but-wrong-
typed `type`/`label`/`direction` errors rather than being skipped; an unexpected
key errors. So a real walker difference in parameter shape now fails the
conformance run loudly instead of normalising to `{}`. Output for valid params is
unchanged (conformance still 31/31). Original finding below.

`ConformanceDump.kt:285-295` reads procedure parameters out of an untyped
`ObjectValue`, casting each entry (`entries["name"] as? IdValue`, …); TS
`dump.ts:264-270` reads a typed `ParameterDef`. A malformed/empty param object
emits `{}` rather than failing, so a real walker difference in parameter shape can
be normalized away. This is the one spot where the two dumpers read *different*
representations — exactly where silent normalization is most dangerous.

---

## Low — all ✅ RESOLVED 2026-06-10

- **`StockLoader.stockQnames()`** — ✅ **fixed** to return the doubled
  `cnc.cnc.role.<name>` (the form stock is actually stored/resolved under), so each
  returned qname `get()`s a stored symbol. KDoc + contract §4.7 updated; new
  `StockLoaderSpec` test asserts every returned qname resolves to a stored stock
  symbol. (Kept rather than deleted — it's in the public contract.)
- **`ObjectValue.entries: Map`** (duplicate keys collapse) — ✅ **surfaced, not
  silenced.** The full ordered-`ObjectEntry[]` model change was deemed
  disproportionate (~15 sites across model/walker/writer/dump/semantics/tests for a
  malformed-input edge case already documented as an accepted divergence in
  AST-NAMING). Instead the walker now emits a `duplicate key '<k>'` warning (the
  same idiom used for duplicate language entries / search properties), so a
  collapsed key — and any reference inside the dropped value — is no longer lost
  silently. Locked by a `TtrLoaderSpec` test.
- **SymbolTable `packageName` threading** — ✅ **documented + tested.** Kotlin's API
  takes a `List<Definition>` (no package decl attached), so unlike TS it cannot
  derive the package — the caller must pass `ParseResult.packageName`. Added a
  `@param` KDoc making that contract explicit and a `SymbolTableSpec` test locking
  that the declared package prefixes the qname (and the un-prefixed form does not
  exist).
- **Missing synthesized-symbol support** — ✅ **documented as a deliberate
  non-port.** Per plan **P2-1**, `mapping-synthesizer.ts` is scoped modeler-TS-only
  (edit-synthesizer adjacent; ai-platform doesn't need it). Added a `SymbolTable`
  KDoc stating the published Kotlin table indexes only explicit `def er2db_*`
  symbols and that inline-mapping synthesis is intentionally out of scope — so this
  is a scoping decision, not a gap to close.

---

## Confirmed faithful (verified, no action)

- **Resolver 6-step chain** — order, per-step matching, exclusion guards, ambiguity
  rule, `cnc.cnc.role.*` doubling, `resolveBareId`: line-for-line mirror of `resolver.ts`.
- **PackageGraph** — Tarjan SCC, edge dedup, BFS deps/dependents, self-edge skip: faithful.
- **PackageInference** — `inferFromUri` mirrors `inferPackageFromUri` exactly.
- **SymbolTable lookups** — `getBySuffix`/`findByLastSegment`, `getByPackage`/
  `findUnderPackage`, `get`/`lookup`, `duplicates`, insertion-order iteration: match.
- **Validator subset** — exactly the contracted four functions
  (`validateDocument`+`validateReferences`+`validateProject`+`validateImports`),
  no missing/extra whole rules; every ported rule emits the matching `DiagnosticCode`.
  (The §4.6 sketch rules — cardinality strings, target shapes, type aliases — do
  not exist in the current `validator.ts`, so their absence is correct.)
- **DiagnosticCode enum** — 26 ↔ 26 parity with `diagnostics.ts`.
- **Stock vocab** — `cnc-stock-roles.ttrm` is SHA-256-identical to TS
  `stock/cnc-roles.ttrm` (Phase 2.8 reconciliation consistent across trees).
- **Publishing** — `publish.yml` tag→module mapping correct; `ttr-semantics`
  wired into the `kotlin/v*` bundle and the dedicated `kotlin-semantics/v*` tag.

---

## DoD status (tracked, not surprises)

- **Phase 1 DoD** unchecked: stage 1.9 (retire `sync-to-ai-platform.sh` /
  `check-sync.sh`, scrub `CLAUDE.md`/architecture/README) is `☐` — sync scripts
  still present.
- **Phase 2 DoD** partial: 2.8 correctly scoped down to the stock single-source
  swap; the resolver/validator consolidation is the deferred separate effort. The
  scope note in `tasks/INDEX.md` is accurate and well-reasoned.

---

## Recommended actions (priority order)

1. **H1** — add `search` to Kotlin `TableDef`/`ViewDef`/`RelationDef` + walker,
   fix `searchBlocksOf`, and add a fixture covering table/relation-level search.
2. **H2** — add multi-document conformance fixtures for same-package, named-import,
   and wildcard-import resolution **success**.
3. **M1** — run `diff`/`diff-sem` in `ci.yml` (or make `conformance.yml` required);
   consider pinning a golden snapshot + `git diff --exit-code` regen check.
4. **M2/M3** — decide whether `Reference` should carry a source span; harden the
   `param()` dumper against silent `as?` drops.
5. **Low** — delete/fix `stockQnames()`; note the `ObjectValue` Map + packageName
   threading drift risks.
