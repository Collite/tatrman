# Agents

An agent is an instance of the **Golem** template — a slice of the metadata model exposed to end users as a single tool in the menu. Agents are defined in the **agent registry**: an `agents/` folder, one YAML file per agent. When a business analyst adds, edits, disables, or deletes a file, the platform (Argo CD) deploys, changes, or removes the agent accordingly — with no infrastructure work.

> You define **what** an agent is (id, label, model scope, environments). The platform owns **how** it runs (Helm chart, image, ingress, secrets). You never set the `host` or the image version.

Agents are **not** part of the TTR language — they are a separate configuration format (`apiVersion: agents.dfpartner.cz/v1`) that consumes the TTR model through the `resolved-packages.json` artifact. The machine-checkable source of truth is `agents/agent.schema.json`.

## Quick start — add an agent

1. Create a file `agents/<id>.yaml` (the naming convention is `<id>.yaml`).
2. Fill it in from the template below. `id` must be unique (lowercase, digits, hyphens).
3. In `shem.packages` list model packages, **or** in `shem.areas` list [areas](16-areas.md). At least **one** of `packages` / `areas` must be present. Every package, area, and entity must exist in the `resolved-packages.json` artifact (otherwise CI fails the PR).
4. Open a PR. CI validates the schema and the existence of every name. After approval and merge to `main`, the agent is deployed via Argo CD and appears in the frontend menu.

```yaml
apiVersion: agents.dfpartner.cz/v1
kind: golem
id: warehouse                  # stable, DNS-safe, unique; never reuse it
label: "Warehouse"             # menu label (max 40 chars, no apostrophe ')
enabled: true                  # default true; false = defined but not deployed
environments: [test]           # a subset of {dev, test, prod}
shem:
  areas: [accounting]          # areas → GOLEM_AREAS; see the areas page in the model
  packages: [warehouse]        # → GOLEM_PACKAGES; nested dotted names allowed
  entities: []                 # optional; canonical entity qnames
# hostPrefix: ...              # OPTIONAL — defaults to "golem-<id>". Set only for legacy DNS.
# image:                       # OPTIONAL, defaults to the platform image.
#   tag: "0.10.1"
```

> The `shem.areas` field follows the `domain` → `area` rename (v3.0; see [Areas](16-areas.md)). Older definitions used `shem.domains` with the same meaning.

## Fields

| Field | Required | Description |
|---|---|---|
| `apiVersion` | ✓ | always `agents.dfpartner.cz/v1` |
| `kind` | ✓ | `golem` |
| `id` | ✓ | unique identifier; drives the release name, the default `hostPrefix`, and the menu key. Once retired it is **never** reused. |
| `label` | ✓ | menu label (1–40 chars, no `'`) |
| `enabled` | – | default `true`; `false` = defined but not deployed |
| `environments` | – | default `[test]`; a non-empty subset of `{dev, test, prod}` |
| `shem.packages` | ✓¹ | packages → `GOLEM_PACKAGES`; nested dotted names allowed |
| `shem.areas` | ✓¹ | [areas](16-areas.md) → `GOLEM_AREAS`; the platform expands them into packages/entities at runtime |
| `shem.entities` | – | canonical entity qnames → `GOLEM_ENTITIES` (validated against the artifact) |
| `hostPrefix` | – | legacy DNS only; otherwise `golem-<id>` |
| `image.tag` | – | canary only; otherwise the platform default image |

¹ `shem` requires **at least one** of `packages` / `areas`.

## Common operations

| I want to… | I do |
|---|---|
| **Add** an agent | new file `agents/<id>.yaml`, PR |
| **Temporarily disable** | `enabled: false` (better than deleting — keeps the history) |
| **Promote to production** | add `prod` to `environments`, e.g. `[test, prod]` |
| **Change the scope** | edit `shem.packages` / `shem.areas` / `shem.entities` |
| **Permanently retire** | delete the file (Argo removes the agent). The `id` is **never** reused. |

## What CI checks

A PR that touches `agents/**` passes through the `validate-agents` gate (reads the committed artifact, runs without Node):

1. **Schema** — the file conforms to `agents/agent.schema.json`.
2. **Package existence** — every `shem.packages[*]` is in the artifact (including nested names).
3. **Area existence** — every `shem.areas[*]` is in the artifact (the platform expands them at runtime).
4. **Entity existence** — every `shem.entities[*]` is a known qname in the artifact.
5. **Unique `id`** — no two files share an `id`.
6. **`hostPrefix` collision** — no two enabled agents in the same environment share a host.
7. **`environments`** — a non-empty subset of the environments the platform runs.
8. **`label`** — non-empty, ≤40 chars, no `'`.

## Source of truth: `resolved-packages.json`

CI validates package, area, and entity names against the `generated/resolved-packages.json` artifact, which the Modeler generates. After any change to the model or to [areas](16-areas.md), regenerate the snapshot:

```
modeler resolve-packages model-ttr --out generated/resolved-packages.json
```
