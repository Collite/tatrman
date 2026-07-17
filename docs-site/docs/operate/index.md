# Operate

> **I run this.**

Everything between `helm install` and a system you are willing to be paged for:
the values you are expected to set, the identity duty you cannot delegate to us,
where policy lives, and how to answer "why did it say that?" six months later.

## What will live here

<!-- TODO(S6): the values contract is a DOCUMENTED ARTIFACT produced by S2, not
     folklore reconstructed from the chart's defaults — if a key is not in the
     contract, an operator should not have to guess what it does. The identity
     duty page carries mcp-surface §3.4's verification requirement: we state it
     plainly here because an operator who skips it has an unguarded system that
     looks guarded. -->

- **The values contract** — every key the umbrella chart honours, what it does,
  and what happens if you leave it at its default.
- **OIDC and Keycloak** — wiring your IdP, and the verification duty that stays
  yours.
- **Policy in git** — the open validator store: writing, reviewing, and shipping
  policy the same way you ship the model.
- **One question, one trace** — the observability story: following a single
  question end to end through the stack.
- **Upgrade and versioning** — what the product version promises you.
- **Engine workers** — sizing and scaling the part that touches your data.
