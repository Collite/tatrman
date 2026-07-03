# Stage B — `domain` block / `.ttrd` → `def area`

Goal: replace the v2.3 top-level `domain` block (and its `.ttrd` file kind) with a normal
**`def area`** definition that lives in ordinary model files. This **deletes the `.ttrd` file-kind
concept entirely** — no `.ttra`, no "file ⇔ exactly one block" rule.

Decision basis: an "area" is model content (a grouping of packages + entities); a plain `def` is
more consistent than a file kind and frees `domain` for the MD value-set. ai-platform's agent
registry must switch from discovering `.ttrd` files to discovering `area` defs (coordinated in
Stage D).

Prereq: Stage A merged & green. TDD: B1 before B2–B5.

References (verified):
- Grammar `packages/grammar/src/TTR.g4`: `document` accepts `domainBlock` (≈ line 49); `domainBlock`
  + `domainProperty` + `domainPackagesProperty` + `domainEntitiesProperty` (≈ lines 80–92); tokens
  `DOMAIN : 'domain'`, `PACKAGES`, `ENTITIES` (≈ lines 522–524); these appear in `idPart` (≈ line 505).
- Semantics file-kind enforcement: search `packages/semantics/src` for `.ttrd` / `domain` block
  handling (`manifest.ts`, `project-symbols.ts`, `domain-table.ts`).
- `packages/migrate/src/resolve-packages.ts`: `isModelExt` includes `.ttrd` (≈ line 49); `if
  (ast.domain && file.path.endsWith('.ttrd'))` → "contributes no symbols" (≈ lines 100–102).

---

- [x] **B1 — Tests first (red).**
  - Replace/relocate the existing `.ttrd` conformance fixture(s) with `def area` in a `.ttrm` file:
    assert `def area myArea { packages: [...], entities: [...] }` parses clean and **does** register
    a resolvable symbol (areas are now defs, unlike the old symbol-less `.ttrd`).
  - Add a negative test: a bare top-level `domain` block is now a parse error.
  - Update `packages/migrate/src/__tests__/**` expectations for `resolve-packages` (areas now
    contribute a symbol; `.ttrd` is no longer a recognised extension).

- [x] **B2 — Grammar.** In `TTR.g4`:
  - Add token `AREA : 'area' ;`; add `AREA` to `idPart`.
  - Add `| AREA id areaDef` to `objectDefinition`; add `areaDef` mirroring the old `domainDef`
    body, and `areaProperty : descriptionProperty | tagsProperty | areaPackagesProperty |
    areaEntitiesProperty ;` reusing the existing `PACKAGES`/`ENTITIES` tokens.
  - **Remove** `domainBlock` from the `document` rule and delete `domainBlock` + its `domainProperty`
    productions and the `DOMAIN` token. (Keep `PACKAGES`/`ENTITIES` tokens — now used by `areaProperty`.)
  - Update the header CHANGELOG comment.

- [x] **B3 — Regenerate + AST/walker.**
  - Run both regen steps (parser prebuild, vscode-ext tm-grammar).
  - In `packages/parser/src/walker.ts` + `ast.ts`: replace the `domainBlock` AST node with an `area`
    definition node carrying `packages`/`entities`. Ensure source locations are populated (edit
    synthesizer invariant).

- [x] **B4 — Semantics + migrate.**
  - Remove the `.ttrd` file-kind rule from semantics; register `area` as a normal definition kind
    (it now contributes a symbol and is importable). Validate `packages`/`entities` references.
  - `packages/migrate/src/resolve-packages.ts`: drop `.ttrd` from `isModelExt` and delete the
    `ast.domain && endsWith('.ttrd')` branch; treat `area` defs like other defs.
  - `packages/migrate/src/index.ts`: if it emits/handles `.ttrd`, update to emit `area` defs in
    `.ttrm` (coordinate with Stage C extension).

- [x] **B5 — Migrate fixtures + docs.**
  - Convert every `.ttrd` fixture/example to a `def area { … }` block in a model file; delete the
    `.ttrd` files. (`rg --files --glob='*.ttrd'` to enumerate.)
  - Update `docs/**` and `CLAUDE.md` wording: drop the "`.ttrd` domain file kind" description; add
    "`def area`". Note the freed-up `domain` keyword is reserved for the MD model.

- [x] **B6 — Verify.**
  - `pnpm --filter @modeler/parser test && pnpm --filter @modeler/semantics test && pnpm --filter @modeler/migrate test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`
  - `rg --files --glob='*.ttrd'` → empty; `rg -n '\bdomain\b' packages/grammar/src/TTR.g4` shows
    only the reserved-for-MD note (no `domainBlock`).

- [x] **B7 — Commit.** `Section Phase0-B: replace domain block/.ttrd with def area`.
