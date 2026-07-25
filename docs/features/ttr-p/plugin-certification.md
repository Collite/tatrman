# Emit-plugin certification & trust (EQ-2 / H-6)

> **Scope.** How a TTR-P **emit plugin** (`ttr-emit-<target>` — an ordinary Maven artifact implementing
> `org.tatrman.ttrp.emit.spi.TtrEmitPlugin`, contracts §8) is *certified* for third-party distribution and
> how the toolchain *trusts* it at load time. Authored for PL-P5.S2. The bash emitter
> (`org.tatrman:ttr-emit-bash`) is the reference plugin; Kestra (S3) and Airflow 3 (S4) follow the same path.

## Two trust roots — do not conflate them

TTR-P has **two independent** signing/trust systems. They protect different things and use different keys.

| Trust root | What it signs | Where it lives | Governs |
|---|---|---|---|
| **IdP / world keys** | world snapshots & canon (`ttr.lock` archive ids, the metadata-server’s signed content) | the platform identity provider | *what data/canon a compile is allowed to resolve* — a runtime/deployment concern |
| **Publisher keys** | the **plugin jar artifact** (a detached OpenPGP `<jar>.asc`) | the plugin *publisher* (e.g. a third party shipping `ttr-emit-<target>`) | *whether the toolchain will load this emit plugin* — a build-time concern |

This document is entirely about the **publisher keys**. An emit plugin is code the compiler loads into its own
process; its provenance is a separate question from whether the *canon it emits over* is trusted. Signing a
plugin jar says nothing about world canon, and vice-versa.

## Certification flow (EQ-2)

A plugin is *certified* — fit for third-party use — when all three hold, in order:

1. **Publish signed.** The artifact is published to a Maven repository with a **detached OpenPGP signature**
   (`<artifact>.asc`) beside the jar — the ordinary Maven Central convention. See *Signing* below.
2. **Pass the determinism kit.** Determinism is a **contract obligation** on `TtrEmitPlugin.emit` (same
   `EmitRequest` ⇒ byte-identical `EmitResult`; no clocks, random ids, env reads, or filesystem/network access).
   Prove it with:

   ```
   ttrp emit-determinism --plugin <group>:<artifact> <program.ttrp>…
   ```

   The kit compiles each program’s `EmitRequest` and re-emits it **twice** through the plugin, byte-comparing
   `EmitResult.files`. Exit `0` = PASS, `1` = a divergence (the report names the file and first differing
   offset), `2` = usage error. Run it over the whole conformance corpus; first-party plugins are wired into CI
   so the check is **permanently guarded** (the Q-6 clause). A plugin that reads a clock fails here.
3. **Record it in the plugin README.** State the certified toolchain/SPI version, the coordinates, the
   publisher key fingerprint, and that `emit-determinism` passes over the corpus. This is the human-visible
   certification record; there is no central registry in v1.

## Signing a plugin artifact

Produce a **detached, armored** signature over the exact published jar bytes:

```
gpg --armor --detach-sign --local-user <publisher-key> ttr-emit-<target>-<version>.jar
# → ttr-emit-<target>-<version>.jar.asc  (publish beside the jar)
```

Any OpenPGP implementation works — verification is BouncyCastle-based and reads standard detached signatures
(RSA/EdDSA, SHA-256+; binary or ASCII-armored). Publish the corresponding **public key** so consumers can add
it to their trusted keyring (below).

## Trust at load time

Third-party plugins load through `EmitPluginLoader.isolated(...)` (a parent-last classloader — the compiler
core never leaks into the plugin). Loading applies **two** checks, in order:

1. **Identity (`ttr.lock [plugins]`).** The artifact `sha256` MUST match its pin (`TTRP-LCK-010` unpinned,
   `TTRP-LCK-011` mismatch). This is the reproducibility gate — it says *this is the exact artifact the lock
   was cut against*, independent of any signature.
2. **Publisher signature (H-6).** The loader looks for `<jar>.asc` beside the jar and applies the policy:

   | Situation | `verify-if-signed` (default) | `require-signed` |
   |---|---|---|
   | signed, verifies against a trusted key | **load** | **load** |
   | signed, verification **fails** (tampered jar / untrusted key) | **refuse** `TTRP-LCK-013` | **refuse** `TTRP-LCK-013` |
   | signed, but no trusted keyring configured | **refuse** `TTRP-LCK-014` | **refuse** `TTRP-LCK-014` |
   | **unsigned** (no `.asc`) | load, **record a warning** | **refuse** `TTRP-LCK-012` |

   The policy only governs **unsigned** plugins. A *present* signature must always verify — a bad or
   unverifiable signature is never silently accepted, under either policy.

### The `require-signed-plugins` knob

Policy is a **deployment decision**, set in `modeler.toml`:

```toml
[ttrp]
require-signed-plugins = true   # default: false (= verify-if-signed)
```

`false` (verify-if-signed) suits development and first-party in-tree plugins; `true` (require-signed) suits a
governed/production deployment where every emit plugin must carry a verified publisher signature.

### Trusted-key ring location

The loader verifies a signature against a **trusted publisher keyring** — a standard OpenPGP public-keyring
file (armored or binary) holding the publisher public keys the deployment has chosen to trust. Its path is
supplied to the loader by the invoking command; the convention is a project-local
`.ttr/trusted-plugin-keys.asc` (falling back to a deployment-wide ring). Adding a publisher’s key to this ring
is the act of *deciding to trust that publisher* — treat it with the same care as any trust-store edit.

> **Note (PL-P5.S2).** The in-tree plugins (bash today; Kestra/Airflow as S3/S4 land) load via the built-in
> fallback and are trusted by being on the compiler’s own classpath — the signature policy above applies to
> **isolated third-party** plugin loads. The command surface that resolves a third-party plugin *by
> coordinates from a plugin dir* (and thus reads `require-signed-plugins` to pick the policy) lands with the
> first non-built-in target; the loader, policy, and knob are in place now.
