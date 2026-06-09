# Resolver consolidation — architecture

**Status:** Design v1, 2026-06-09. Follow-up to grammar-master Phase 2. Delivers
the deferred half of Phase 2.8: ai-platform stops hand-maintaining a reference
resolver + symbol table and consumes the published
`org.tatrman.ttr.semantics.{Resolver, SymbolTable}` instead.

## Why

Phase 2.8 consolidated the **stock vocabulary** (`BuiltinStockSource` now
delegates to the published `StockLoader`). The **resolver** was deliberately
left in ai-platform because it is *not* a drop-in:

| Aspect | ai-platform `infra/metadata/resolve/*` | published `org.tatrman.ttr.semantics` |
|---|---|---|
| Identity type | proto `cz.dfpartner.plan.v1.QualifiedName` (`package`, `schemaCode` enum, `namespace`, `name`) | dotted `String` qname inside `SymbolEntry` |
| Package in identity | **No** — package is a *visibility scope* only; a def's identity is the `schemaCode.namespace.name` triple | **Yes** — qname is `[package.]schema.nsOrKind.name[.child]` |
| `nsOrKind` | always the file's `namespace` | `namespace`, or `def.kind` when the file declares no namespace |
| Stock roles | `cnc.cnc.role.fact` — `package=cnc` applied at the auto-import step | `cnc.cnc.role.fact` — doubled via `isStockCnc` on a `stock://` URI |
| Resolution result | `Resolution.Resolved(QualifiedName)` / `Resolution.Diagnostic(code, msg)` | `ResolutionResult.Resolved(SymbolEntry, viaStep)` / `Unresolved(reason, …)` |

A naive swap would change every resolved def's identity (modeler keeps the
package, ai-platform drops it) and ripple through the reconciler, the proto
model, and the export pipeline. The published resolver was also never validated
against ai-platform's resolver — the Phase 2 conformance harness only compared
modeler-TS ↔ modeler-Kotlin.

**End state:** ai-platform's `ReferenceResolver.kt` and `SymbolTable.kt` are
deleted. `ReferenceResolutionPass` keeps its orchestration, its proto identities,
its reference collection, and its import/circular diagnostics, but its symbol
table + per-reference resolution run through a thin **adapter** over the
published library. A grammar/resolution change then ships to ai-platform as an
`org.tatrman:*` version bump.

## Chosen approach — adapter over the published library

We keep ai-platform's `ReferenceResolutionPass` as the orchestrator and proto
`QualifiedName` as the downstream identity. We replace **only** the two internal
moving parts — the symbol-table build and the per-reference `resolve()` call —
with the published `SymbolTable` + `Resolver`, behind a new ai-platform class
`PublishedResolverAdapter`.

```
 LoadedFile[]  (parsed via the published TtrLoader — already consumed in 1.8)
      │
      ▼
 ReferenceResolutionPass.run()              ── UNCHANGED orchestration ──
      │   • collectReferences(file.definitions)      (ai-platform's own — keeps
      │       roles / er2db / er2cnc / relation-from-to; NOT nameAttribute)
      │   • per-file import diagnostics              (unused / duplicate /
      │       wildcard-no-match / missing-package)
      │   • detectCircularDependencies(...)
      │
      ▼
 PublishedResolverAdapter                   ── NEW (replaces SymbolTable +
      │                                            ReferenceResolver) ──
      │   build(files):
      │     org.tatrman.ttr.semantics.SymbolTable
      │       .upsertDocument(uri, file.definitions, schemaCode, namespace, pkg)   per file
      │   resolve(refPath, aiCtx): Resolution
      │     1. bare-import guard (kept from ai-platform)
      │     2. modelerResolver.resolveReference(Ref(refPath, parts), modelerCtx)
      │     3. map result → Resolution (proto QualifiedName / Diagnostic)
      │
      ▼
 Resolution.Resolved(QualifiedName)  ──►  reconciler / proto model / export
```

### The conversion: `SymbolEntry` → proto `QualifiedName`

This is the heart of the adapter. It is well-defined and mirrors ai-platform's
existing identity rules exactly:

- **package** = `"cnc"` when the reference resolved via the **auto-import** step
  (`viaStep == AutoImport`), `""` otherwise. This is precisely what ai-platform's
  `ReferenceResolver` step 5 does today (stamp `cnc` on auto-imported stock
  roles; everything else is package-less).
- **schemaCode** = `SchemaCode.valueOf(entry.schemaCode.uppercase())`.
- **namespace** = `entry.namespace` — see the modeler-side change below.
- **name** = `entry.name` for a top-level def; `"${parentName}.${entry.name}"`
  for a nested attribute/column (ai-platform keys nested defs as
  `parent.child`). `parentName` is the `name` of the entry at `entry.parent`.

Because the package is dropped for non-stock entries, a resolved user def comes
back as the same `schemaCode.namespace.name` triple ai-platform produces today —
identities are preserved.

### Context mapping

```
ai-platform ResolutionContext(packageName, imports, schemaCode, resolvedNamespace)
   ─►  modeler ResolutionContext(
           schemaCode      = aiCtx.schemaCode,
           namespace       = aiCtx.resolvedNamespace,
           imports         = aiCtx.imports,
           packageName     = aiCtx.packageName,
           enclosingQname  = null)            // lexical scope stays ai-platform's job
```

`enclosingQname` is null because ai-platform resolves `nameAttribute` /
`codeAttribute` lexically in the load pipeline (Stage 07), not in this pass — and
its `collectReferences` never feeds them here, so the published resolver is never
asked to resolve them. That keeps the reference-collection difference off the
table entirely.

### Diagnostic mapping

```
ResolutionResult.Resolved              ─► Resolution.Resolved(toProtoQName(symbol, viaStep))
ResolutionResult.Unresolved(NotFound)  ─► Resolution.Diagnostic("ttr/unimported-reference", …)
ResolutionResult.Unresolved(Ambiguous) ─► Resolution.Diagnostic("ttr/ambiguous-reference", …)
```

## Modeler-side change (additive)

Add a `namespace: String` field to `org.tatrman.ttr.semantics.SymbolEntry`.
Today the namespace is only embedded in the qname string; the adapter needs it
explicitly to build the proto triple (and deriving it from the qname is fragile
because of the `nsOrKind` rule and nested `parent.child` names). The field is
populated in `DocumentSymbols` (it already knows the namespace) — purely
additive, no behavioural change. Ships as `org.tatrman:*:0.3.0`.

No other modeler change is required: the resolver algorithm, qname construction,
and stock handling are already correct and conformance-locked.

## Safety net — differential parity harness

The migration's correctness rests on one claim: *for every reference ai-platform
produces, the published resolver yields the same proto `QualifiedName` /
diagnostic as the legacy resolver.* Both implement "the six-step chain per
Modeler spec," but with different internal representations and edge cases (FQN
ordering, the wildcard package-strip, the bare-import guard).

We prove the claim empirically before deleting anything:

- A `ResolverParitySpec` runs **both** resolvers (legacy `ReferenceResolver` and
  the new `PublishedResolverAdapter`) over a corpus of `(refPath, context)`
  cases drawn from the existing fixtures (`ResolutionIntegrationSpec`,
  `StockRoleResolutionSpec`, `MetadataServiceFixtureSpec`,
  `SearchBlockEndToEndSpec`, `Phase2_2ExpressivenessSpec`) plus targeted edge
  cases (multi-wildcard ambiguity, FQN-vs-same-name, cross-package import,
  nested attribute/column refs, stock auto-import).
- For each case it asserts the two outputs are equal (proto `QualifiedName`
  equality, or identical diagnostic code).
- The legacy resolver stays in the tree until this spec is green, then is
  deleted in the same PR that removes the parity scaffold.

This is the ai-platform analogue of the Phase 2 conformance harness — it catches
divergence the moment the adapter behaves differently from the code it replaces.

## What stays in ai-platform

- `ReferenceResolutionPass` — orchestration, `collectReferences`, import/circular
  diagnostics (proto- and `LoadedFile`-coupled).
- `DrillMapValidator` — validates `drill_map` against the assembled `Model`
  (proto `queries`/`drillMaps`) with `DRILL_MAP_*` codes; not grammar-level, not
  in the published `Validator`.
- All proto conversion, the reconciler, the export pipeline, YAML import.

## Phasing (summary; see `plan.md`)

- **Phase A — modeler:** add `SymbolEntry.namespace`; publish `0.3.0`.
- **Phase B — ai-platform:** build `PublishedResolverAdapter` + the parity
  harness; prove parity with the legacy resolver still present.
- **Phase C — ai-platform:** delete `ReferenceResolver.kt` / `SymbolTable.kt`;
  `ReferenceResolutionPass` uses the adapter only; remove the parity scaffold.
- **Phase D — optional:** fold the import/circular diagnostics into the published
  `Validator.validateImports` + `validateCircularDependencies` so even those stop
  being hand-maintained. Decide after C based on appetite.

## Risks

1. **Same-package edge cases.** Modeler same-package resolution uses the declared
   package; ai-platform's `inPackage` uses the file's `schema.namespace`. The
   parity harness is the guard; any divergence is a fixture, investigated case by
   case (the resolved target should be identical even if the *step* differs).
2. **Stock package stamping.** Driven off `viaStep == AutoImport`; verified by
   `StockRoleResolutionSpec` (positive: `cnc.cnc.role.fact`; negative: a
   non-stock bare name still errors).
3. **Nested `parent.child` names.** The conversion joins parent + child; covered
   by relation-join / er2db_attribute fixtures that reference nested defs.
4. **Bare-import guard.** Kept in the adapter ahead of the published resolver
   (the published resolver assumes the parser rejects single-segment imports).
5. **Two grammar-bump rehearsal (Phase 2 DoD 2.8.8).** After C, bump the modeler
   grammar/version, republish, switch ai-platform's version ref, and confirm the
   suite is green with no source edits — the headline promise.
