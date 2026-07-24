// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import org.tatrman.ttr.md.resolve.MemberSnapshot
import org.tatrman.ttr.semantics.md.MdModel
import java.time.Instant

/**
 * Resolves a request's `model` name to a loaded [MdModel]. Production is a [FileModelProvider] over a
 * model-repo directory; tests inject an in-memory map. Returning `null` for an unknown model is the
 * "model not found" signal the tools surface as a failed outcome.
 */
fun interface ModelProvider {
    fun model(name: String): MdModel?
}

/**
 * Supplies the member snapshot for **connected** resolution of `model` at `asof` (dot-path §7.1).
 * Disconnected mode never calls this (the tools pass `null` members, R13). Production would back this
 * with the S6 catalog over the metadata server; this first cut wires it only in tests (InMemory), the
 * live WS wiring being a follow-up. `null` ⇒ no members available ⇒ the request resolves disconnected.
 */
fun interface MemberProvider {
    fun snapshot(
        model: String,
        asof: Instant,
    ): MemberSnapshot?

    companion object {
        /** A provider that never has members — the disconnected default. */
        val NONE: MemberProvider = MemberProvider { _, _ -> null }
    }
}
