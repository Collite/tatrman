<!-- SPDX-License-Identifier: Apache-2.0 -->
# Your first agent against the door

*Tutorial. One question, start to finish: connect a generic MCP client, forward the user's
identity, ask, and read the answer together with its provenance. Framework-neutral — the shapes
below are the contract's shapes; the [quickstart](../get-running/quickstart.md) carries the same
run captured live against a cluster.*

By the end you will have made a governed query on behalf of a real user and seen the trail of what
the platform did to keep the answer legal. You need a running Tatrman (the
[quickstart](../get-running/quickstart.md) gets you one) and an MCP-capable client or SDK.

## 1. Register the doors

Point your MCP client at the doors you need — at minimum the model door and the query door, each at
its service's `POST /mcp`:

- `ttr-meta-mcp` — to find what exists.
- `ttr-query-mcp` — to ask for data.

Your client will list the tools each door offers. If it does not, the door is unreachable — check
the service is `Ready` before going further.

## 2. Forward the user's token

This is the step that makes the whole thing governed. On **every** call, attach the end user's
bearer token (the `Authorization: Bearer …` header your MCP client sends with the request). Your
agent calls *on behalf of* the user; it never calls as itself. If you skip this, the door refuses
with `missing_user_identity` — as it should. See [The identity contract](identity.md) for the full
obligation.

## 3. Turn words into model vocabulary

Start with `search` on the model door to bind the user's words to qualified model ids:

```jsonc
// tool: search  (ttr-meta-mcp)
{ "query": "invoices by customer", "limit": 5 }
```

You get back scored candidates over entities, aliases and search keywords — enough to know the
entity and fields your query will name. (For ambiguous *instances* — "which Jan Novák?" — use
`resolve.bind:v1`, which will hand back a clarifying question rather than guess.)

## 4. Ask for the data

Express the intent as a query over the modeled entities on the query door:

```jsonc
// tool: query  (ttr-query-mcp)
{ "source": "…query over the entity you just resolved…", "source_language": "sql", "row_limit": 100 }
```

## 5. Read the envelope *and* the provenance

The response carries the result **and** its trail. Read both:

```jsonc
{
  "ok": true, "tool": "query", "rowCount": 42, "truncated": false,
  "columns": [ /* … */ ],
  "pipelineWarnings": [
    { "code": "security_predicate_applied", "severity": "info", "sourceService": "validator" }
  ]
}
```

`pipelineWarnings` is **always present**. Here it tells you a row-level filter was applied — the
user saw their rows, not everyone's. Show this to your user. An answer without its provenance is
half an answer, and swallowing a refusal (`ok: false`, or an empty resolution) to produce a
confident-sounding reply is the one thing a Tatrman agent must never do.

## Where to go next

- Make the refusal behaviors first-class: [The identity contract](identity.md).
- Understand every tool and field: [The doors and tools](doors-and-tools.md).
- Prove your agent behaves: [The conformance suite as your harness](conformance-harness.md).

_Framework specifics (LangGraph, Koog, MCP-capable assistants) differ only in how you register a
door and attach the bearer — the four steps above are the same everywhere._
