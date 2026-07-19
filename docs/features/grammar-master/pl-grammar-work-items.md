# Grammar-master work items — Platform (PL) batch

Status: **filed 2026-07-19 (PL-P0.S1)** · Source of truth for scope/DoD: `project/platform/design-corpus/implementation/plan.md`; shapes: platform [`contracts.md`](../../../../project/platform/design-corpus/design/contracts.md).

Four `TTR.g4` grammar surfaces are **reserved** by the PL-P0 S1 amendment batch. Each is **additive** (new optional construct / sugar → minor bump, `@grammar-version` `0.9 → 0.10` when the first lands). **Specs are recorded here now; implementation lands in the phase that consumes each** — the grammar-master process (`new-grammar-version-process.md`) is followed per surface when it is implemented, not now. This is the filing the plan's PL-P0 DoD requires and PL-P0.S2.T5 confirms.

| # | Surface | Family | Owner (lane) | Target phase | Kind |
|---|---|---|---|---|---|
| 1 | `params` (runtime params, F-4-i) | TTR-P | senior2 | **PL-P2**.S1 | additive minor |
| 2 | `on-failure` island vocabulary (F-4-iv) | TTR-P | senior2 | **PL-P2**.S1 | additive minor |
| 3 | K `extends`-platform-world (world composition) | TTR-M | senior2 | **PL-P1**.S4 | additive minor |
| 4 | `security` block (H-1 reservation) | TTR-M | senior2 | **PL-P4**.S3 | additive minor |

The manifest **schema** already permits #1/#2 (see TTR-P [`contracts.md §5.1`](../ttr-p/architecture/contracts.md#manifest-v2)); these items cover the *grammar* that makes the toolchain **emit** them.

---

## 1. `params` — runtime params (F-4-i) · lands PL-P2.S1

Trigger-time, declared-and-typed params on a TTR-P program. Types `{string, int, decimal, date, datetime, bool}`, binding trigger-time, builtin `run-date`. Compiles into manifest `params[]` (name/type/required/default) and `island.params[]`. Legal only against a world whose executor-type manifest declares `params` (else T6 compile error — §5.2). Determinism: params are declarations, not values; the bundle stays a pure function of resolved inputs (values arrive at trigger time via the envelope, not the artifact).

## 2. `on-failure` island vocabulary (F-4-iv) · lands PL-P2.S1

Island-scoped on-failure edges: an island declared to run **iff** a named source island failed. Compiles into manifest `island.onFailureOf` (+ `retries`, transient/permanent classification, wave-resume guarded by snapshot fingerprint). `absorbs` stays reserved (false in v1). Legal only against a world whose executor manifest declares `onFailure`/`retries`/`resume` (else T6 compile error).

## 3. K `extends`-platform-world — world composition · lands PL-P1.S4

A TTR-M world document may declare, at `def world` level:

```ttrm
def world acme.worlds.prod {
    extends: "acme.platform.prod"      # platform-world qname; platform world is AUTHORITATIVE
    # project may ADD objects and EXTEND (non-contradicting) declarations
}
```

**Composition semantics** (platform [`contracts.md §16`](../../../../project/platform/design-corpus/design/contracts.md) + the C-6/K rule):
- The platform world is **authoritative**; the project world **adds** objects and **extends** non-contradicting declarations.
- A **contradiction** with the platform world is a **compile error** (`TTRP-LCK-004` at lock/resolve time — platform §21).
- Composition is fed into the **fingerprint** and must be **order-insensitive**; contradiction detection is exact (property-test target — plan Risks).
- Veles serves the **RESOLVED composed world** as TTR text, so standalone re-resolution of the archive is the identity (`snapshot.json.resolvedFrom` is informative only, never hashed).

Consumed by: the ttr-metadata `WorldResolver` composition (PL-P1.S4) and `ttr.lock` (`TTRP-LCK-004`).

## 4. `security` block (H-1 reservation) · lands PL-P4.S3

TTR-M grammar reservation (shape only — exact grammar lands via this process at PL-P4):

```ttrm
security {
    own       sales: team_sales                     # ownership declaration
    classify  order_line.customer_email: pii        # classifications = native grant vocabulary (HQ-1)
    grant     read on sales to accounting           # role- or classification-mapped grant
    mask      order_line.customer_email             # column mask (default: all; policy maps exceptions)
}
```

Invariants the reservation pins:
- **Fingerprint-neutral:** `security` blocks are **excluded** from the world/T6 semantic fingerprint (access changes must not churn world verification) and never alter emitted plans.
- **Generator is one-way + deterministic (MIT):** `security` → Rego fragments + structured data, package `tatrman.generated.<sanitized-qname>`, `# GENERATED — do not edit` header, same input ⇒ same bytes. Never hand-edited.
- **Composition = deny-overrides:** sugar grants; hand Rego can always take away. Row-level predicates stay Rego-side in v1.
- Verbatim role names legal; classification→role mapping is org policy data; **unknown roles fail closed at bundle build** (Perun, platform §19), advisory lint standalone (H-8).

Consumed by: the MIT generator + Perun bundle pipeline (PL-P4).
