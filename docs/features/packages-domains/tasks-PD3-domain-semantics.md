# PD3 — Domain semantics (`DomainTable`, recursive closure, diagnostics)

**Goal:** resolve domains — build a project-wide `DomainTable`, compute each domain's **recursive** package closure and entity set, enforce `.ttrd` file-kind, and emit the domain diagnostics. This is where the "load is recursive, import is not" rule physically lives.

**Reads:** [contracts §13.2–§13.3](../../design/v1-1-contracts.md#132-diagnostic-additions-extend-6), [design §14.3–§14.4](../../design/v1.1-packages-and-graphs.md#143-reference-recursion-load-vs-import-b20), [architecture §4](architecture.md). The shipped `B3-resolver.md`/`B4-diagnostics.md` show the resolver + validator wiring pattern.
**Blocked by:** PD1 (derivation/canonical qnames), PD2 (`DomainBlock` AST).
**Blocks:** PD4 (artifact reads `DomainTable`).
**Estimated time:** 3–4 days.

## Tests-first

- [x] `packages/semantics/src/__tests__/domain-table.test.ts`:
  - **Recursive closure (headline).** packages `a`, `a.b`, `a.b.c`; `domain D { packages: [a] }` → `['a','a.b','a.b.c']`.
  - **Contrast with import non-recursion.** Same fixture: `import a.*` resolves `topA` via wildcard but never exposes `inB` (a.b) through the wildcard step; domain membership over the same project IS recursive. Both asserted together.
  - `entities:` member resolves to its canonical qname.
  - root elision: `root="cz.dfpartner"`, `domain D { packages: [a] }` → `['cz.dfpartner.a','cz.dfpartner.a.b']`.
- [x] `packages/lint/src/__tests__/domain-diagnostics.test.ts` — **placed in `@modeler/lint`** (the validator lives there, like the package rules; the task's `packages/semantics` path is not where the validators are):
  - unresolved member → `ttr/domain-member-not-found` (Warning).
  - empty domain → `ttr/domain-empty` (Warning).
  - two `.ttrd` files declaring `domain accounting` → `ttr/duplicate-domain` (Error).
  - `entities: [a.er.entity.x]` covered by a recursive `packages:` member → `ttr/domain-redundant-member` (Info).
  - `.ttrd` with no `domain` block, a `domain` block in a `.ttr`, and domain+def → `ttr/wrong-file-kind` (asserted via `parseString`, since it is parser-emitted).
- [x] `tests/integration/src/domains-lsp.test.ts` — open a `.ttrd`; go-to-def on a `packages:` member jumps to the package's files (recursive closure), on an `entities:` member to the entity def; `getProjectInfo` lists the domain with its closure size. (find-refs on domain members deferred — not in DONE criteria.)

## Library reference

No external library. Reuse: the `ProjectSymbolTable` (`listPackages()`, `getByPackage()`, `get()` — contracts §3) for closure computation; the existing resolver for `entities:` members; the validator-registration pattern from `B4-diagnostics.md`. Recursion = "every known package whose canonical name equals `X` or starts with `X.`".

## Implementation tasks

- [x] **PD3.1 — `DomainTable` module.** `packages/semantics/src/domain-table.ts`: `ResolvedDomain`, `DomainEntry`, `resolveDomain`, `domainPackageClosure`, `class DomainTableBuilder` (build → `Map<name, ResolvedDomain>`, first-wins on duplicate names).
- [x] **PD3.2 — Recursive package closure.** `domainPackageClosure` = every package whose root-elided name `=== X` or `startsWith(X + ".")` (both sides root-normalised); returns canonical names, sorted. Commented as the only recursive prefix-match (§14.3); ignores the default empty package.
- [x] **PD3.3 — Entity members.** Resolved via the standard resolver to canonical qnames; unresolved ones drop out of `resolvedEntities` and are flagged by PD3.5.
- [x] **PD3.4 — File-kind enforcement.** `walker.ts` extended: `.ttrd` ⇔ a `domain` block and no graph/def; a `domain` block only in `.ttrd`; `domain`/`graph`/`def` mutually exclusive. All emit `ttr/wrong-file-kind`.
- [x] **PD3.5 — Domain diagnostics.** New `@modeler/lint` `domains` category + `rules/domains.ts`: `domain-empty`, `domain-member-not-found`, `domain-redundant-member` (document), `duplicate-domain` (project, cross-file). Severities per §13.2.
- [x] **PD3.6 — Expose domains to the LSP.** `getProjectInfo` gains `domains: DomainInfo[]` (name, member counts, resolved package/entity counts), built from open + disk `.ttrd` files. Bonus: go-to-def on domain members (the payoff for PD2.6's member source locations).

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/integration-tests test -- domains
pnpm -r build && pnpm -r typecheck && pnpm -r lint
```

All exit 0. The recursive-closure-vs-import-non-recursion contrast test passes — this is the gate proving B20.

## DONE when

- [x] Every checkbox ticked.
- [x] `domain D { packages: [a] }` resolves the full `a.*` subtree; `import a.*` never exposes `a.b` via the wildcard step. Both asserted in one test.
- [x] All five domain diagnostics (four lint rules + parser `wrong-file-kind`) fire on their fixtures with the contracted severities.
- [x] `.ttrd` file-kind is enforced; `domain`/`graph`/`def` are mutually exclusive per document.
- [x] `getProjectInfo` lists domains. Full repo `build`/`test`/`typecheck`/`lint` green (integration 27 files, 175 passed).
