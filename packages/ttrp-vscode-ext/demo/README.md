<!-- SPDX-License-Identifier: Apache-2.0 -->
# TTR-P assist demo workspace

A self-contained workspace for exercising the VS Code assist loop (Checks C/D of the
TTR-P v1 acceptance guide). It resolves the shared **`acme.worlds.dev`** world so that
generated candidates validate against a real model — the missing piece that makes the
`generate → validate → repair → Apply` loop reach a green **Apply**.

## Contents

| File | Role |
|---|---|
| `hero.ttrp` | The canonical hero program (copy of the conform fixture) — the doc you author into. |
| `modeler.toml` | `[ttrp]` project manifest — pins `world = "acme.worlds.dev"` and `default-imports = ["erp.*"]`. |
| `models/` | The shared erp-project model repo (`acme` world + `erp` tables) so the world resolves. |

`ttrp check hero.ttrp` from this directory is clean — the world and all model objects resolve.

## Running the assist (Check C)

1. F5 the extension (`packages/ttrp-vscode-ext`) → Extension Development Host.
2. In that window, **open this `demo/` folder**, then open `hero.ttrp`.
3. Put the cursor on a blank line inside the `crunch` container (where `sales` is in scope).
4. **`TTR-P: Generate with Assist`**.

- **Offline (no key):** the default `ttrp.assist.endpoint = mock:` emits `filter(sales, amount == 100000)`
  → the loop catches `TTRP-EQ-001`, repairs to `=`, and shows a diff + **Apply / Discard**.
- **Real model:** point at any OpenAI-compatible chat gateway:
  - `ttrp.assist.endpoint` = the gateway's `…/v1/chat/completions` URL
  - `ttrp.assist.model` = the gateway's model tag (confirm with a `curl` first — some gateways map
    their own deployment aliases rather than canonical model names)
  - **`TTR-P: Set Assist API Key`** → the `Bearer` key for that gateway (SecretStorage; never sent to the LSP)

Candidates are validated **as they will read after Apply** — spliced at the cursor and checked
against this workspace's world — so a passing candidate applies coherently.
