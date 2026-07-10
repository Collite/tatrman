# Review 026 — Section B1 (AST extension)

**Branch:** `v1-1` (commit `63bdb61` for Section A + uncommitted working-tree changes for B1).
**Scope reviewed:** the AST extension described in [`docs/v1-1/plan/tasks/B1-ast-extension.md`](../plan/tasks/B1-ast-extension.md) and the type/walker contracts in [`docs/v1-1/design/v1-1-contracts.md §2`](../design/v1-1-contracts.md#2-ast-additions).
**Files in scope:**

- `packages/parser/src/ast.ts` (modified — added `PackageDecl`, `ImportDecl`, `GraphBlock`, `GraphLayout`; extended `Document`)
- `packages/parser/src/walker.ts` (modified — added `walkPackageDecl`, `walkImportDecl`, `walkGraphBlock`, `walkGraphLayout`, `walkViewport`; emitted `WrongFileKind`)
- `packages/parser/src/diagnostics.ts` (modified — added `WrongFileKind = 'ttr/wrong-file-kind'`)
- `packages/parser/src/__tests__/ast-v1.1.test.ts` (new, 11 cases)

**Verification runs:**

| Command                                         | Result               |
| ----------------------------------------------- | -------------------- |
| `pnpm --filter @modeler/parser test`            | ✅ 52/52 (37 v1 + 4 grammar-v2 + 11 new ast-v1.1)         |
| `pnpm --filter @modeler/integration-tests test` | ✅ 29/29             |
| `pnpm -r typecheck`                             | ✅                   |

The mini-plan's Done-when criteria are all met: the four new types are exported, the walker populates them, every v1 sample still parses with `packageDecl === undefined` and `imports === []` (verified by the unchanged `parser.test.ts` suite), `ttr/wrong-file-kind` fires on the obvious bad case, and no semantics work has bled into this phase.

---

## Verdict

**B1 is ready** to flip to `[x]`, with three follow-ups worth doing before C1/E4 actually round-trips graph layouts. None block B2 — they bite later, in the layout/serialization path. Treat this review as "merge-blocker-free; clean up the long-tail bugs while the context is warm."

Specifically:

- All four AST node interfaces match contracts §2 byte-for-byte.
- `Document.imports` is non-optional and always populated (empty array when there's no `import` line) — that invariant is asserted by the test suite.
- `walkPackageDecl` / `walkImportDecl` produce the dotted name, the parts array, and the correct `wildcard` boolean for the trailing `.*`. Source-location math agrees with the ANTLR convention spelled out in `CLAUDE.md` (`endColumn = stopToken.column + stopTokenLength`), and the test verifies a multi-token span (`'package billing.invoicing'.length` = 25) — which is exactly the formula that bit Section D last time.
- `walkGraphBlock` populates `name`, `schema`, `objects`, `description`, `tags`, `layout`.
- `WrongFileKind` is emitted with `severity: 'error'` and the offending `graphBlock` range as source.

The follow-ups are below.

---

## Findings

### 1. (Hygiene) `STATUS.md` was not flipped to "under review"

`STATUS.md` still shows `[ ] B1 ast extension` (no `under review` annotation). Project convention (per the top of `STATUS.md`) is:

> Pick a next task list, mark with "in progress" in the list below, and work on it.
> Once dev complete, mark it "under review" in the list below, and notify the user to review it.

This is process-only — not a code issue — but it makes it harder to tell at a glance which phase is in flight. Fix on commit.

### 2. (Real bug — bites C1/E4) `walkGraphLayout` cannot read `bendPoints`

`packages/parser/src/walker.ts:357–370` tries to recover the `bendPoints: [number, number][]` field of `GraphLayout.edges` like this:

```ts
const bxEntry = item.object_()!.propertyList()?.propertyEntry()
                  .find((e) => e.key().getText() === '0');
const byEntry = item.object_()!.propertyList()?.propertyEntry()
                  .find((e) => e.key().getText() === '1');
```

This branch can never fire. TTR's `key` rule resolves to an `id`, and `id` is built from `idPart` alternatives — none of which include `NUMBER_LITERAL`. A bare `0`/`1` in the source is a number, not an identifier, so `propertyEntry` will never have a key whose `getText()` is `'0'` or `'1'`. As a result, `bendPoints` is always `undefined` even when the source contains a valid list-shaped serialization.

The natural source serialization for `[number, number][]` is a list of two-number lists, e.g. `bendPoints: [[10, 20], [30, 40]]`. The walker should iterate `list().value()` and unwrap each item as a `list()` with two `NUMBER_LITERAL` children. The current object-with-`'0'`/`'1'` shape isn't even valid TTR.

No existing test exercises this path. The `graph with layout → layout node positions parsed` case only covers `nodes`, not `edges`. So the bug is silently latent — exactly the kind of thing C1's `getGraph` round-trip will surface.

**Suggested fix:** rewrite the edges branch to expect `bendPoints` as a `list` whose items are themselves two-element `list`s of `NUMBER_LITERAL`. Add a test case that constructs `edges: { e1: { bendPoints: [[10, 20]] } }` and asserts the parsed result matches the AST shape.

### 3. (Real bug — unsound type cast) `displayMode` walker accepts arbitrary identifiers

`packages/parser/src/walker.ts:401`:

```ts
if (idCtx) displayMode = idCtx.getText() as typeof displayMode;
```

The TS union for `displayMode` is `'just-names' | 'with-types' | 'with-constraints'` (kebab-case, from contracts §2 / §11.2). But TTR identifier syntax doesn't permit hyphens — the lexer's `IDENT` rule is `[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*`. There is no way to write `with-types` as a bare `id` in TTR source. Source files can only emit camelCase (`withTypes`) or snake_case (`with_types`).

So:

- Either the union is wrong and should be `'justNames' | 'withTypes' | 'withConstraints'` (or `string` until B4 narrows it),
- Or the contract is internally inconsistent and `GraphLayout` round-tripping through `.ttrg` files will silently corrupt the value,
- Or the walker needs to translate camelCase IDs into the kebab-case union members.

The `ast-v1.1.test.ts` "graph with viewport" case (lines 133–146) hides this by asserting `displayMode === 'withTypes'`. That string is *not* a member of the type union — only the unsound `as typeof displayMode` cast lets it compile. The test passes but the AST is now structurally inconsistent with its declared type.

**Suggested fix (pick one):**

1. Change the AST type to `displayMode: 'just-names' | 'with-types' | 'with-constraints'` and translate from a TTR-legal id form (camelCase or snake_case) at walk time. Most invasive but keeps the wire format clean.
2. Loosen the AST type to `displayMode: string`. Cheap, defers validation to B4, but then v1's `LayoutFile` consumers downstream need to widen too.
3. Change the AST type to `displayMode: 'justNames' | 'withTypes' | 'withConstraints'` (camelCase). Keeps the cast sound. Requires a contracts §2 / §11.2 amendment — open a PR against `v1-1-contracts.md` first per the contract-amendment discipline.

Whichever you pick, also update the test case to match.

### 4. (Minor) `graphBlock` description strips quotes by hand

`packages/parser/src/walker.ts:308`:

```ts
description = gp.descriptionProperty()!.stringLiteralForm()!.getText().slice(1, -1);
```

Compare to every other walker for the same property (e.g. `walkModelDef` line 571):

```ts
description = walkStringLiteralForm(p.descriptionProperty()!.stringLiteralForm()!, file);
```

The shortcut at line 308 silently misbehaves on two inputs:

- A description containing escape sequences (`description: "say \"hi\""`) — the shortcut leaves the `\"` literally in the value; `walkStringLiteralForm` strips it.
- A triple-quoted description (`description: """..."""`) — the shortcut strips one character from each end, mangling the content; `walkStringLiteralForm` handles the `"""…"""` case explicitly and runs `dedent`.

Note that the AST shape here differs: every other `description` is `StringValue | TripleStringValue`, but `GraphBlock.description` is plain `string` per contracts §2. That's intentional (the graph-block description is treated as plain text downstream), but the *parsing* should still honor escape sequences and the triple-string form. The shortest correct fix is:

```ts
if (gp.descriptionProperty()) {
  const parsed = walkStringLiteralForm(gp.descriptionProperty()!.stringLiteralForm()!, file);
  description = parsed.value;
}
```

Add a test case with an escape sequence to lock this in.

### 5. (Informational) Wrong-file-kind only fires on `graph + def`, not the other half of the rule

Contracts §2 (paragraph at the end of §2):

> A document with both `graph` and non-empty `definitions` is a parse-time recoverable error (`ttr/wrong-file-kind`, Error).

Contracts §6 expands this:

> `ttr/wrong-file-kind` (Error, parser) — `.ttrg` with no `graph` block, or `.ttr` with one.

So the diagnostic has **two** triggers:

1. **`.ttr` file with a `graph` block.** (Or `.ttrg` with definitions — same shape.) The walker covers this via the `graphCtx && definitions.length > 0` branch.
2. **`.ttrg` file with no `graph` block.** Not covered — and can't be, here, because `walker.ts` has no idea what the file extension is.

The mini-plan's B1.5 only spells out (1), and the test only covers (1). That's fine for B1's scope; (2) needs the file-extension context that lives one layer up (the LSP server or the host file-reading code), and it's reasonable for that to come with C1 ("ttrg parsing").

**Action:** leave a `// TODO(C1): also emit WrongFileKind when a .ttrg has no graph block` comment next to the existing emit site so future-you / future-reviewer doesn't think the diagnostic is finished here.

### 6. (Informational, non-deviation) AST diff matches contracts §2 byte-for-byte

For the record:

| Type           | Contract §2 location | `ast.ts` location | Match  |
| -------------- | -------------------- | ----------------- | ------ |
| `PackageDecl`  | lines 116–123        | lines 386–391     | ✅      |
| `ImportDecl`   | lines 125–134        | lines 393–399     | ✅      |
| `GraphBlock`   | lines 135–149        | lines 412–421     | ✅      |
| `GraphLayout`  | lines 151–162        | lines 401–410     | ✅      |
| `Document`     | lines 167–174        | lines 423–430     | ✅      |

Field order, optionality flags, and union members all agree. The doc-comment paragraphs from contracts §2 ("Dotted name as written…", "Required, non-empty…") didn't make it into `ast.ts` — that's a style choice and the contracts doc is the canonical reference anyway.

### 7. (Informational) Order of error-emit vs definitions walk

`walkDocument` walks every `definition` before checking the `wrong-file-kind` condition. Means we do the work of constructing all `Definition` AST nodes even when we already know the file is malformed. Not a correctness issue — definitions parse fine, they're just orphaned next to a `graph` block — and emitting one diagnostic per file is cheap. Acceptable as-is.

---

## What "done" looks like after the follow-ups

B1 is fully closed when:

1. Findings 2, 3, 4 are addressed (with appropriate test coverage for each).
2. `STATUS.md` reflects `[x] B1 ast extension` after a commit.
3. A `TODO(C1)` comment is left at the `WrongFileKind` emit site so the second trigger isn't forgotten.

All other B1 task-list boxes can be considered ticked. B2 is unblocked **today** — none of the follow-ups gate symbol-table work — but please don't start B2 in the same commit; keep the B1 cleanup commit small and reviewable.
