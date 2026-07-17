<!-- SPDX-License-Identifier: Apache-2.0 -->
# OIDC and Keycloak

*How-to. Wire your identity provider, and the verification duty that stays yours.*

Tatrman decides what each user may see. It can only do that if the real user's identity reaches the
validator on every call — and if the token carrying that identity is *verified* before it does.
Agents forward the token (that is their job); **verifying it is yours.**

## Wire your IdP

Set the `auth.*` keys (see [The values contract](values-contract.md)):

```yaml
auth:
  issuerUrl: https://idp.example.com/realms/yourrealm
  audience: tatrman
  jwksUri: https://idp.example.com/realms/yourrealm/protocol/openid-connect/certs
  realm: yourrealm
  verification: ingress        # or in-door
```

The reference IdP is Keycloak; any OIDC provider works. The platform reads exactly two facts from a
token — the **user id** (`preferred_username`, falling back to `sub`) and the **roles**
(`realm_access.roles`). If your IdP names these differently, map them to those two claims in your
IdP configuration. The platform does not reshape claims, and neither should your agents.

## The verification duty — do not skip it

**Deployments MUST verify the bearer token before pipeline entry.** The mechanism is free:

- **`verification: ingress`** — terminate and verify at the ingress/sidecar, and let the door
  extract already-trusted claims. (The live pilot does this.)
- **`verification: in-door`** — the door verifies the token itself.

Either is conforming. What is *not* conforming is skipping verification and trusting whatever
identity arrives — that is an unguarded system that looks guarded. If you take one thing from this
page: an operator who skips token verification has removed the platform's entire governance
guarantee while leaving every green checkmark in place.

## Keep the shortcuts off

`auth.trustedNetworkShortcuts` (the `X-User-Id` header, the `user_id` argument) defaults off and
should stay off unless you own the entire call path and know exactly what you are trusting. They are
outside the identity contract, and any conformant agent ignores them anyway. When a real bearer
token resolves, they are ignored regardless.

## For the quickstart only

`devIdp.*` gives you a throwaway Keycloak with a seeded realm and a `demo`/`demo` user so a first
run works without an IdP. Turn it off and wire the `auth.*` keys above before anyone real uses the
system.

## Verify

- A call with no token is refused (`missing_user_identity`) — good.
- A service-account token with no user claim is refused — agents never call as themselves.
- The same governed question asked by two different users returns different `pipelineWarnings` — the
  per-user filters are being applied. That difference is the proof your identity wiring works.
