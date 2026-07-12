// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.test

import org.tatrman.ttrp.lsp.viewstate.ViewStateRenameParticipant
import org.tatrman.ttrp.lsp.viewstate.ZetaKeyRemap
import java.util.concurrent.CopyOnWriteArrayList

/** Records the ζ key remaps a rename produces (Stage 4.1's stand-in for the Stage-5.2 `.ttrl` writer). */
class RecordingRenameParticipant : ViewStateRenameParticipant {
    val remaps = CopyOnWriteArrayList<ZetaKeyRemap>()

    override fun onRename(
        uri: String,
        remaps: List<ZetaKeyRemap>,
    ) {
        this.remaps += remaps
    }
}
