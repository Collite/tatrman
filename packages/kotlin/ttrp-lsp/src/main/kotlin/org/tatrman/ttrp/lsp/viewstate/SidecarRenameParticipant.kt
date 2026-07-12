// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.viewstate

/**
 * The Stage-5.2 realization of the ζ groundwork (C1-c-i): on every successful
 * `textDocument/rename`, migrate the paired `.ttrl` sidecar's ζ keys in the SAME operation
 * as the text edit, so there is no window where the document and its sidecar disagree.
 * Registered by default on [org.tatrman.ttrp.lsp.TtrpLanguageServer]; the in-process test
 * harness swaps in a recording participant instead.
 */
class SidecarRenameParticipant(
    private val layout: LayoutService = LayoutService(),
) : ViewStateRenameParticipant {
    override fun onRename(
        uri: String,
        remaps: List<ZetaKeyRemap>,
    ) {
        layout.migrateKeys(uri, remaps.map { it.oldKey to it.newKey })
    }
}
