# SV-P0 · S6 — Deployment rename + final gates

> Repo: **olymp** (cluster defs) + the pilot deployment. Same change window as the ledger's N1 (chart names, Argo apps follow the module renames). The pilot may alternatively be PINNED pre-move — that is an allowed DONE state if repointing now is inconvenient (record the pin).

- [ ] **T1 — Image builds from tatrman-server.** Wire `release-image.yml` (cloned shape from kantheon) in tatrman-server for the moved services; images named `ttr-query`, `ttr-validate`, … and `veles` (persona legal in *release/image* names? NO for published chart names — J-v2 guardrail allows personas in Helm *release* names and k8s labels only; image repository names are publish-adjacent → use functional names: `veles` is a surviving persona and stays `veles`). Record the registry paths in findings.
- [ ] **T2 — Olymp chart/values rename.** Chart names, `values.yaml` keys, image refs, Argo Application names for the moved services per ledger §3 (same change window discipline). Charon's chart stays kantheon-sourced (operate-parked) — only its proto changed (S5 T3): bump its image if the transfer.v1 rename rebuilt it.
- [ ] **T3 — backstage catalog + health wiring (RO-22 executed).** health + backstage now build/deploy from tatrman-server; backstage catalog entries updated — moved services point at `Collite/tatrman-server`, kantheon components stay registered in the same instance; health aggregation covers both repos' services as before.
- [ ] **T4 — Pilot repoint or pin.** Either: deploy the renamed charts to the pilot (olymp PR → Argo sync; verify one governed query end-to-end via ttr-query-mcp afterwards), or: pin the pilot to the pre-move images/charts and record the pin (image digests + chart versions) in findings AND in `00-task-management.md`'s DONE checklist.
- [ ] **T5 — The grep gate, both repos (phase DONE gate).**
  ```bash
  for r in ~/Dev/collite-gh/tatrman-server ~/Dev/collite-gh/kantheon; do cd $r &&
  grep -rn -iE 'ariadne|theseus|proteus|argos\b|kyklop|arges|brontes|steropes|echo\b|kadmos|prometheus' \
    --include='*.proto' --include='*.kt' --include='*.kts' --include='*.py' --include='*.conf' \
    --include='*.yaml' --include='*.toml' . \
    | grep -viE 'CHANGELOG|docs/|history|lore|Forked 2026|_to_delete' | head; done   # expect: empty on both
  ```
  (kantheon's agents may reference *surviving* personas — Golem/Pythia/Iris/Veles/Perun/Charon are not in the regex; anything the gate catches is real.)
- [ ] **T6 — Close the phase.** All six stage rows checked in `00-task-management.md`; phase-DONE checkboxes walked; findings sections compiled into a short `docs/ecosystem/server/implementation/tasks/sv-p0-review-input.md` for Bora's phase review; control room gets the session-index row at the review.

**Verify block:** T5 is the verify block; plus `argocd app list | grep ttr-` (or the Argo UI) showing synced apps if T4 chose repoint.

## Findings / ⚑
_(registry paths: … · pilot pin or repoint evidence: …)_
