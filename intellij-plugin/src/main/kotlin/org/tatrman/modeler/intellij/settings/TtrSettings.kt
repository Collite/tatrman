// SPDX-License-Identifier: Apache-2.0
package org.tatrman.modeler.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application-level settings for the TTR Modeler plugin (contracts §7), persisted
 * in `ttr-modeler.xml`.
 *
 * - [State.nodePath] — blank = auto-discover `node` on `PATH`.
 * - [State.serverPathOverride] — blank = use the bundled server.
 * - [State.traceLevel] — `off | messages | verbose`, mapped onto LSP4IJ's own
 *   per-server trace (no custom logging channel).
 */
@State(name = "TtrSettings", storages = [Storage("ttr-modeler.xml")])
class TtrSettings : PersistentStateComponent<TtrSettings.State> {
    data class State(
        var nodePath: String = "",
        var serverPathOverride: String = "",
        var traceLevel: String = "off",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): TtrSettings = service()
    }
}
