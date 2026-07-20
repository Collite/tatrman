<!-- SPDX-License-Identifier: Apache-2.0 -->
# ttr-md-agent

The **agent-facing MD dot-path resolver**, exposed over MCP (Model Context Protocol) streamable
HTTP. A calling agent tokenizes free natural language into path tokens; this service turns those
tokens into a **canonical MD path** (or reports ambiguity / diagnostics). It is a *thin shell*
(architecture MDS6): every bit of language intelligence lives in `ttr-md-resolver`; this module only
adapts MCP ⇄ resolver DTOs. No prompt logic, no fuzzy matching.

Dot-path stage **S7-A**. Contracts: [`§9`](../../../../project/tatrman/features/md/dot-path/contracts.md)
(tool shapes are normative there).

## Tools

| tool | in | out |
|---|---|---|
| `md_resolve` | `{ tokens[] \| raw, model, mode: connected\|disconnected, asof? }` | `{ status: resolved\|ambiguous\|failed, path?, shape?, explanation?, alternatives?, diagnostics? }` |
| `md_explain` | `{ path \| raw, model }` | `{ explanation, shape }` |
| `md_list_members` | `{ domain, prefix?, limit? }` | `{ members, truncated }` |

`raw` is split on `.` respecting quotes (`"…"`), brace sets (`{…}`) and ranges (`a..b`) — the same
`PathText` tokenizer the resolver uses; no other NL processing happens here (MDS6). The DTOs are the
resolver's `@Serializable` `§3` types verbatim — their field names are a public contract from v1.

## Running

```sh
# model repo: one directory per model, each holding its .ttrm files (models/<name>/*.ttrm)
MD_AGENT_MODEL_ROOT=path/to/models \
MD_AGENT_DEFAULT_MODEL=md \
./gradlew :packages:kotlin:ttr-md-agent:run
# → ttr-md-agent 0.1.0 listening on http://127.0.0.1:3535/mcp
```

Config (env): `MD_AGENT_HOST` (default `127.0.0.1`, loopback-only), `MD_AGENT_PORT` (`3535`),
`MD_AGENT_MODEL_ROOT` (`models`), `MD_AGENT_DEFAULT_MODEL` (backs `md_list_members`, which carries no
`model` argument).

## Worked example (`md_resolve`)

Against the shared `sales` model, resolving `sales.name.Kaufland.net` (a `name`-qualified member, so
it resolves **disconnected**):

```json
// → md_resolve { "model": "md", "raw": "sales.name.Kaufland.net" }
{
  "status": "resolved",
  "path": {
    "cubelet": "sales",
    "coordinates": [ { "dimension": "Customer", "attribute": "Customer.name",
                       "selector": { "type": "Pinned", "member": { "text": "Kaufland", "deferred": true } } } ],
    "measure": "net",
    "agg": "SUM"
  },
  "shape": ["Time.day"],
  "explanation": [ { "token": "Kaufland", "slot": "customer.name", "via": "token" }, "…" ]
}
```

An unqualified measure that lives on two cubelets is **ambiguous**:

```json
// → md_resolve { "model": "md", "mode": "connected", "raw": "Kaufland.net" }   (net ∈ {sales, plan})
{ "status": "ambiguous", "alternatives": [ { "cubelet": "plan", "…": "…" }, { "cubelet": "sales", "…": "…" } ] }
```

## Member sourcing (connected mode)

`mode: connected` resolves **bare** members against a live member snapshot; `disconnected` (the
default) leaves bare members illegal (`TTRP-MD-007`) and qualified pairs deferred (R13). The service
takes a member-snapshot seam, but **live sourcing over the metadata server is a follow-up** — this
first cut ships disconnected by default (so `md_list_members` and bare-member `connected` resolution
need that wiring to return data). See `scripts/agent-loop-demo.main.kts` for a client-side demo.
