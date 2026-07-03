package org.tatrman.modeler.intellij

import org.jetbrains.plugins.textmate.api.TextMateBundleProvider

/**
 * Registers the shipped VS Code-style TextMate bundle (`<pluginHome>/textmate`,
 * containing `package.json` + `ttr`/`ttrg.tmLanguage.json`) so IntelliJ's
 * TextMate engine colors `.ttrm` (scope `source.ttrm`) and `.ttrg` with the same
 * grammars VS Code uses. `fileNamePatternMapping` (IJ4) attaches the LSP on top
 * while preserving this coloring.
 */
class TtrTextMateBundleProvider : TextMateBundleProvider {
    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> =
        listOf(TextMateBundleProvider.PluginBundle("TTR Modeler", PluginResources.textMateBundleDir()))
}
