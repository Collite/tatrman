<!-- SPDX-License-Identifier: Apache-2.0 -->
# The identity contract

*Reference. On-behalf-of (OBO): what your agent MUST forward, what the door guarantees back, and
why an agent that guesses instead of refusing is a bug.*

Tatrman's governance is per-user. The platform decides which rows you may see, which columns are
masked, and which questions are allowed **for the person asking** — not for your agent. That only
works if the real user's identity reaches the validator on every call. Forwarding it is your
agent's job, and it is not optional.

## What your agent MUST do

1. **Forward the end user's bearer token on every call.** Per-user identity is structural, not
   advisory — the platform's whole governance model depends on the validator seeing the real user,
   not your service. Your agent calls **on behalf of** the user; it never calls as itself.

That is the entire obligation. Everything below is what the platform does with it, and the ways it
fails closed when the obligation is not met.

## What the door guarantees back

- The token's identity and roles become the pipeline context and reach the validator **unchanged**.
- The governed result returns with its **provenance attachment** — `pipelineWarnings`, always
  present — so the caller can see every row filter, column mask, cap and coercion the platform
  applied on that user's behalf. Ask the same question as two different users and the provenance
  differs; that is the system working.

## The three rejections (fail-closed, suite-asserted)

The door refuses rather than guess about identity. Your agent must expect these and surface them —
never retry them away:

| Situation | Rejection |
|---|---|
| No identity on the call | `missing_user_identity` |
| A service-account token with no user claim | rejected — agents never call as themselves |
| The token's identity conflicts with an identity passed as an argument | `identity_conflict` — no spoofing |

An agent that relies on being able to act as a service account, or that passes a `user_id` argument
hoping the door trusts it, is **non-conformant** by construction.

## Claim conventions

The reference IdP is Keycloak, and the platform reads two facts from the token:

- **User id** — `preferred_username`, falling back to `sub`.
- **Roles** — `realm_access.roles`.

Other identity providers map to the same two facts; the mapping is a deployment concern (see the
Operate track's OIDC page), not something your agent configures. Your agent only forwards the
token — it does not parse or reshape claims.

## Trusted-network shortcuts are not for you

A deployment may, on its own authority, enable edge shortcuts (an `X-User-Id` header, a `user_id`
tool argument) for traffic it already trusts. These are **deployment-internal configuration,
outside this contract**, and they are ignored the moment a real bearer token resolves. A
third-party agent that depends on them is non-conformant. Forward the token.

## Why "refuse over guess" is the whole point

Governance you can bypass is decoration. The identity contract is fail-closed on purpose: no
identity means no answer, a conflicting identity means no answer, and a below-threshold resolution
means a clarifying question rather than a confident-sounding fabrication. Build your agent to carry
the user's identity honestly and to relay the platform's refusals plainly — and prove it with the
[conformance suite](conformance-harness.md), which asserts exactly these behaviors.
