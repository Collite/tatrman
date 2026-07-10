# Tatrman Server — The MCP Surface Contract (RO-8 · RO-25)

> **Status:** pinned 2026-07-10 (design-gaps session, closing item 4 of `../../next-steps-260710-design-gaps.md`; decision = **RO-25**, control room §7). This document is the named, tatrman-owned contract that RO-8 declared and `ecosystem.md` §5 describes: **the MCP surface is the ecosystem's consumption contract** — how any agent, from any vendor, in any framework, consumes the standard in practice. Apache-2.0; owned by **tatrman** (the standard), implemented by **tatrman-server** (the doors).
> **Ground truth for the live shape:** kantheon `tools/{theseus,ariadne,echo}-mcp` (manifests, `docs/technical/query-mcp-service.md`, identity specs) as of 2026-07-10.

---

## 1. Scope and principles

- The surface consists of the four MCP doors (§2), their tool schemas, the identity pass-through semantics (§3), and the compatibility rule (§4). The conformance conversation suite (§5) is the contract's executable test.
- **The surface is wire — J-v2 applies in full.** Personas never appear in tool names, capability ids, categories, or schema fields. (Tool names are already functional; the capability ids rename in SV-P0/S4, §2.1.)
- Transport: MCP over StreamableHTTP; HTTP probes (`/health`, `/ready`, `/status`, `/metrics`) sit beside `/mcp` and are deployment surface, not contract.
- A second implementation of any door is possible by construction: everything an agent can observe is specified here or in the referenced schemas — nothing depends on implementation internals.

## 2. Doors and tool inventory (1.0.0)

| Door | Function | Tools (v1) |
|---|---|---|
| `ttr-meta-mcp` | The model: what is known (Veles's agent door) | `get_model`, `get_object`, `list_objects`, `list_queries`, `resolve_area`, `search` |
| `ttr-query-mcp` | The governed query path | `query`, `compile` |
| `ttr-fuzzy-mcp` | Fuzzy candidate matching over model-declared searchable fields | `match` |
| `ttr-grounding-mcp` | Deterministic grounding of universal spans (chrono/geo/money) | **reserved — arrives SV-P3**; its tools enter this contract under the same rules |

### 2.1 Capability ids — functional categories (RO-25)

Capability ids and categories go functional in the SV-P0/S4 sweep (rename-before-publish; the k8s endpoints rename with N1 in the same window):

| Live id (kantheon) | Final id | Category |
|---|---|---|
| `theseus.query:v1` | `query.run:v1` | `query` |
| `theseus.compile:v1` | `query.compile:v1` | `query` |
| `ariadne.get_model:v1` | `meta.get_model:v1` | `meta` |
| `ariadne.get_object:v1` | `meta.get_object:v1` | `meta` |
| `ariadne.list_objects:v1` | `meta.list_objects:v1` | `meta` |
| `ariadne.list_queries:v1` | `meta.list_queries:v1` | `meta` |
| `ariadne.resolve_area:v1` | `meta.resolve_area:v1` | `meta` |
| `ariadne.search:v1` | `meta.search:v1` | `meta` |
| `echo.match:v1` | `fuzzy.match:v1` | `fuzzy` |
| (SV-P3) | `grounding.*:v1` | `grounding` |

MCP tool *names* (what an agent calls) stay as they are — `query`, `compile`, `get_model`, `get_object`, `list_objects`, `list_queries`, `resolve_area`, `search`, `match` — they are already functional.

### 2.2 Tool schemas — the pinned shapes

**`query`** (run a query through the governed path: translate → validate → dispatch → worker; results stream back typed).
Required: `source` (query text) · `source_language` (`sql` | `transdsl` | `dfdsl` | `rel_node`).
Optional: `parameters` (bound parameter map, types inferred from JSON) · `session_id` (sticky routing, only honored when the worker advertises `supports_stateful_sessions`) · `format` (`json` default | `csv` | `tsv` | `markdown`) · `row_limit` (clamped to `[1, 5000]`, default 500) · `hide_columns_matching` (regex list) · `row_numbering` (`none` | `one_based`).
Output: `content[0]` = formatted result text; `structuredContent` = the **result envelope**:

```jsonc
{
  "ok": true, "tool": "query",
  "rowCount": 5, "columnCount": 4, "truncated": false,
  "format": "json", "mediaType": "application/json; charset=utf-8",
  "columns": [ { "name": "id", "type": "Int64", "nullable": true } ],
  "messages": [ { "severity": "warning", "code": "partial_results_truncated", "text": "…" } ],
  "pipelineWarnings": [ { "code": "security_predicate_applied", "severity": "info",
      "text": "…", "sourceService": "validator", "metadata": { "sourceStage": "security" } } ]
}
```

**`pipelineWarnings` is always present** (empty array when clean) — this is the provenance attachment the contract guarantees (§3): every governed answer carries what the pipeline did to it, uniformly parseable.

**`compile`** (front of the pipeline only — parse → validate; nothing executes).
Required: `source`, `source_language` (as above) · `target_dialect` (`mssql` | `postgresql` | `mysql_mariadb`).
Optional: `parameters` · `apply_security` (default `true`; `false` requires an admin role, else `permission_denied`).
Output: compiled SQL (`content[0]`, `structuredContent.compiledSql`) + `structuredContent.parameterPlan` = `[{name, type, bound, label?}]`.

**`meta.*` tools** (schemas per the ariadne-mcp manifests; detail pinned at the proto level in `org.tatrman.meta.v1`):
`get_model(packages[], include_search_hints?, include_roles?, include_drill_map?, locale?)` → ModelBundle (entities, relations, tables, views, pattern/named queries, roles, drill maps, package versions) — the heavy call. · `get_object(id)` → typed detail by qualified name (e.g. `er.entity.artikl`). · `list_objects(kind?, package?, binding?)` → drained-pagination `objects[]`. · `list_queries(package?, parse_status?)` → query descriptors with SQL template + parameter list. · `resolve_area(area)` → the package set a subject area spans (+ description, tags, `found`). · `search(query, limit?)` → top-N results over names, localized labels, aliases, search keywords, with scores and snippets.

**`match`** (`fuzzy.match:v1`): `name` (required) · `category?` · `algorithm?` (`LEVENSHTEIN` | `TATRMAN` | `JARO_WINKLER` cascade; precision-first, recall-fallback) · `limit?` → `matches[]` with scores. Czech-aware diacritic handling is contract-observable behavior (the suite asserts it).

Structured content rule (all doors): the `structuredContent` root is always a JSON **object**, never a bare array.

## 3. Identity pass-through — the OBO contract (RO-25)

What a third-party agent MUST do, and what the door guarantees back:

1. **The agent forwards the end user's bearer token on every call.** Per-user identity is structural, not advisory — the two-call thesis depends on the validator seeing the real user.
2. **Fail-closed rejections are contract behavior** (the suite asserts them): no identity → `missing_user_identity`; a service-account token with no user claim → rejected (agents never call as themselves); a token-vs-argument identity conflict → `identity_conflict` (no spoofing).
3. **The door guarantees:** the token's identity and roles become the pipeline context and reach the validator **unchanged**; the governed result returns with the provenance attachment (`pipelineWarnings`, always present — RLS predicate injection, column masking, caps, coercions are all visible to the caller).
4. **Deployments MUST verify the token before pipeline entry.** The mechanism is free (ingress/sidecar termination or in-door verification) — the guarantee, not the mechanism, is contractual. *(The live pilot terminates at ingress; the door only extracts claims — a conforming arrangement.)*
5. The trusted-network shortcuts that exist in the live edge (`X-User-Id` header, `user_id` tool argument) are **deployment-internal configuration, outside this contract**. A third-party agent that relies on them is non-conformant; a deployment that enables them does so on its own authority (they are ignored whenever a bearer token resolves).

Claim conventions (Keycloak-shaped, the reference IdP): user id = `preferred_username` falling back to `sub`; roles = `realm_access.roles`. Other IdPs map to the same two facts; the mapping is deployment configuration.

## 4. Versioning and compatibility (J-v2 verbatim — RO-25)

- Every tool carries its version in the capability id (`:v1`).
- **Within v1, changes are additive only**: new optional inputs, new output fields. Agents MUST tolerate unknown output fields (the envelope grows; it never mutates).
- **Any breaking change ships as a new `:v2` capability alongside `:v1`** — never in place. Deprecation windows are documented in the capability manifest; deprecated ≠ removed within a major product version.
- One wire discipline everywhere: this is the same rule the protos live under; the MCP schemas are wire.

## 5. The conformance conversation suite (NEW-2 — closed by RO-25)

**Purpose:** the executable test of this contract. The RO-3 bar's Golem clause runs it; a third-party agent claiming to consume Tatrman Server conformantly runs the same files.

**Format — declarative conversation fixtures** (YAML; engine-agnostic): each fixture pins a conversation turn against a reference model and asserts the observable path, not the implementation:

```yaml
id: core/rls-denied-column
model: fixtures/models/pilot-mini      # reference model the suite ships
identity: { user: analyst_a, roles: [sales_read] }
turn: "Average salary by department last quarter"
expect:
  calls:                                # expected resolution/grounding calls, order-free set
    - tool: meta.search    # binds "salary", "department"
    - tool: query.run
  result:
    envelope: { ok: false }             # or ok:true + shape assertions
    error_code: column_denied
  provenance:
    pipelineWarnings_contains: [{ code: column_denied_mask, sourceService: validator }]
```

Assertion vocabulary: expected tool-call set (with argument matchers) · governed result shape (envelope fields, column sets, row-count bounds — never exact floats where engines may differ) · provenance assertions (`pipelineWarnings` codes) · rejection assertions (identity gate, permission). A fixture never asserts LLM wording — only observable calls and governed results.

**Tiers and pass-bar:**

- **Core tier — hand-authored, ~25–40 fixtures, 100% must pass to claim conformance.** Coverage floor: every tool of every door exercised · RLS row-filter case · column deny/mask case · identity-gate rejections (all three of §3.2) · truncation/row-limit behavior · parameter binding via `compile` · fuzzy match with diacritics · refusal-over-guess (low-confidence turn must end in a clarifying question or a typed error, never a fabricated answer). Exists without DFP by construction.
- **Extended tier — the DFP-derived synthetic corpus** (RO-19 ask ③, pilot-derived, anonymized): reported as a score, non-gating. Grows without breaking anyone's conformance claim.

**Ownership and home:** the suite is part of the standard (tatrman repo, beside the contract it tests), per RO-8. The runner is a thin harness (feed fixture, observe MCP traffic, diff assertions) — reference runner ships with the suite; Golem (Kotlin+Koog) is its first consumer, the Python Golem its second (RO-19).

**Authoring schedule:** fixture schema finalization + core-tier authoring = SV-P4 (with the reference Golem); grounding fixtures join when `ttr-grounding-mcp` lands (SV-P3).

## 6. Open ends (tracked, not blocking)

- `grounding.*:v1` tool schemas — pinned at SV-P3 with the extraction (this doc gains §2.2 entries; J-v2 governs their arrival).
- Fixture-schema detail (matcher grammar, model-fixture packaging) — SV-P4 `/planning` task material, inside the frame pinned here.
- `meta.*` structured-output schemas are pinned by `org.tatrman.meta.v1` proto shapes after the S4 sweep; this doc references, not restates, them.
