// SPDX-License-Identifier: Apache-2.0
package org.tatrman.modeler.intellij

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.modeler.intellij.settings.TtrSettings

/**
 * State schema + persistence round-trip for [TtrSettings] per contracts §7.
 * Hermetic: exercises the data class and `getState`/`loadState` on a plain
 * instance without the application service container.
 */
class TtrSettingsTest : StringSpec({

    "State defaults are blank node path, blank server override, trace off" {
        val state = TtrSettings.State()
        state.nodePath shouldBe ""
        state.serverPathOverride shouldBe ""
        state.traceLevel shouldBe "off"
    }

    "values round-trip through loadState / getState" {
        val settings = TtrSettings()
        settings.loadState(
            TtrSettings.State(
                nodePath = "/usr/local/bin/node",
                serverPathOverride = "/opt/ttr/server-stdio.mjs",
                traceLevel = "verbose",
            ),
        )

        val state = settings.getState()
        state.nodePath shouldBe "/usr/local/bin/node"
        state.serverPathOverride shouldBe "/opt/ttr/server-stdio.mjs"
        state.traceLevel shouldBe "verbose"
    }
})
