# Phase 3.G — Static-site deploy

**Goal:** the Designer is published to GitHub Pages on every push to `main` (when designer-relevant files change). `?demo=v1-metadata` loads the `samples/v1-metadata/` bundle pre-loaded so anyone visiting the URL sees a working graph without uploading anything.

**Reads:** [contracts §9 (URI conventions)](../../design/phase-03-contracts.md#9-uri-and-path-conventions).
**Blocked by:** §D / §E / §F (Designer needs to actually work end-to-end).
**Blocks:** nothing.

## Tests-first

CI-deploy work has less unit-test surface; we rely on smoke checks instead. Write these test stubs (most of them are shell-script asserts run inside the deploy workflow).

- [ ] `packages/designer/scripts/__tests__/copy-samples.test.ts` — Node script test.
  - The `copySamplesToDist` script copies every file under `samples/v1-metadata/` (excluding hidden files and `.modeler/`) into `packages/designer/dist/samples/v1-metadata/`. Assert the file count matches and one specific file's content is identical.
- [ ] `packages/designer/src/__tests__/demo-loader.test.tsx` — RTL + jsdom; `fetch` mocked.
  - With `?demo=v1-metadata` in the URL, App fetches `/samples/v1-metadata/modeler.toml`, `/samples/v1-metadata/db.ttr`, etc., and dispatches `loadProject` exactly once with the discovered files.
  - With no demo flag, App shows the landing-page card and does not fetch anything.
- [ ] CI workflow self-check: a job that runs the deploy workflow's preview build locally (or `--dry-run`-equivalent) and `curl`-checks the resulting `index.html` and worker `.js` for 200s. Document the command in `packages/designer/README.md`.

## Library reference

No external libraries. The deploy is plain GitHub Actions + Pages. Confirm via Context7 the current expected Pages action versions:

```
mcp__context7__resolve-library-id { libraryName: "actions/upload-pages-artifact", query: "upload built site as Pages artifact" }
mcp__context7__resolve-library-id { libraryName: "actions/deploy-pages",          query: "deploy uploaded artifact, environment, permissions block" }
```

Pin major versions in the workflow file.

## Implementation tasks

- [ ] **G.1 — Sample copy script.** New `packages/designer/scripts/copy-samples.ts` (or `.js`). Run via `pnpm --filter @modeler/designer prebuild` so the samples land in `dist/` automatically. Tests green.
- [ ] **G.2 — Vite config base path.** In `packages/designer/vite.config.ts`, set `base: process.env.DESIGNER_BASE_URL ?? '/'` so production builds bake in the GitHub Pages sub-path (`/<repo>/`) and local dev stays at `/`. Worker URL resolves correctly under both.
- [ ] **G.3 — Demo-mode landing.** In `App.tsx`'s top-level effect: if `new URLSearchParams(window.location.search).get('demo') === 'v1-metadata'`, fetch every `.ttr`, `.ttrl`, `.toml` under `/samples/v1-metadata/` (you have the manifest of the bundle from G.1 — read it via `/samples/v1-metadata/index.json` written by `copy-samples.ts`). Hand them to `loadProjectViaUpload`-equivalent in-memory and proceed. Make demo-loader test green.
- [ ] **G.4 — Landing-page card.** If no project is loaded and no demo flag, render a centered card with two CTAs: "Load a project" (opens the directory picker) and "Open demo" (sets `?demo=v1-metadata`).
- [ ] **G.5 — GitHub Pages workflow.** New `.github/workflows/designer-deploy.yml`:
  - `on: push: branches: [main], paths: ['packages/designer/**', 'packages/lsp/**', 'samples/**']`
  - `permissions: pages: write, id-token: write`
  - Build steps: pnpm install → `pnpm --filter @modeler/lsp build` → `DESIGNER_BASE_URL='/<repo>/' pnpm --filter @modeler/designer build`
  - Use `actions/upload-pages-artifact@v3` (or current major) pointing at `packages/designer/dist/`.
  - Use `actions/deploy-pages@v4` (or current) to deploy.
  - `concurrency: { group: 'designer-deploy-${{ github.ref }}', cancel-in-progress: true }`
- [ ] **G.6 — Smoke-curl post-deploy.** Add a final workflow step that `curl -fI <pages-url>/` and `curl -fI <pages-url>/assets/<worker-filename>.js` to fail the workflow if either is not 200. Worker filename comes from `dist/assets/` post-build; emit the filename to `$GITHUB_OUTPUT` from the build step.
- [ ] **G.7 — README and one-time setup.** Update `packages/designer/README.md` with: (a) deploy URL, (b) the one-time GitHub Pages settings activation (must be done manually in the repo's Settings → Pages), (c) the `DESIGNER_BASE_URL` env var meaning, (d) where the embed script will land in v1.x (out of scope for Phase 3).

## Verify by running

```bash
DESIGNER_BASE_URL='/' pnpm --filter @modeler/designer build
# Inspect packages/designer/dist/ — confirm:
#   - index.html exists
#   - assets/index-*.js + assets/server-browser-*.js both exist
#   - samples/v1-metadata/*.ttr files were copied in
npx http-server packages/designer/dist -p 8080
# Open http://localhost:8080/?demo=v1-metadata — see samples loaded, db/er rendering, inspector working.
```

After merge, the workflow runs on push to main; verify the deployed URL with the same `?demo=v1-metadata` query.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] copy-samples + demo-loader tests green.
- [ ] Manual: local serve of `dist/` with `?demo=v1-metadata` shows a fully-working Designer.
- [ ] CI: the designer-deploy workflow ran successfully at least once on `main`; the deploy URL is documented in `packages/designer/README.md`.
