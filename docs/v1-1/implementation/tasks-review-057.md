# Tasks — review-057 (Section I1)

> **STATUS (2026-05-25): all closed — I1 works end-to-end.** Created `tests/integration/src/rename.test.ts` (4 cases, green). Fixed G1–G5, G7, three further bugs the test surfaced (def-name span, qualified-ref collapse, named-import rewrite), and cleared `edit` lint. G6 left as a noted deviation. See the "Resolution" section of [`review-057.md`](review-057.md). Gate: edit 60 · lsp 91 · integration 92(+1 skip) · vscode-ext 24 · typecheck 8/8 · lint clean.

Findings in [`review-057.md`](review-057.md). I created the integration test you were blocked on — **`tests/integration/src/rename.test.ts`** (3 cases). It currently fails 3/3; those failures are the bugs below. **The task is to make that file pass** (plus the Med/Low cleanups). Run it with `pnpm --filter @modeler/integration-tests test -- rename`.

Fix the three High bugs first — they're what break rename end-to-end.

---

## G1 [High] — Make `qnameOf` package-aware

Symbol-table keys are package-qualified, but `qnameOf` omits the package, so `projectSymbols.get(...)` misses and `prepareRename` returns null.

- [ ] **G1.1** In `server.ts:165` `qnameOf`, prepend the file's package. Replace the two `return` lines so the package segment is included:
  ```ts
  function qnameOf(def: Definition, ast: Document, enclosing?: Definition): string {
    const pkg = ast.packageDecl?.name ?? '';
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const tail = enclosing ? [enclosing.name, def.name] : [def.name];
    return [pkg, schemaCode, namespace, ...tail].filter((s) => s !== '').join('.');
  }
  ```
- [ ] **G1.2** Verify `prepareRename` on an entity now returns `{ range, placeholder }` (the first integration case).

## G2 [High] — Only do a package rename when the cursor is on the `package` declaration

`onRenameRequest` (`server.ts:725`) takes the package branch whenever the file has a `package` decl, ignoring the cursor.

- [ ] **G2.1** Guard the package branch on the cursor line. Change `if (ast.packageDecl) {` to also require the position to be on the package declaration:
  ```ts
  if (ast.packageDecl && params.position.line === ast.packageDecl.source.line - 1) {
  ```
  (Optionally also require the character to fall within the name span.) When the guard is false, fall through to the symbol-rename path below.
- [ ] **G2.2** Verify renaming an entity now edits the def + cross-reference + `.ttrg`, and does **not** touch the `package` line (second integration case).

## G3 [High] — Keep the `package` keyword in the package-decl edit

`rename-package.ts:104-106` replaces `package <name>` with just `<newName>`, dropping the keyword and corrupting the file.

- [ ] **G3.1** Edit only the name span. The replacement range must start *after* `package `:
  ```ts
  const nameStartOffset = idx + before.length + 'package '.length;
  const startPos = offsetToPosition(content, nameStartOffset);
  const endPos = offsetToPosition(content, nameStartOffset + oldPackageName.length);
  edits.push(buildTextEdit(uri, startPos, endPos, newPackageName));
  ```
- [ ] **G3.2** Strengthen the unit test in `rename-package.test.ts` ("updates package declaration …") to assert the applied result **contains `package billing.invoicing_v2`** (with the keyword), not just the bare name.
- [ ] **G3.3** Verify the third integration case (rename-package keeps `package ` and the file parses).

## G4 [Med] — Un-skip the idempotent rename-symbol test

- [ ] **G4.1** Remove `.skip` from `rename-symbol.test.ts:226` and make it pass: applying the rename, then re-running `buildRenameSymbolEdit` with the new name as `oldQname`/`newBareName`, yields `{ documentChanges: [] }`. If it doesn't, fix the early-return in `buildRenameSymbolEdit` (the `currentText === newBareName || currentText === newQname` check).

## G5 [Med] — Return a real error on invalid / colliding new names (I1.5)

- [ ] **G5.1** In `onRenameRequest`, when `params.newName` is not a legal identifier, or `conflictCheck.length > 0`, throw an LSP `ResponseError` with code `lsp.ErrorCodes.InvalidParams` (or `InvalidRequest`) and a clear message — don't return `{ documentChanges: [] }` (which silently no-ops). VS Code surfaces the message as a refusal dialog.
- [ ] **G5.2** Add a test (unit or integration) asserting a colliding rename rejects with an error rather than an empty edit.

---

## Low

- [ ] **G6** — Decide on I1.6: either note in the PR that `.ttrg` propagation lives in `rename-symbol.ts` (not the reference index) by design, or extend `reference-index.ts` to index `.ttrg` `objects` as the plan specified.
- [ ] **G7** — Fix or guard the child-qname branch in `rename-symbol.ts:151` (the `partQname.startsWith(oldQname + '.')` case computes the wrong prefix). A `.ttrg` object that is a child of the renamed symbol would be mangled.

---

## Done when

- [ ] `pnpm --filter @modeler/integration-tests test -- rename` → **3/3 green**.
- [ ] The idempotent rename-symbol test is un-skipped and green; the package-decl unit test asserts the keyword is kept.
- [ ] A colliding/invalid rename returns an LSP error, not an empty edit.
- [ ] `pnpm --filter @modeler/edit test && pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test && pnpm -r typecheck` all green.
