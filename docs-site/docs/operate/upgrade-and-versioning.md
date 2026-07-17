<!-- SPDX-License-Identifier: Apache-2.0 -->
# Upgrade and versioning

*Reference. What the product version promises you.*

You upgrade Tatrman the way you upgrade any Helm release — but the number you are upgrading *to*
carries a promise, and knowing it is the difference between a routine bump and a careful migration.

## The product version

The chart's **`appVersion` is the product version.** It is the one number that tells you what
changed for you as an operator and as a consumer:

- **Within a major version, the wire surface is additive.** The MCP tools your agents call, and the
  result envelope they read, only grow — new optional inputs, new output fields. A conformant agent
  keeps working across every minor and patch bump without you touching it (this is the
  [compatibility rule](../connect/compat.md), from the consumer's seat).
- **A breaking wire change ships as a new major**, and as a new `:v2` capability *alongside* the
  `:v1` it replaces — never in place. You migrate deliberately; nothing is pulled out from under a
  running agent within a major.
- **Deprecated is not removed** within a major. A capability marked deprecated keeps answering; the
  window is documented in the capability manifest.

## Image tags follow the product

Service images resolve by the precedence `image.tag` → `global.image.tag` → `appVersion`. Pin
`global.image.tag` to the product version you intend to run and leave per-service tags unset for a
uniform, reproducible install. An upgrade is then a single value change plus `helm upgrade`.

## The documentation is versioned too

The docs site is published with `mike`: before `1.0.0` it serves a rolling `latest`; from `1.0.0`
onward each product version gets its own labelled snapshot. Read the docs for the version you run,
not the tip — the snapshot for your `appVersion` describes the surface you actually have.

## Before you upgrade

- Read the release notes for the target `appVersion` — a minor/patch is additive; a new major names
  its `:v2` capabilities and deprecations.
- Bump `global.image.tag` and `helm upgrade`.
- Confirm every pod returns to `Ready` and the front door's `/ready` is green.
- Re-run your agent's [conformance check](../connect/conformance-harness.md) — green means the
  surface your agent depends on survived the upgrade.
