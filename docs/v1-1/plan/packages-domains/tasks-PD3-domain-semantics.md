# PD3 — Domain semantics (`DomainTable`, recursive closure, diagnostics)

**Goal:** resolve domains — build a project-wide `DomainTable`, compute each domain's **recursive** package closure and entity set, enforce `.ttrd` file-kind, and emit the domain diagnostics. This is where the "load is recursive, import is not" rule physically lives.

**Reads:** [contracts §13.2–§13.3](../../design/v1-1-contracts.md#132-diagnostic-additions-extend-6), [design §14.3–§14.4](../../design/v1.1-packages-and-graphs.md#143-reference-recursion-load-vs-import-b20), [architecture §4](architecture.md). The shipped `B3-resolver.md`/`B4-diagnostics.md` show the resolver + validator wiring pattern.
**Blocked by:** PD1 (derivation/canonical qnames), PD2 (`DomainBlock` AST).
**Blocks:** PD4 (artifact reads `DomainTable`).
**Estimated time:** 3–4 days.

## Tests-first

- [ ] `packages/semantics/src/__tests__/domain-table.test.ts`:
  - **Recursive closure (the headline test).** Fixture project with packages `a`, `a.b`, `a.b.c`. `domain D { packages: [a] }` → `resolvedPackages` = `['a','a.b','a.b.c']` (canonical/prefixed). Assert all three present.
  - **Contrast with import non-recursion.** In the same fixture, a `.ttr` with `import a.*` exposes only `a`'s top-level defs, NOT `a.b`'s. Assert both behaviours in one test so the distinction is locked.
  - `entities:` member resolves via the standard resolver; `resolvedEntities` contains its canonical qname.
  - root elision: with `root="cz.dfpartner"`, `domain D { packages: [a] }` resolves `a` to `cz.dfpartner.a` and its closure.
- [ ] `packages/semantics/src/__tests__/domain-diagnostics.test.ts`:
  - unresolved member → `ttr/domain-member-not-found` (Warning).
  - empty domain → `ttr/domain-empty` (Warning).
  - two `.ttrd` files declaring `domain accounting` → `ttr/duplicate-domain` (Error).
  - `entities: [a.er.entity.x]` where package `a` is already a recursive `packages:` member → `ttr/domain-redundant-member` (Info).
  - `.ttrd` with no `domain` block, and a `domain` block inside a `.ttr` → `ttr/wrong-file-kind` (Error) for both.
- [ ] `tests/integration/domains-lsp.test.ts` — open a `.ttrd` in the LSP harness; assert go-to-def on a `packages:` member jumps to the package's files and find-refs works (free reuse of existing infra; smoke-level).

## Library reference

No external library. Reuse: the `ProjectSymbolTable` (`listPackages()`, `getByPackage()`, `get()` — contracts §3) for closure computation; the existing resolver for `entities:` members; the validator-registration pattern from `B4-diagnostics.md`. Recursion = "every known package whose canonical name equals `X` or starts with `X.`".

## Implementation tasks

- [ ] **PD3.1 — `DomainTable` module.** New `packages/semantics/src/domain-table.ts`. `interface ResolvedDomain { name; resolvedPackages: string[]; resolvedEntities: string[]; source }`. `class DomainTableBuilder` takes the `ProjectSymbolTable` + the set of `DomainBlock`s; `build()` returns `Map<name, ResolvedDomain>`.
- [ ] **PD3.2 — Recursive package closure.** For each `packages:` member `X`, closure = every package in `ProjectSymbolTable.listPackages()` whose canonical name `=== X` or `startsWith(X + ".")`. Normalise `X` through the root-elision rule (PD1.4) first. Sort results. **This is the only recursive prefix-match in the codebase** — comment it as such and reference design §14.3.
- [ ] **PD3.3 — Entity members.** Resolve each `entities:` member via the standard resolver to a canonical qname; collect into `resolvedEntities`. Unresolved → flag for PD3.5.
- [ ] **PD3.4 — File-kind enforcement.** Extend the existing `ttr/wrong-file-kind` check (v1.1.C1): a `.ttrd` must contain exactly one `domain` block and no `graph`/`def`; a `domain` block must not appear in `.ttr`/`.ttrg`. Mutual exclusivity of `domain`/`graph`/`definitions` on `Document`.
- [ ] **PD3.5 — Domain diagnostics.** Wire `ttr/domain-member-not-found`, `ttr/domain-empty`, `ttr/duplicate-domain`, `ttr/domain-redundant-member` into the validator with the severities in contracts §13.2. `duplicate-domain` is cross-file (like `id-unique` upstream) — detect during the project-level pass.
- [ ] **PD3.6 — Expose domains to the LSP.** Add `domains: DomainInfo[]` to `modeler/getProjectInfo` (name, member counts, resolved package count) so the Designer/CLI can list them. Keep the shape minimal; the full artifact is PD4.

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/integration-tests test -- domains
pnpm -r build && pnpm -r typecheck && pnpm -r lint
```

All exit 0. The recursive-closure-vs-import-non-recursion contrast test passes — this is the gate proving B20.

## DONE when

- [ ] Every checkbox ticked.
- [ ] `domain D { packages: [a] }` resolves the full `a.*` subtree; `import a.*` still resolves only `a`'s top level. Both asserted in one test.
- [ ] All five domain diagnostics fire on their fixtures with the contracted severities.
- [ ] `.ttrd` file-kind is enforced; `domain`/`graph`/`def` are mutually exclusive per document.
- [ ] `getProjectInfo` lists domains.
