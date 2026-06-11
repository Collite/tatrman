# Resolver consolidation — contracts

Companion to [`architecture.md`](architecture.md). Every API surface, data
shape, and conversion rule the implementation must honour. Section numbers are
referenced from the task lists.

## 1. Modeler-side change — `SymbolEntry.namespace`

Package: `org.tatrman.ttr.semantics` (artifact `org.tatrman:ttr-semantics`).

Add one field to the existing `SymbolEntry` data class (additive):

```kotlin
data class SymbolEntry(
    val qname: String,
    val kind: String,
    val name: String,
    val namespace: String,        // NEW — the file's namespace ("" when none declared)
    val source: SourceLocation,
    val documentUri: String,
    val parent: String?,
    val packageName: String,
    val schemaCode: String,
    val mappingSource: MappingSource? = null,
)
```

Population rule (in `DocumentSymbols`): `namespace` is the `namespace` passed to
`DocumentSymbols` / `upsertDocument` — the **file's** namespace, verbatim
(possibly `""`). It is the *same* value for a def and its nested children. It is
**not** the `nsOrKind` fallback: when the file declares no namespace,
`SymbolEntry.namespace == ""` even though the qname uses `def.kind` in that slot.

Constraints:
- Additive only — no other field changes, no qname-construction change, no
  resolver change.
- The Phase 2 conformance harness must stay green (the semantics dump excludes
  positions/identity-internal fields, so it is unaffected; run it to confirm).
- `SemanticsConformanceDump`, `Fixtures`, and the specs that build `SymbolEntry`
  via `upsertDocument` need no change (the field is produced internally). Any
  test that *constructs* a `SymbolEntry` literal must add `namespace = …`.

Version: ships as `org.tatrman:ttr-semantics:0.3.0` (bundle `kotlin/v0.3.0`,
re-cutting `ttr-parser`/`ttr-writer` at `0.3.0`). CHANGELOG entry required.

## 2. ai-platform — `PublishedResolverAdapter`

Package: `infra.metadata.resolve` (new file `PublishedResolverAdapter.kt`).
Replaces `SymbolTable.kt` + `ReferenceResolver.kt`.

```kotlin
import cz.dfpartner.plan.v1.QualifiedName
import cz.dfpartner.plan.v1.SchemaCode
import infra.metadata.source.LoadedFile
import org.tatrman.ttr.semantics.ResolutionContext as TtrCtx
import org.tatrman.ttr.semantics.ResolutionResult as TtrResult
import org.tatrman.ttr.semantics.ResolutionStep
import org.tatrman.ttr.semantics.Resolver as TtrResolver
import org.tatrman.ttr.semantics.SymbolEntry
import org.tatrman.ttr.semantics.SymbolTable as TtrSymbolTable

class PublishedResolverAdapter private constructor(
    private val resolver: TtrResolver,
    private val byQname: Map<String, SymbolEntry>,   // for nested parentName lookup
) {
    companion object {
        fun build(files: List<LoadedFile>): PublishedResolverAdapter {
            val table = TtrSymbolTable()
            for (f in files) {
                table.upsertDocument(
                    uri         = f.storageFile.path,
                    definitions = f.definitions,
                    schemaCode  = f.schemaCode,
                    namespace   = f.namespace,
                    packageName = f.declaredPackage ?: "",   // see §2.1
                )
            }
            val byQname = table.all().associateBy { it.qname }
            return PublishedResolverAdapter(TtrResolver(table), byQname)
        }
    }

    /** Drop-in for the legacy `ReferenceResolver.resolve(ref, ctx)`. */
    fun resolve(ref: String, ctx: ResolutionContext): Resolution {
        // (1) bare-import guard — kept from the legacy resolver: a non-wildcard
        //     import with < 3 dotted segments is illegal regardless of the ref.
        for (imp in ctx.imports) {
            if (!imp.wildcard && imp.target.split(".").size < 3) return unimported(ref)
        }
        // (2) delegate to the published resolver
        val result = resolver.resolveReference(
            TtrResolver.Ref(ref, ref.split(".")),
            TtrCtx(
                schemaCode     = ctx.schemaCode ?: "db",
                namespace      = ctx.resolvedNamespace ?: "",
                enclosingQname = null,
                imports        = ctx.imports,
                packageName    = ctx.packageName,
            ),
        )
        // (3) map back to ai-platform's proto-shaped Resolution
        return when (result) {
            is TtrResult.Resolved   -> Resolution.Resolved(toProtoQName(result.symbol, result.viaStep))
            is TtrResult.Unresolved -> when (result.reason) {
                TtrResult.Reason.Ambiguous -> ambiguous(ref)
                TtrResult.Reason.NotFound  -> unimported(ref)
            }
        }
    }

    private fun toProtoQName(e: SymbolEntry, step: ResolutionStep): QualifiedName { /* §2.2 */ }
}
```

`ResolutionContext`, `Resolution` (`Resolved`/`Diagnostic`), `unimported(ref)`,
`ambiguous(ref)` keep their **current** ai-platform definitions (move the helpers
into the adapter or a small shared file). The diagnostic messages stay
byte-identical to the legacy ones so existing assertions pass.

### 2.1 Package passed to `upsertDocument`

Pass `file.declaredPackage ?: ""`. The package is used by the published resolver
for the same-package step (correct sibling resolution) **and** appears in the
modeler qname — but it is dropped again in `toProtoQName` for non-stock entries
(§2.2), so ai-platform's package-less identity is preserved. The stock
`LoadedFile` from `BuiltinStockSource` declares `package = "cnc"` →
qname `cnc.cnc.role.fact`, which the auto-import step then matches.

### 2.2 `toProtoQName` — the conversion contract

```kotlin
private fun toProtoQName(e: SymbolEntry, step: ResolutionStep): QualifiedName {
    val pkg = if (step == ResolutionStep.AutoImport) "cnc" else ""   // mirror legacy step 5
    val protoName = e.parent?.let { "${byQname[it]?.name ?: it.substringAfterLast('.')}.${e.name}" } ?: e.name
    return QualifiedName.newBuilder()
        .setPackage(pkg)
        .setSchemaCode(runCatching { SchemaCode.valueOf(e.schemaCode.uppercase()) }
            .getOrDefault(SchemaCode.SCHEMA_CODE_UNSPECIFIED))
        .setNamespace(e.namespace)
        .setName(protoName)
        .build()
}
```

Rules, normative:

1. **package** = `"cnc"` iff `step == AutoImport`, else `""`. (Identical to legacy
   `ReferenceResolver`: package is stamped only on auto-imported stock roles.)
2. **schemaCode** = enum of `e.schemaCode` uppercased; `SCHEMA_CODE_UNSPECIFIED`
   on miss (matches legacy `buildDef`).
3. **namespace** = `e.namespace` (the new field, §1).
4. **name** = `e.name` for a top-level def; `"<parentName>.<e.name>"` for a
   nested def, where `<parentName>` is `byQname[e.parent].name` (the parent
   entry's simple name). Mirrors legacy `buildNestedDef`'s `"$parentName.$name"`.

### 2.3 `ReferenceResolutionPass` change

`ReferenceResolutionPass.run()` swaps two lines:

```kotlin
// before
val symbolTable = SymbolTable(definitions = buildDefList())
val resolver = ReferenceResolver(symbolTable)
// after
val adapter = PublishedResolverAdapter.build(files)
```

…and the per-reference call `resolver.resolve(ref.path, ctx)` becomes
`adapter.resolve(ref.path, ctx)`. `recordUsedImport` currently calls
`symbolTable.matchingWildcard(...)` / `symbolTable.byFull(...)` for unused-import
tracking; replace those with adapter helpers that delegate to the published
`SymbolTable.getByPackage(...)` / `get(...)` (see §2.4), or compute used-imports
from the resolver's `viaStep` (a `NamedImport` / `WildcardImport` result marks
that import used). `collectReferences`, `buildDefList`,
`definitionKindSchemaAndNamespace`, `detectCircularDependencies`, and all
diagnostic emission stay byte-identical.

### 2.4 Used-import tracking

Preferred: derive used-imports from resolution. Extend the adapter to return the
winning `viaStep` (or expose a `resolveDetailed` that returns
`(Resolution, ResolutionStep?)`). When `viaStep ∈ {NamedImport, WildcardImport}`,
mark the matching import index used (by suffix match on `imp.target` /
`getByPackage(imp.target)` wildcard match). This removes ai-platform's reliance on
the deleted `SymbolTable.matchingWildcard` and keeps the unused-import /
wildcard-no-match diagnostics unchanged.

## 3. Differential parity harness

New ai-platform spec `infra/metadata/src/test/.../resolve/ResolverParitySpec.kt`.

```kotlin
data class ParityCase(
    val name: String,
    val files: List<LoadedFile>,   // built like the existing resolution specs
    val refs: List<Pair<String /*refPath*/, ResolutionContext>>,
)

// For each case, for each ref:
val legacy = ReferenceResolver(SymbolTable(buildDefList(files))).resolve(ref, ctx)
val viaPub = PublishedResolverAdapter.build(files).resolve(ref, ctx)
assertSameResolution(legacy, viaPub)   // see below
```

`assertSameResolution(a, b)` asserts:
- both are `Resolution.Resolved` with **equal** `QualifiedName` (proto equality:
  package, schemaCode, namespace, name), **or**
- both are `Resolution.Diagnostic` with the **same `code`** (message text is not
  compared).

Corpus (minimum): port the reference scenarios from `ResolutionIntegrationSpec`,
`StockRoleResolutionSpec`, `Phase2_2ExpressivenessSpec`, plus explicit edge
cases — (a) two wildcard imports exposing the same bare name → ambiguous;
(b) an entity and a table sharing a name with both wildcards imported, ref by FQN
→ resolves, not ambiguous; (c) cross-package named import; (d) nested
attribute/column FQN ref (relation join / er2db_attribute target); (e) bare stock
role → `cnc.cnc.role.<n>`; (f) bare non-stock unknown → `ttr/unimported-reference`.

The legacy `ReferenceResolver` + `SymbolTable` remain in the tree **only** to
satisfy this spec. They (and this spec) are deleted together in Phase C.

### 3.1 Agreed behavioural delta — multi-wildcard ambiguity (Phase B finding)

The parity harness surfaced exactly one divergence, in edge case (a) (two
wildcard imports exposing the same bare name). It is an **agreed, documented
delta**, not an adapter bug:

- **Legacy** reports `ttr/ambiguous-reference` — its `matchingWildcard` matches a
  wildcard import by **schema.namespace** prefix, so `import db.y.*` +
  `import er.b.*` both expose a def named `ambiguous`, and it detects the clash.
- **Adapter** reports `ttr/unimported-reference` — the published modeler-v1.1
  resolver matches wildcard imports by **package** (`SymbolTable.getByPackage`),
  and ai-platform defs carry no declared package (`packageName == ""`), so the
  wildcard step finds nothing; the step-6 unique-suffix fallback then sees two
  suffix matches → `NotFound`.

Why it is safe to accept:
- **Both reject the reference** — no def's proto identity changes; only the
  diagnostic *subcode* differs, and only for a reference that is unresolvable
  either way.
- **No real fixture is affected.** Real wildcard targets (e.g.
  `fixture-packages/relations/Order.ttr` → `import er.sales.*` → `Product`) are
  globally unique, so the adapter's step-6 fallback resolves them to the
  *identical* `QualifiedName` the legacy wildcard step produces.
- The architecture's own tolerance (Risk #1: "the resolved target should be
  identical even if the step differs") holds — here the target is identical
  (both unresolved).

The parity case carries a `diagnosticCodeMayDiffer` flag: for this one case it
asserts both resolvers still **reject** the ref, not the exact subcode. Accepting
this delta is what lets Phase C delete ai-platform's schema.namespace wildcard
logic outright instead of preserving it in the adapter.

## 4. Versioning & sequencing

| Artifact | Version | When |
|---|---|---|
| `org.tatrman:*` (parser/writer/semantics) | `0.3.0` | Phase A — adds `SymbolEntry.namespace` |
| ai-platform `tatrman-modeler` ref | `0.3.0` | Phase B |

No SNAPSHOTs (grammar-master D6). Local cross-repo iteration via
`publishToMavenLocal` + a temporary `mavenLocal()` in ai-platform
`settings.gradle.kts`, reverted before commit (same recipe used in Phase 2.8).

## 5. Out of scope

- Changing the proto `QualifiedName` shape or ai-platform's package-as-visibility
  model. The adapter preserves both.
- `DrillMapValidator` and any proto/reconciler/export code.
- Folding ai-platform's import/circular diagnostics into the published
  `Validator` — that is optional Phase D, planned separately if pursued.
