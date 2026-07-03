# Feature: Default (root) package name

Give the **default package** a configurable name via `modeler.toml`, so files at the
project root that carry no `package` declaration land in a *named* package instead of
the nameless empty default. This removes the awkwardness of qualifying root-package
defs (ambiguous suffix-only FQNs, no wildcard import) described in the design notes.

## Status

Planning. Artefacts in this folder:

| Artefact | File |
|---|---|
| Architecture (solution shape, data flow, components) | [`architecture.md`](architecture.md) |
| Contracts (manifest schema, interfaces, validation) | [`contracts.md`](contracts.md) |
| Phased implementation plan (deliverables, DoD, pre-flight) | [`plan.md`](plan.md) |

Task lists are **not** generated yet — they are the next step after this plan is approved
(see the planning skill: architecture + contracts + plan must exist first).

## Decisions (locked with the user)

- **D1 — Root package only.** The configured name applies *only* to files at the project
  root that have **no** `package` declaration. Files in subdirectories keep their current
  behaviour (path-inferred / declared). Example: with `package = "df"`, `model-ttr/root.ttr`
  (no decl) → `package df`; `model-ttr/artikl/db.ttr` → `artikl` (unchanged).
- **D2 — Explicit declarations are absolute.** A `package X` declaration in a file always
  means exactly `X` and overrides the configured name. The configured name only *fills in*
  where there is no declaration. ai-platform's existing `package artikl` lines are untouched.

## Two notions of "package" (the key correction)

Verified against the code — there are **two decoupled package notions**, and the earlier "completion
hint only" claim was wrong:

- **Semantic / qname layer (authoritative for resolution):** declaration-only — `ast.packageDecl?.name ?? ''`
  at every qname site. Path does **not** affect qnames in modeler. (`semantics/symbol-table.ts:45`;
  `ProjectSymbolTable.upsertDocument`'s 5th arg is currently **ignored** — the seam to activate.)
- **Lint / path-inference layer (advisory):** `@modeler/lint`'s `missing-package-declaration` (info,
  with a safe autofix that inserts `package <inferred>`) and `package-declaration-mismatch` (error)
  drive the *"this file is in package 'ucetnictvi'"* message you saw — they **nudge** you to declare
  the path-implied package; they don't set the qname.

The TS feature is mostly: *introduce one `effectivePackageName()` helper, compute it once at ingestion,
route every qname site through it* (no-op when unset). Sharp edge: keep `stock://` files nameless so
the cnc auto-import keeps working.

## Cross-repo scope (ai-platform)

Per the user's decision, the feature spans **both repos** so runtime resolution matches the editor:

- **modeler TS** — Phases 1–4.
- **modeler Kotlin twin (`ttr-semantics`)** — Phase 5: `Manifest.kt` + `effectivePackageName`, pinned
  by the conformance harness.
- **ai-platform (`infra/metadata`)** — Phase 6: its loader currently computes packages **from path**
  and enforces mismatch, but **does not read `modeler.toml`** and **exempts root files**. The change:
  read the project file and give root-level files the configured name. See `architecture.md` §2c/§6
  and `plan.md` Phase 6.
