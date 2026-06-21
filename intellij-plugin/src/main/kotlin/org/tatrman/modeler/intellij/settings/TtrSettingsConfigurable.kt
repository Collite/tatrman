package org.tatrman.modeler.intellij.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/**
 * Settings page under *Settings | Languages & Frameworks | TTR Modeler*
 * (contracts §4.5). Three fields bound directly to the application-level
 * [TtrSettings] state: Node path, LSP server override, and trace level.
 */
class TtrSettingsConfigurable : BoundConfigurable("TTR Modeler") {

    private val state get() = TtrSettings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        row("Node executable path:") {
            textField()
                .columns(40)
                .bindText({ state.nodePath }, { state.nodePath = it.trim() })
                .comment("Blank = auto-discover <code>node</code> on your PATH. Node.js 20+ required.")
        }
        row("LSP server path override:") {
            textField()
                .columns(40)
                .bindText({ state.serverPathOverride }, { state.serverPathOverride = it.trim() })
                .comment("Blank = use the server bundled with the plugin.")
        }
        row("Trace level:") {
            comboBox(listOf("off", "messages", "verbose"))
                .bindItem({ state.traceLevel }, { state.traceLevel = it ?: "off" })
                .comment("Surfaced through the LSP4IJ console (no separate log).")
        }
    }
}
