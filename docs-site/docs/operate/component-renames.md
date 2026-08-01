<!-- SPDX-License-Identifier: Apache-2.0 -->
# Component renames (2026-08)

*Reference. Every component dropped its `ttr-` prefix. Nothing you call changed.*

Every service, library, tool and worker in the server carried a `ttr-` prefix inherited from the
fork. It said nothing a reader did not already know — you are in the Tatrman server, everything
here is Tatrman — while making every chart name, image tag and log line four characters longer.
As of the **2026-08** release the prefix is gone.

## What did not change

**No wire contract moved.** This was a rename of components, not of the surface you call:

- **MCP tool names and their arguments** are untouched — `query`, `compile`, `search`,
  `get_model`, `resolve_area`, `ground_time`, `match`, and the rest.
- **gRPC package names** are untouched — `query.v1`, `meta.v1`, `translate.v1`, `validate.v1`,
  `dispatch.v1`, `fuzzy.v1`, `nlp.v1`, `llm.v1`, `worker.v1`, `transfer.v1`.
- **Capability ids** are untouched — `resolve.bind:v1` and its siblings.
- **Kubernetes namespaces** are untouched — a deployed estate keeps its `ttr-server` namespace.
- **Kubernetes Service names and OpenTelemetry `service.name` values** are untouched. They never
  carried the prefix: your dashboards, alerts and traces key on `query`, `dispatch`, `validate`
  and friends today, and still will. <!-- naming-gate-ok -->

A conformant agent needs no change at all. If you only ever talk to the MCP doors, you can stop
reading here.

## What changed

Chart names, image repositories, Gradle modules, source directories, and published Maven
coordinates.

### Services, tools, workers <!-- naming-gate-ok -->

| Old | New |
| --- | --- |
| `ttr-query` | `query` |
| `ttr-translate` | `translate` |
| `ttr-validate` | `validate` |
| `ttr-dispatch` | `dispatch` |
| `ttr-resolver` | `resolver` |
| `ttr-nlp` | `nlp` |
| `ttr-llm-gateway` | `llm-gateway` |
| `ttr-grounding-mcp` | `grounding-mcp` |
| `ttr-identity` | `identity` |
| `ttr-fuzzy` | **`lex-matcher`** |
| `ttr-meta-mcp` | `meta-mcp` |
| `ttr-query-mcp` | `query-mcp` |
| `ttr-nlp-mcp` | `nlp-mcp` |
| `ttr-fuzzy-mcp` | **`lex-matcher-mcp`** |
| `ttr-worker-postgres` | `worker-postgres` |
| `ttr-worker-mssql` | `worker-mssql` |
| `ttr-worker-polars` | `worker-polars` |
| `ttr-nlp-{morphodita,nametag3,stanza,spacy}` | `nlp-{morphodita,nametag3,stanza,spacy}` |

`charon`, `chrono`, `geo`, `money` and `veles` never carried the prefix and are unchanged.

**`ttr-fuzzy` became `lex-matcher`, not `fuzzy`** — the odd one out. The component matches
question text against model vocabulary; "fuzzy" names one technique it uses, not the job it does.
The wire package `fuzzy.v1` keeps its name, because that *is* wire contract.

### Published Maven coordinates <!-- naming-gate-ok -->

Under the unchanged `org.tatrman` group:

| Old | New |
| --- | --- |
| `ttr-server-proto` | `server-proto` |
| `ttr-fuzzy-core` | `lex-matcher-core` |
| `ttr-text` | `text` |
| `ttr-meta-client` | `meta-client` |
| `ttr-llm-client` | `llm-client` |
| `ttr-transfer-core` | `transfer-core` |

The old coordinates stay resolvable at the versions already published; they receive no further
releases. Bump to the new coordinate at the first release you adopt after 2026-08.

!!! note "The toolchain keeps its prefix"

    `org.tatrman:ttr-parser`, `ttr-writer`, `ttr-semantics`, `ttr-metadata`, `ttr-translator` and
    the rest of the **TTR language toolchain** are published from a different repository and are
    **not** renamed. `ttr-` there means "the TTR language", which is exactly what those libraries
    are about. <!-- naming-gate-ok -->

## What you have to do

**If you consume the MCP doors:** nothing.

**If you deploy the Helm charts:** the per-component chart names and image repositories moved,
so a GitOps repository pinning `chartPath` or `image.repository` needs one editing pass:

```yaml
# before
image:
  repository: ghcr.io/collite/ttr-query   # naming-gate-ok

# after
image:
  repository: ghcr.io/collite/query
```

The umbrella chart's values keys are unchanged, so `values.yaml` overrides carry over as they are.

**If you depend on the published libraries:** update the coordinates in the table above when you
next bump versions.
