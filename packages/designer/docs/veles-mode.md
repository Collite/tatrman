<!-- SPDX-License-Identifier: Apache-2.0 -->
# Veles mode — the read-only catalog viewer (SV-P4·S2·T5)

RO-9 says the Designer viewer ships in-chart and renders the deployed model **over
Veles**. This note records how that was built and the decisions behind the five gaps
the S0·T4 audit flagged — plus what is deliberately **cut** from this task (the viewer
is outside the RO-3 acceptance bar; "deploy + render the catalog" is the bar).

## The blocker the audit missed, and the fix

The S0·T4 audit assumed *"Veles's `ListObjects`/`GetObject`/`Search`/`GetModel` map ~1:1
over HTTP onto `ModelDataSource`."* That is **false about HTTP**: those RPCs are
**gRPC-only** (`org.tatrman.meta.v1.VelesService`), `meta.proto` carries no
`google.api.http` transcoding, and no grpc-web/Connect bridge exists in the stack. A
browser cannot read the catalog from Veles as it stood — the only Veles HTTP was
`/status`, `/model/export` (a `.ttr` tarball) and `/model/export/dot` (Graphviz text).

**Fix (chosen with Bora, 2026-07-17):** a small **read-only JSON API on Veles** —
Veles already holds the model graph in memory, so it now serves it as browsable JSON at
`GET /model/{index,graph,object,search}` (tatrman-server `services/veles` read routes).
The browser adapter is then a thin `fetch` client, and the routes emit the existing
`ttrm/*` read DTOs verbatim so no transport-specific mapping is needed.

## What was built

- **`VelesDataSource`** (`src/data/veles-data-source.ts`) — implements the READ half of
  `ModelDataSource` over the Veles JSON API. `capabilities.edit = false`.
- **`?veles=<base>` selection** (`src/data/select-data-source.ts`) — explicit (P2). `<base>`
  is a same-origin path prefix (`/veles`) or a full http(s) origin (`http://localhost:7260`).
- **`VelesModeApp`** (`src/VelesModeApp.tsx`) — the read-only view: schema/package browse
  (`getModelGraph`) on the shared `WsCanvas`, object inspect (`getObject`), search
  (`search`). No graphs list, no `.ttrg`, no edit affordances.

## The five gaps — decisions

1. **No Veles `ModelDataSource` impl → built** (`VelesDataSource`). Read-only.
2. **Backend selection for a Veles HTTP origin → `?veles=`.** Distinct from `?server=`
   (which stays loopback-WS-only, a modeling-time server). A non-loopback origin is
   allowed for `?veles=` because Veles is the *deployed* catalog service, inherently
   remote; the **same-origin path-prefix form is preferred** (in-chart, CORS-free).
3. **`.ttrl` layout — CUT for Veles mode.** Veles serves no `.ttrl` sidecars, so
   `VelesDataSource` does not implement the optional `getLayout`; the catalog view
   auto-lays-out (same as WS-mode schema browse). Persisted positions are a
   modeling-repo concept, not a served-catalog one — out of scope here.
4. **Browser auth posture (contracts §16 "PEP: coarse visibility").** The read API is
   unauthenticated at the Veles edge today (like `/status`), matching the private-cluster
   posture. For an exposed deployment the **preferred shape is same-origin path routing**:
   the viewer's ingress serves the SPA and reverse-proxies `/veles/*` to the Veles
   Service, so the browser never makes a cross-origin call and the ingress is the PEP.
   The full-origin `?veles=` form requires the browser's origin in Veles's
   `KTOR_CORS_ALLOWED_HOSTS`. Fine-grained per-object visibility is **not** enforced by
   this read API — flagged as a future PEP concern, consistent with "coarse visibility."
5. **`onModelChanged` — poll, not push.** Veles has no server push, so `VelesDataSource`
   polls the existing `GET /status` `model_version` (default 15 s) and fires on change.

## Cut from this task (⚑ tracked, not done)

- **Containerizing + charting the viewer.** The designer is a Vite SPA with **no
  container image, no Helm chart, and no olymp deployment** today. Serving it in-chart
  (an nginx image of `vite build` + an umbrella subchart + the ingress that path-routes
  `/veles/*` → Veles + CORS) is the remaining "deploy" leg. It is outside the RO-3 bar;
  the viewer renders the catalog **today in dev** (`npm run dev`, `?veles=http://localhost:7260`
  with the origin added to Veles's CORS allowlist). The chart subchart is the S2·T5
  follow-up (or a later viewer arc).
- **Areas / richer object detail.** `getModelIndex` returns `areas` if Veles exposes
  subject areas; object detail surfaces qname/kind/schema/pkg/source/references — the
  gRPC `GetModel` carries more (roles, drill maps) not needed to render the catalog.
