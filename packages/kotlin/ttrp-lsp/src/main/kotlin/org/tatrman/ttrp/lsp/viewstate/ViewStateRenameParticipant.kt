// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.viewstate

/** ζ key = SSA-qualified view-state node identity, e.g. `"crunch/sales#2"`, `"crunch/sums~1"`. */
data class ZetaKeyRemap(
    val oldKey: String,
    val newKey: String,
)

/**
 * ζ groundwork (C1-c): the interface lands in Stage 4.1; the atomic `.ttrl` pair
 * rewrite lands in Stage 5.2. A participant is called with every successful rename
 * BEFORE the `WorkspaceEdit` is returned, so the sidecar can be rewritten atomically
 * with the text edit (C1-c discipline: a changed SSA/chain length ⇒ orphan, never
 * guess). Stage 4.1 registers only a recording participant, in tests.
 */
fun interface ViewStateRenameParticipant {
    fun onRename(
        uri: String,
        remaps: List<ZetaKeyRemap>,
    )
}
