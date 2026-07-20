// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttr.metadata.world.ResolvedWorld

/**
 * The semantic world fingerprint (F-f-ii) for the TTR-P bundle path.
 *
 * **Reconciled (PL-P1 review-070, R2-8):** this now DELEGATES to the single canonical implementation
 * [org.tatrman.ttr.metadata.world.WorldFingerprint] — the one the compiler front-end (`WorldComposer`)
 * and the conformance harness already use. Previously a second, divergent canonicalization lived here
 * (it hashed the `qname`, used empty-string defaults instead of eliding them, and omitted `extends`), so
 * the SAME resolved world produced two different `sha256:` values on the two sides of the seam — any
 * cross-check of a composed-world fingerprint against `manifest.world.fingerprint` would spuriously
 * mismatch. One implementation removes that hazard.
 *
 * The world qname travels in clear beside the hash (`manifest.world = {qname, fingerprint}`), so it is
 * intentionally NOT part of the hashed form.
 */
object WorldFingerprint {
    fun of(world: ResolvedWorld): String =
        org.tatrman.ttr.metadata.world.WorldFingerprint
            .of(world)
}
