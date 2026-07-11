# SV-P1 · S0 — Pre-flight (RO-13 review · Central namespace · calendar · keys)

> Mixed list: T1/T4 are **sessions with Bora**, T2/T3/T5/T6 are Bora's external track (tracked here because S1/S4 block on them). Nothing in this stage writes code. Everything here can start **immediately and in parallel** — T2 has registrar/DNS lead time, start it first.

- [ ] **T1 — The RO-13 core ⚑ review (blocks S1). [GATE]** Session: Bora + agent walk the frozen PL contracts ([`../../../platform/design/contracts.md`](../../../platform/design/contracts.md)) **§2 snapshot archive · §3 `ttr.lock` · §4 stats schema · §5 plan-proto flags** — every ⚑ in those sections gets kept/amended/dropped. These schemas enter force at the publish gates and are frozen by S1's tags; the resolver options (R3-α, snapshot-fed vocabulary) are a second consumer waiting on §2. Output: **RO-29** in control room §7 (append-only; cite RO-13) + ⚑ markers resolved in the contracts doc + one line in [`../plan.md`](../plan.md) §SV-P1 pre-flight. *Nothing S1 publishes may embed a schema that this review then changes.*
- [ ] **T2 — Maven Central namespace verification (Bora; blocks S4; LEAD TIME — start now).** Register a Central Portal account (`central.sonatype.com`) for **Collite** and claim the `org.tatrman` namespace via DNS TXT on `tatrman.org` (RO-17: the namespace rides the domain, which rides the domain transfer to Collite — stewardship checklist; if the transfer is not done, verify under the current registrar account and note it). Record the portal account + verification status in findings. Generate a **user token** (Portal → Account → Generate User Token) for CI.
- [ ] **T3 — GitHub admin (Bora, 5 min).** `Collite/tatrman-server`: Settings → set default branch `master`, delete the empty `main`. (Review-input ⚑3 — agent token lacked repo-admin.)
- [ ] **T4 — Ratify the calendar.** Bora ratifies/edits the month-grid proposal in [`00-task-management.md`](./00-task-management.md) §calendar (carried item 2 from the execution handover; urgent since RO-24 fixed versions and SV-P0 closed). Record the ratified grid there; note the collision rule stands (November > Aricoma).
- [ ] **T5 — Signing key (Bora; blocks S4).** Generate the Collite release-signing GPG keypair (H-6 trust root: Collite-held): `gpg --full-generate-key` (RSA 4096, uid e.g. `Collite Release Signing <releases@collite.cz>`), publish the public key (`gpg --keyserver keyserver.ubuntu.com --send-keys <id>`), export the private key ASCII-armored for CI (`gpg --export-secret-keys --armor <id>`), store offline + as org secrets (S4·T3 names them). Record key id + fingerprint in findings.
- [ ] **T6 — Fold the SV-P0 branches (Bora). [GATE for S1·T2+]** Merge to `master`: tatrman `sv-p0-server-fork` · tatrman-server `sv-p0-move` · kantheon `sv-p0-kantheon-close`. CI green on each `master` afterward. Tags in S1/S2 are cut only from the folded `master` (rule 8).

**Verify block:**
```bash
# T1: RO-29 exists
grep -c "RO-29" ~/Dev/collite-gh/tatrman/docs/ecosystem/platform/design/00-control-room.md   # ≥ 1
# T2: namespace shows verified in the Central Portal UI (manual check; record screenshot/date in findings)
# T3: default branch
gh api repos/Collite/tatrman-server --jq .default_branch    # "master"
# T6: folds done
for r in tatrman tatrman-server kantheon; do git -C ~/Dev/collite-gh/$r log master --oneline -1; done
```

## Findings / ⚑

*(fill as you go — key ids, portal account, verification date, RO-29 pointer)*
