<!-- SPDX-License-Identifier: Apache-2.0 -->
# The compatibility rule

*Reference. What the platform may change under you, and what it may not.*

The MCP surface is wire, and it lives under one discipline everywhere — the same rule the platform's
protocol buffers live under. If you build to it, your agent keeps working across releases without
you watching a changelog.

## The rule

- **Every tool carries its version in its capability id** (`:v1`).
- **Within `v1`, changes are additive only.** New optional inputs and new output fields may appear;
  nothing that exists is removed or repurposed. The envelope *grows*; it never mutates.
- **Your agent MUST tolerate unknown output fields.** Read the fields you know by name; ignore the
  rest. An agent that rejects a response because it carries a field it has not seen before is
  fragile by its own choice — a future additive field will break it, and that is not a
  compatibility violation.
- **Any breaking change ships as a new `:v2` capability alongside `:v1`** — never in place. You
  migrate when you choose to; `:v1` does not change under you.
- **Deprecated is not removed.** Within a major product version, a deprecated capability keeps
  working; deprecation windows are documented in the capability manifest.

## What this means for you

- Parse defensively: known fields by name, unknown fields ignored. This single habit is what makes
  your agent forward-compatible.
- Pin to `:v1`. You will be told, in the manifest, when `:v2` exists and when `:v1` is deprecated —
  and even then `:v1` keeps answering until the next major.
- Treat the capability id, not the tool name, as the contract. Tool *names* (`query`, `search`,
  `match`, …) are stable and functional; the versioned capability id is what carries the promise.

The [conformance suite](conformance-harness.md) asserts observable calls and governed results, not
wire trivia — so a conformant agent and a conformant deployment stay compatible across every
additive release by construction.
